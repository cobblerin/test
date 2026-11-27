package kr.cobblerin.pokedex;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DexProtocol implements ModInitializer {
    public static final int[] ENDS = {0, 151, 251, 386, 493, 649, 721, 809, 905, 1025};
    public static final int TOTAL = ENDS[9];
    public static final int PAGE_SIZE = 6;
    public static final String OVERVIEW = "overview";
    public static final String CANDIDATES = "candidates";
    public static final String PREVIEW = "preview";
    public static final String DONATE = "donate";
    public static final String CLAIM = "claim";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Response.ID, Response.CODEC);
    }

    public record Request(int sequence, String action, int species, int page, String value) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("cobblemon_donation_pokedex", "request_v4"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Request::sequence,
                PacketCodecs.string(16), Request::action,
                PacketCodecs.VAR_INT, Request::species,
                PacketCodecs.VAR_INT, Request::page,
                PacketCodecs.string(36), Request::value, Request::new);

        @Override
        public Id<Request> getId() {
            return ID;
        }
    }

    public record Response(int sequence, String json) implements CustomPayload {
        public static final Id<Response> ID = new Id<>(Identifier.of("cobblemon_donation_pokedex", "response_v4"));
        public static final PacketCodec<RegistryByteBuf, Response> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Response::sequence, PacketCodecs.string(32767), Response::json, Response::new);

        @Override
        public Id<Response> getId() {
            return ID;
        }
    }

    public record View(Overview overview, CandidatePage candidates, Preview preview, String message) {}

    public record Overview(Set<Integer> registered, Map<Integer, Integer> available, List<Progress> progress,
                           List<RewardView> rewards) {}

    public record Progress(int count, int total, int percent) {}

    public enum RewardStatus { LOCKED, UNCONFIGURED, CLAIMABLE, CLAIMED }

    public record RewardView(String id, String label, Progress progress, RewardStatus status, String item, int count) {}

    public record CandidatePage(int species, int page, int pages, List<Candidate> entries) {}

    public record Candidate(String id, String nickname, String location, int level, boolean shiny,
                     List<Integer> ivs, Set<String> aspects) {}

    public record Preview(int species, Candidate candidate, String token) {}
}
