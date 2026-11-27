package kr.cobblerin.pokedex.server;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static kr.cobblerin.pokedex.DexProtocol.*;
import static kr.cobblerin.pokedex.server.DonationPokedex.*;

final class DexNetworking {
    private static final Gson GSON = new Gson();
    private static final String ERROR = "도감 처리 중 오류가 발생했습니다. 서버 로그를 확인해 주세요.";
    private static final Map<UUID, Offer> OFFERS = new HashMap<>();

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(Request.ID, (request, context) -> handle(context.player(), request));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OFFERS.remove(handler.player.getUuid()));
    }

    static int open(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, Response.ID)) {
            message(player, "도감 HUD를 열려면 서버와 같은 버전의 납부 도감 모드를 클라이언트에도 설치해 주세요.");
            return 0;
        }
        return handle(player, new Request(0, OVERVIEW, 0, 0, "")) ? 1 : 0;
    }

    private static boolean handle(ServerPlayerEntity player, Request request) {
        if (!ServerPlayNetworking.canSend(player, Response.ID)) {
            return false;
        }
        if (inBattle(player)) {
            OFFERS.remove(player.getUuid());
            send(player, request, status("배틀이 끝난 후 도감을 이용해 주세요."));
            return false;
        }

        try {
            DonationLedger ledger = ledger(player);
            Map<Integer, UUID> records = ledger.read();
            Offer offer = OFFERS.remove(player.getUuid());
            View view = switch (request.action()) {
                case OVERVIEW -> {
                    reconcile(player, records);
                    yield new View(overview(player, records), null, null, null);
                }
                case CANDIDATES -> new View(null, candidatePage(player, records, request), null, null);
                case PREVIEW -> preview(player, records, request);
                case DONATE -> confirm(player, ledger, records, offer, request);
                case CLAIM -> {
                    var milestone = MILESTONES.stream().filter(entry -> entry.id().equals(request.value())).findFirst();
                    String result = milestone.isPresent() ? claim(player, records, milestone.get()) : "알 수 없는 보상입니다.";
                    yield new View(overview(player, records), null, null, result);
                }
                default -> status("알 수 없는 요청입니다.");
            };
            send(player, request, view);
            return true;
        } catch (IOException | RuntimeException exception) {
            LOG.error("Pokédex HUD request failed for {}", player.getUuid(), exception);
            send(player, request, status(ERROR));
            return false;
        }
    }

    private static void send(ServerPlayerEntity player, Request request, View view) {
        ServerPlayNetworking.send(player, new Response(request.sequence(), GSON.toJson(view)));
    }

    private static View status(String message) {
        return new View(null, null, null, message);
    }

    private static Overview overview(ServerPlayerEntity player, Map<Integer, UUID> records) {
        Map<Integer, Integer> available = new HashMap<>();
        for (var store : stores(player)) {
            for (Pokemon pokemon : store) {
                int dex = number(pokemon);
                if (dex >= 1 && dex <= TOTAL && !records.containsKey(dex) && canDonate(pokemon)) {
                    available.merge(dex, 1, Integer::sum);
                }
            }
        }
        List<Progress> progress = new ArrayList<>(ENDS.length);
        progress.add(progress(registered(records, 0), TOTAL));
        for (int generation = 1; generation < ENDS.length; generation++) {
            progress.add(progress(registered(records, generation), ENDS[generation] - ENDS[generation - 1]));
        }

        List<RewardView> rewards = new ArrayList<>();
        for (var milestone : MILESTONES) {
            ItemStack stack = ItemStack.EMPTY;
            try {
                stack = reward(milestone);
            } catch (IOException | RuntimeException exception) {
                LOG.warn("Invalid Pokédex reward {}", milestone.id(), exception);
            }
            int count = progress.get(milestone.generation()).count();
            RewardStatus status = rewardEligibility(player, milestone, count);
            if (status == RewardStatus.CLAIMABLE && stack.isEmpty()) {
                status = RewardStatus.UNCONFIGURED;
            }
            rewards.add(new RewardView(milestone.id(), milestone.label(), milestone.generation(),
                    progress(count, milestone.required()),
                    status,
                    stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount()));
        }
        return new Overview(records.keySet(), available, progress, rewards);
    }

    private static Progress progress(int count, int total) {
        return new Progress(count, total, Math.min(100, count * 100 / total));
    }

    private static CandidatePage candidatePage(ServerPlayerEntity player, Map<Integer, UUID> records, Request request) {
        List<Candidate> result = new ArrayList<>();
        int dex = request.species();
        int offset = Math.max(0, Math.min(request.page(), 100_000)) * PAGE_SIZE;
        int count = 0;
        if (dex >= 1 && dex <= TOTAL && !records.containsKey(dex)) {
            var stores = stores(player);
            for (int i = 0; i < stores.size(); i++) {
                for (Pokemon pokemon : stores.get(i)) {
                    if (number(pokemon) == dex && canDonate(pokemon)) {
                        if (count >= offset && result.size() < PAGE_SIZE) {
                            result.add(candidate(pokemon, i));
                        }
                        count++;
                    }
                }
            }
        }
        int pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
        if (offset >= count && offset > 0) {
            return candidatePage(player, records, new Request(request.sequence(), CANDIDATES, dex, 0, ""));
        }
        return new CandidatePage(dex, offset / PAGE_SIZE, pages, result);
    }

    private static Candidate candidate(Pokemon pokemon, int store) {
        var ivs = pokemon.getIvs();
        var nickname = pokemon.getNickname();
        String name = nickname == null ? "" : nickname.getString();
        return new Candidate(pokemon.getUuid().toString(), name, store == 0 ? "파티" : "PC",
                pokemon.getLevel(), pokemon.getShiny(), List.of(
                ivs.getOrDefault(Stats.HP), ivs.getOrDefault(Stats.ATTACK), ivs.getOrDefault(Stats.DEFENCE),
                ivs.getOrDefault(Stats.SPECIAL_ATTACK), ivs.getOrDefault(Stats.SPECIAL_DEFENCE),
                ivs.getOrDefault(Stats.SPEED)), Set.copyOf(pokemon.getAspects()));
    }

    private static Candidate findCandidate(ServerPlayerEntity player, int dex, UUID uuid) {
        if (dex < 1 || dex > TOTAL) {
            return null;
        }
        var stores = stores(player);
        for (int i = 0; i < stores.size(); i++) {
            Pokemon pokemon = stores.get(i).get(uuid);
            if (pokemon != null && number(pokemon) == dex && canDonate(pokemon)) {
                return candidate(pokemon, i);
            }
        }
        return null;
    }

    private static View preview(ServerPlayerEntity player, Map<Integer, UUID> records, Request request) {
        int dex = request.species();
        UUID uuid;
        try {
            uuid = UUID.fromString(request.value());
        } catch (IllegalArgumentException exception) {
            return status("올바른 포켓몬을 선택해 주세요.");
        }
        Candidate candidate = findCandidate(player, dex, uuid);
        if (records.containsKey(dex) || candidate == null) {
            return status("선택한 포켓몬을 지금은 납부할 수 없습니다.");
        }
        var preview = new Preview(dex, candidate, UUID.randomUUID().toString());
        OFFERS.put(player.getUuid(), new Offer(preview, uuid, System.nanoTime()));
        return new View(null, null, preview, null);
    }

    private static View confirm(ServerPlayerEntity player, DonationLedger ledger, Map<Integer, UUID> records,
                                Offer offer, Request request) throws IOException {
        if (offer == null || !offer.preview().token().equals(request.value())
                || System.nanoTime() - offer.created() > TimeUnit.MINUTES.toNanos(2)) {
            return status("납부 확인이 만료되었습니다. 개체를 다시 선택해 주세요.");
        }
        Preview preview = offer.preview();
        Candidate current = findCandidate(player, preview.species(), offer.uuid());
        if (!preview.candidate().equals(current)) {
            return status("포켓몬 상태가 바뀌었습니다. 개체를 다시 선택해 주세요.");
        }
        String result = donate(player, ledger, records, preview.species(), offer.uuid());
        return new View(overview(player, records), null, null, result);
    }

    private record Offer(Preview preview, UUID uuid, long created) {}
}
