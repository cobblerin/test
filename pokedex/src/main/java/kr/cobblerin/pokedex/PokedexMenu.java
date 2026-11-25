package kr.cobblerin.pokedex;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static kr.cobblerin.pokedex.DonationPokedex.*;

final class PokedexMenu extends GenericContainerScreenHandler {
    private static final int PAGE_SIZE = 36;
    private final ServerPlayerEntity player;
    private final SimpleInventory buttons;
    private final DonationLedger ledger;
    private final Map<Integer, UUID> registrations;
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private int generation = 1;
    private int page;

    PokedexMenu(int syncId, ServerPlayerEntity player, DonationLedger ledger, Map<Integer, UUID> registrations) {
        this(syncId, player, ledger, registrations, new SimpleInventory(54));
    }

    private PokedexMenu(int syncId, ServerPlayerEntity player, DonationLedger ledger,
                        Map<Integer, UUID> registrations, SimpleInventory buttons) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, player.getInventory(), buttons, 6);
        this.player = player;
        this.ledger = ledger;
        this.registrations = registrations;
        this.buttons = buttons;
        showGeneration();
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity sender) {
        if (sender != player) {
            return;
        }
        // This is a read-only virtual inventory. Never pass a click through to vanilla item handling.
        if (action == SlotActionType.PICKUP && button == 0) {
            Runnable callback = actions.get(slot);
            if (callback != null) {
                try {
                    callback.run();
                } catch (RuntimeException exception) {
                    report(player, exception);
                    player.closeHandledScreen();
                }
            }
        }
        if (player.currentScreenHandler == this) {
            syncState();
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    private void showGeneration() {
        reset();
        Map<Integer, List<Pokemon>> owned = new HashMap<>();
        for (var store : stores(player)) {
            for (Pokemon pokemon : store) {
                if (canDonate(pokemon)) {
                    owned.computeIfAbsent(number(pokemon), ignored -> new ArrayList<>()).add(pokemon);
                }
            }
        }
        for (int gen = 1; gen <= 9; gen++) {
            int target = gen;
            int size = ENDS[gen] - ENDS[gen - 1];
            put(gen - 1, gen == generation ? Items.RED_STAINED_GLASS_PANE : Items.BOOK,
                    Text.literal(gen + "세대"), () -> {
                        generation = target;
                        page = 0;
                        showGeneration();
                    }, line(registered(registrations, gen) + " / " + size));
        }
        int first = ENDS[generation - 1] + 1 + PAGE_SIZE * page;
        int last = Math.min(first + PAGE_SIZE - 1, ENDS[generation]);
        for (int number = first; number <= last; number++) {
            int entry = number;
            boolean done = registrations.containsKey(entry);
            int available = owned.getOrDefault(entry, List.of()).size();
            boolean ready = !done && available > 0;
            Item icon = done ? Items.LIME_DYE : ready ? Items.YELLOW_DYE : Items.GRAY_DYE;
            String status = done ? "등록 완료" : ready ? "납부 가능 · " + available + "마리" : "미등록";
            if (!done && !ready && !implemented(entry)) {
                status = "미등록 · 서버에서 개체 제공 필요";
            }
            put(9 + number - first, icon, title(entry),
                    ready ? () -> showCandidates(entry, 0) : null, line(status));
        }
        int totalPages = (ENDS[generation] - ENDS[generation - 1] + PAGE_SIZE - 1) / PAGE_SIZE;
        paging(page, totalPages, () -> { page--; showGeneration(); }, () -> { page++; showGeneration(); });
        int total = registered(registrations, 0);
        put(53, Items.CHEST, Text.literal("완성 보상"), this::showRewards,
                line("전체 " + total + " / " + ENDS[9] + " · " + total * 100 / ENDS[9] + "%"));
    }

    private void showCandidates(int number, int candidatePage) {
        reset();
        List<Candidate> candidates = new ArrayList<>();
        var stores = stores(player);
        addCandidates(candidates, stores.get(0), number, "파티");
        addCandidates(candidates, stores.get(1), number, "PC");
        put(4, Items.BOOK, title(number), null, line("납부할 포켓몬을 선택하세요."));
        int pages = Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(candidatePage, pages - 1);
        int first = current * PAGE_SIZE;
        for (int index = first; index < Math.min(first + PAGE_SIZE, candidates.size()); index++) {
            Candidate candidate = candidates.get(index);
            Pokemon pokemon = candidate.pokemon();
            put(9 + index - first, Items.PAPER, pokemon.getDisplayName(false),
                    () -> confirm(number, candidate, current),
                    line(candidate.location() + " · 레벨 " + pokemon.getLevel()
                            + (pokemon.getShiny() ? " · 이로치" : "")),
                    line("개체값 HP/공/방/특공/특방/스피드 "
                            + ivs(pokemon)), line("개체 ID " + pokemon.getUuid()));
        }
        if (candidates.isEmpty()) {
            put(22, Items.BARRIER, Text.literal("현재 납부할 수 있는 개체가 없습니다."), null);
        }
        paging(current, pages, () -> showCandidates(number, current - 1), () -> showCandidates(number, current + 1));
        put(53, Items.ARROW, Text.literal("도감으로"), this::showGeneration);
    }

    private static void addCandidates(List<Candidate> candidates,
                                      Iterable<Pokemon> store, int number, String location) {
        for (Pokemon pokemon : store) {
            if (number(pokemon) == number && canDonate(pokemon)) {
                candidates.add(new Candidate(pokemon, location));
            }
        }
    }

    private static String ivs(Pokemon pokemon) {
        var ivs = pokemon.getIvs();
        return List.of(
                Stats.HP,
                Stats.ATTACK,
                Stats.DEFENCE,
                Stats.SPECIAL_ATTACK,
                Stats.SPECIAL_DEFENCE,
                Stats.SPEED
        ).stream().map(stat -> String.valueOf(ivs.getOrDefault(stat))).collect(Collectors.joining("/"));
    }

    private void confirm(int number, Candidate candidate, int candidatePage) {
        reset();
        Pokemon pokemon = candidate.pokemon();
        UUID uuid = pokemon.getUuid();
        put(22, Items.PAPER, pokemon.getDisplayName(false), null,
                line(candidate.location() + " · 레벨 " + pokemon.getLevel()
                        + (pokemon.getShiny() ? " · 이로치" : "")),
                line("개체값 HP/공/방/특공/특방/스피드 " + ivs(pokemon)),
                line("개체 ID " + uuid),
                line("이 개체가 소모되고 해당 종이 영구 등록됩니다."));
        put(48, Items.RED_CONCRETE, Text.literal("취소"), () -> showCandidates(number, candidatePage));
        put(50, Items.LIME_CONCRETE, Text.literal("납부 확정"), () -> {
            try {
                donate(player, ledger, registrations, number, uuid);
                showGeneration();
            } catch (IOException | RuntimeException exception) {
                report(player, exception);
                player.closeHandledScreen();
            }
        });
    }

    private void showRewards() {
        reset();
        for (int index = 0; index < MILESTONES.size(); index++) {
            Milestone milestone = MILESTONES.get(index);
            boolean claimed = player.getCommandTags().contains(milestone.tag());
            int count = registered(registrations, milestone.generation());
            boolean earned = count >= milestone.required();
            ItemStack reward = ItemStack.EMPTY;
            try {
                reward = reward(milestone);
            } catch (IOException | RuntimeException exception) {
                LOG.warn("Invalid Pokédex reward {}", milestone.id(), exception);
            }
            boolean available = !reward.isEmpty();
            String status = claimed ? "수령 완료" : !earned ? "진행 중" : available ? "클릭하여 수령" : "보상 미설정";
            Item icon = claimed ? Items.LIME_DYE : earned && available ? Items.CHEST : Items.GRAY_DYE;
            Text contents = available ? Text.empty().append(reward.getName()).append(" × " + reward.getCount()) :
                    line("설정된 아이템 없음");
            put(20 + index, icon, Text.literal(milestone.label()), !claimed && earned && available ? () -> {
                try {
                    claim(player, registrations, milestone);
                } catch (IOException | RuntimeException exception) {
                    report(player, exception);
                }
                showRewards();
            } : null, line(status), line("등록 " + count + " / 필요 " + milestone.required()), contents);
        }
        put(53, Items.ARROW, Text.literal("도감으로"), this::showGeneration);
    }

    private void paging(int page, int total, Runnable previous, Runnable next) {
        if (page > 0) {
            put(45, Items.ARROW, Text.literal("이전 페이지"), previous);
        }
        put(49, Items.PAPER, Text.literal((page + 1) + " / " + total + " 페이지"), null);
        if (page + 1 < total) {
            put(51, Items.ARROW, Text.literal("다음 페이지"), next);
        }
    }

    private static Text title(int number) {
        return Text.literal("#" + String.format(Locale.ROOT, "%04d", number) + " ")
                .append(speciesName(number));
    }

    private static Text line(String value) {
        return Text.literal(value).formatted(Formatting.GRAY);
    }

    private void reset() {
        actions.clear();
        buttons.clear();
    }

    private void put(int slot, Item material, Text title, Runnable action, Text... lore) {
        ItemStack item = new ItemStack(material);
        item.set(DataComponentTypes.CUSTOM_NAME, title.copy().styled(style -> style.withItalic(false)));
        if (lore.length > 0) {
            item.set(DataComponentTypes.LORE, new LoreComponent(List.of(lore)));
        }
        buttons.setStack(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    private record Candidate(Pokemon pokemon, String location) {}
}
