package kr.cobblerin.pokedex.server;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static kr.cobblerin.pokedex.DexProtocol.ENDS;
import static kr.cobblerin.pokedex.DexProtocol.RewardStatus;
import static net.minecraft.server.command.CommandManager.literal;

public final class DonationPokedex implements DedicatedServerModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("DonationPokedex");
    static final List<Milestone> MILESTONES = List.of(
            new Milestone("gen1", "1세대 완성", 151, 1, "cobblemon:ultra_ball", 16),
            new Milestone("total25", "전체 25%", 257, 0, "cobblemon:great_ball", 16),
            new Milestone("total50", "전체 50%", 513, 0, "cobblemon:ultra_ball", 16),
            new Milestone("total75", "전체 75%", 769, 0, "cobblemon:ultra_ball", 32),
            new Milestone("total100", "전체 100%", 1025, 0, "cobblemon:master_ball", 1)
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path REWARD_CONFIG = FabricLoader.getInstance().getConfigDir()
            .resolve("cobblemon_donation_pokedex/rewards.json");

    @Override
    public void onInitializeServer() {
        DexNetworking.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> initializeRewards());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                reconcile(handler.player, ledger(handler.player).read());
            } catch (IOException | RuntimeException exception) {
                report(handler.player, exception);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var command = dispatcher.register(literal("donationdex").executes(context ->
                    DexNetworking.open(context.getSource().getPlayerOrThrow())));
            dispatcher.register(literal("pokedex").executes(command.getCommand()));
        });
    }

    private static void initializeRewards() {
        try {
            Files.createDirectories(REWARD_CONFIG.getParent());
            if (Files.notExists(REWARD_CONFIG)) {
                Map<String, Reward> defaults = new LinkedHashMap<>();
                for (Milestone milestone : MILESTONES) {
                    defaults.put(milestone.id(), new Reward(milestone.item(), milestone.count()));
                }
                Files.writeString(REWARD_CONFIG, GSON.toJson(defaults));
            }
        } catch (IOException exception) {
            LOG.error("Could not initialize Pokédex rewards", exception);
        }
    }

    static DonationLedger ledger(ServerPlayerEntity player) {
        Path directory = player.getServer().getSavePath(WorldSavePath.ROOT)
                .resolve("data/cobblemon_donation_pokedex");
        return new DonationLedger(directory.resolve(player.getUuidAsString() + ".json"));
    }

    static void reconcile(ServerPlayerEntity player, Map<Integer, UUID> registrations) throws IOException {
        if (registrations.isEmpty()) {
            return;
        }
        var stores = stores(player);
        for (var registration : registrations.entrySet()) {
            UUID uuid = registration.getValue();
            Owned owned = findOwned(stores, uuid);
            if (owned == null) {
                continue;
            }
            if (number(owned.pokemon()) != registration.getKey() || !owned.store().remove(owned.pokemon())) {
                throw new IOException("Unable to reconcile donated Pokémon " + uuid);
            }
        }
    }

    static String donate(ServerPlayerEntity player, DonationLedger ledger, Map<Integer, UUID> registrations,
                       int number, UUID uuid) throws IOException {
        if (registrations.containsKey(number)) {
            return "이미 등록한 포켓몬입니다.";
        }
        var stores = stores(player);
        Owned owned = findOwned(stores, uuid);
        if (owned == null || number(owned.pokemon()) != number || !canDonate(owned.pokemon()) || inBattle(player)) {
            return "이 포켓몬은 지금 납부할 수 없습니다.";
        }

        Map<Integer, UUID> updated = new HashMap<>(registrations);
        updated.put(number, uuid);
        ledger.write(updated);
        try {
            owned.store().remove(owned.pokemon());
            if (findOwned(stores, uuid) != null) {
                throw new IOException("Cobblemon did not remove donated Pokémon " + uuid);
            }
        } catch (RuntimeException | IOException exception) {
            if (findOwned(stores, uuid) != null) {
                ledger.write(registrations);
                throw exception;
            }
            LOG.warn("Cobblemon reported an error after removing Pokémon {}", uuid, exception);
        }
        registrations.put(number, uuid);
        return "포켓몬을 도감에 영구 등록했습니다.";
    }

    static boolean inBattle(ServerPlayerEntity player) {
        return BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player) != null;
    }

    static boolean canDonate(Pokemon pokemon) {
        return pokemon.getState() instanceof InactivePokemonState
                && pokemon.getTetheringId() == null && pokemon.heldItem().isEmpty();
    }

    static int number(Pokemon pokemon) {
        var species = pokemon.getSpecies();
        if (!species.getResourceIdentifier().getNamespace().equals("cobblemon")) {
            return -1;
        }
        return species.getNationalPokedexNumber();
    }

    static List<PokemonStore<?>> stores(ServerPlayerEntity player) {
        var storage = Cobblemon.INSTANCE.getStorage();
        return List.of(storage.getParty(player), storage.getPC(player));
    }

    private static Owned findOwned(List<PokemonStore<?>> stores, UUID uuid) {
        for (var store : stores) {
            Pokemon pokemon = store.get(uuid);
            if (pokemon != null) {
                return new Owned(store, pokemon);
            }
        }
        return null;
    }

    static int registered(Map<Integer, UUID> registrations, int generation) {
        if (generation == 0) {
            return registrations.size();
        }
        return (int) registrations.keySet().stream()
                .filter(number -> number > ENDS[generation - 1] && number <= ENDS[generation])
                .count();
    }

    static ItemStack reward(Milestone milestone) throws IOException {
        try (var reader = Files.newBufferedReader(REWARD_CONFIG)) {
            var root = GSON.fromJson(reader, JsonObject.class);
            var entry = root.get(milestone.id());
            if (entry == null) {
                return ItemStack.EMPTY;
            }
            Reward reward = GSON.fromJson(entry, Reward.class);
            Identifier identifier = Identifier.tryParse(reward.item());
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                throw new IOException("Unknown reward item for " + milestone.id());
            }
            Item item = Registries.ITEM.get(identifier);
            if (item == Items.AIR || reward.count() < 1 || reward.count() > item.getMaxCount()) {
                throw new IOException("Invalid reward count or item for " + milestone.id());
            }
            return new ItemStack(item, reward.count());
        }
    }

    static RewardStatus rewardEligibility(ServerPlayerEntity player, Milestone milestone, int registered) {
        if (player.getCommandTags().contains(milestone.tag())) {
            return RewardStatus.CLAIMED;
        }
        if (registered < milestone.required()) {
            return RewardStatus.LOCKED;
        }
        return RewardStatus.CLAIMABLE;
    }

    static String claim(ServerPlayerEntity player, Map<Integer, UUID> registrations, Milestone milestone)
            throws IOException {
        int count = registered(registrations, milestone.generation());
        RewardStatus status = rewardEligibility(player, milestone, count);
        if (status == RewardStatus.CLAIMED) {
            return "이미 수령한 보상입니다.";
        }
        if (status == RewardStatus.LOCKED) {
            return "보상 수령 조건을 아직 달성하지 않았습니다.";
        }
        ItemStack reward = reward(milestone);
        if (reward.isEmpty()) {
            return "서버에서 이 보상을 설정하지 않았습니다.";
        }
        PlayerInventory inventory = player.getInventory();
        int slot = inventory.getEmptySlot();
        if (slot < 0) {
            return "인벤토리에 빈 칸을 한 칸 만들어 주세요.";
        }
        // Both inventory and command tags are saved in the same vanilla player data file.
        if (!player.addCommandTag(milestone.tag())) {
            return "보상 수령 상태를 기록할 수 없습니다.";
        }
        inventory.setStack(slot, reward);
        inventory.markDirty();
        return milestone.label() + " 보상을 받았습니다.";
    }

    static void message(ServerPlayerEntity player, String text) {
        player.sendMessage(Text.literal(text), false);
    }

    static void report(ServerPlayerEntity player, Exception exception) {
        LOG.error("Pokédex operation failed for {}", player.getUuid(), exception);
        message(player, "도감 처리 중 오류가 발생했습니다. 서버 로그를 확인해 주세요.");
    }

    record Milestone(String id, String label, int required, int generation, String item, int count) {
        String tag() {
            return "donation_pokedex_claimed_" + id;
        }
    }

    private record Reward(String item, int count) {}
    private record Owned(PokemonStore<?> store, Pokemon pokemon) {}
}
