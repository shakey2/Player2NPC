package com.goodbird.player2npc.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CharacterStorageOperations {
    private static final Logger LOGGER = LogManager.getLogger();

    public record StoredCharacterRow(String characterId, String fullName, String shortName, long bytesOnDisk) {
    }

    private CharacterStorageOperations() {
    }

    public static Path ownerDir(MinecraftServer server, UUID ownerUuid) {
        return OwnerCharacterStoragePaths.ownersRoot(server.getWorldPath(LevelResource.ROOT)).resolve(ownerUuid.toString());
    }

    public static long directorySizeBytes(Path root) {
        if (root == null || !Files.exists(root)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    public static void deleteDirectoryRecursive(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ex) {
                    LOGGER.warn("Failed to delete {}", p, ex);
                }
            });
        }
    }

    public static List<StoredCharacterRow> listStoredCharacters(MinecraftServer server, UUID ownerUuid) {
        List<StoredCharacterRow> rows = new ArrayList<>();
        Path dir = ownerDir(server, ownerUuid);
        if (!Files.isDirectory(dir)) {
            return rows;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isDirectory).sorted(Comparator.comparing(p -> p.getFileName().toString())).forEach(charDir -> {
                String characterId = charDir.getFileName().toString();
                DisplayFields df = readDisplayJson(charDir.resolve("display.json"));
                String full = df.fullName != null ? df.fullName : "(unknown)";
                String shortN = df.shortName != null ? df.shortName : "(unknown)";
                long bytes = directorySizeBytes(charDir);
                rows.add(new StoredCharacterRow(characterId, full, shortN, bytes));
            });
        } catch (IOException e) {
            LOGGER.warn("listStoredCharacters failed for {}", ownerUuid, e);
        }
        return rows;
    }

    public static List<String> findCharacterIdsByFullName(MinecraftServer server, UUID ownerUuid, String query, boolean exact) {
        String q = query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        List<String> matches = new ArrayList<>();
        for (StoredCharacterRow row : listStoredCharacters(server, ownerUuid)) {
            if (row.fullName() == null || "(unknown)".equals(row.fullName())) {
                continue;
            }
            boolean ok = exact ? row.fullName().equals(q) : row.fullName().equalsIgnoreCase(q);
            if (ok) {
                matches.add(row.characterId());
            }
        }
        return matches;
    }

    public static List<String> findCharacterIdsByShortName(MinecraftServer server, UUID ownerUuid, String query, boolean exact) {
        String q = query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        List<String> matches = new ArrayList<>();
        for (StoredCharacterRow row : listStoredCharacters(server, ownerUuid)) {
            if (row.shortName() == null || "(unknown)".equals(row.shortName())) {
                continue;
            }
            boolean ok = exact ? row.shortName().equals(q) : row.shortName().equalsIgnoreCase(q);
            if (ok) {
                matches.add(row.characterId());
            }
        }
        return matches;
    }

    public static boolean deleteCharacterData(MinecraftServer server, UUID ownerUuid, String characterId) throws IOException {
        Path d = ownerDir(server, ownerUuid).resolve(characterId);
        if (!Files.isDirectory(d)) {
            return false;
        }
        deleteDirectoryRecursive(d);
        return true;
    }

    public static void deleteAllCharacterData(MinecraftServer server, UUID ownerUuid) throws IOException {
        Path d = ownerDir(server, ownerUuid);
        if (!Files.isDirectory(d)) {
            return;
        }
        // Delete only per-character subdirectories; preserve owner-root files such as botblacklist.json and userblacklist.json.
        try (Stream<Path> stream = Files.list(d)) {
            for (Path child : stream.toList()) {
                if (Files.isDirectory(child)) {
                    deleteDirectoryRecursive(child);
                }
            }
        }
    }

    public static void dismissLiveByCharacterId(MinecraftServer server, UUID ownerUuid, String characterId) {
        if (server == null || characterId == null) {
            return;
        }
        ServerPlayer ownerOnline = server.getPlayerList().getPlayer(ownerUuid);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof AutomatoneEntity auto)) {
                    continue;
                }
                if (auto.character == null || auto.controller == null || auto.controller.getOwner() == null) {
                    continue;
                }
                if (!ownerUuid.equals(auto.controller.getOwner().getUUID())) {
                    continue;
                }
                if (!characterId.equals(auto.character.id())) {
                    continue;
                }
                String mapKey = auto.character.name();
                auto.discard();
                if (ownerOnline != null) {
                    CompanionManager.get(ownerOnline).removeCompanionMapping(mapKey);
                }
            }
        }
    }

    public static void dismissAllAutomatonsForOwner(ServerPlayer ownerPlayer) {
        CompanionManager.get(ownerPlayer).dismissAllCompanions();
    }

    public static Optional<UUID> parseUuidLenient(String s) {
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(s.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static final class DisplayFields {
        final String fullName;
        final String shortName;

        DisplayFields(String fullName, String shortName) {
            this.fullName = fullName;
            this.shortName = shortName;
        }
    }

    private static DisplayFields readDisplayJson(Path path) {
        if (!Files.isRegularFile(path)) {
            return new DisplayFields(null, null);
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            return new DisplayFields(extractJsonString(raw, "fullName"), extractJsonString(raw, "shortName"));
        } catch (IOException e) {
            return new DisplayFields(null, null);
        }
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int start = i + needle.length();
        StringBuilder sb = new StringBuilder();
        for (int p = start; p < json.length(); p++) {
            char c = json.charAt(p);
            if (c == '\\' && p + 1 < json.length()) {
                sb.append(json.charAt(p + 1));
                p++;
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
