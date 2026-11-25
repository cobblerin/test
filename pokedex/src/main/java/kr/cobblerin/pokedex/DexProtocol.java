package kr.cobblerin.pokedex;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class DexProtocol {
    static final int[] ENDS = {0, 151, 251, 386, 493, 649, 721, 809, 905, 1025};
    static final int TOTAL = ENDS[9];
    static final int PAGE_SIZE = 6;
    static final String OVERVIEW = "overview";
    static final String CANDIDATES = "candidates";
    static final String PREVIEW = "preview";
    static final String DONATE = "donate";
    static final String CLAIM = "claim";

    record Request(int sequence, String action, int species, int page, String value) implements CustomPayload {
        static final Id<Request> ID = new Id<>(Identifier.of("cobblemon_donation_pokedex", "request_v2"));
        static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.tuple(
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

    record Response(int sequence, String json) implements CustomPayload {
        static final Id<Response> ID = new Id<>(Identifier.of("cobblemon_donation_pokedex", "response_v2"));
        static final PacketCodec<RegistryByteBuf, Response> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Response::sequence, PacketCodecs.string(32767), Response::json, Response::new);

        @Override
        public Id<Response> getId() {
            return ID;
        }
    }

    record View(Overview overview, CandidatePage candidates, Preview preview, String message) {}

    record Overview(Set<Integer> registered, Map<Integer, Integer> available, List<RewardView> rewards) {}

    record RewardView(String id, String label, int current, int required, boolean claimed, String item, int count) {
        boolean claimable() {
            return !claimed && current >= required && count > 0;
        }
    }

    record CandidatePage(int species, int page, int pages, List<Candidate> entries) {}

    record Candidate(String id, String nickname, String location, int level, boolean shiny,
                     List<Integer> ivs, Set<String> aspects) {}

    record Preview(int species, Candidate candidate, String token) {}
}
