package kr.cobblerin.pokedex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

record DonationLedger(Path file) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FORMAT = new TypeToken<Map<Integer, UUID>>() {}.getType();

    Map<Integer, UUID> read() throws IOException {
        if (Files.notExists(file)) {
            return new HashMap<>();
        }
        try (var input = Files.newBufferedReader(file)) {
            Map<Integer, UUID> entries = GSON.fromJson(input, FORMAT);
            if (entries == null || entries.entrySet().stream().anyMatch(entry ->
                    entry.getKey() == null || entry.getKey() < 1 || entry.getKey() > 1025 || entry.getValue() == null)
                    || new HashSet<>(entries.values()).size() != entries.size()) {
                throw new IOException("Invalid Pokédex data in " + file);
            }
            return entries;
        }
    }

    void write(Map<Integer, UUID> entries) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "pokedex-", ".tmp");
        try {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(GSON.toJson(new TreeMap<>(entries), FORMAT));
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
