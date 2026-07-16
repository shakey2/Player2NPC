package com.goodbird.player2npc.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Per-owner, per-world permadeath denial. A canonical lethal decision is recorded in volatile
 * memory before disk I/O, so persistence failure can never turn the same death into a respawn.
 */
public final class PermadeathBanStorage {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON =
            new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String KEY_SCHEMA_VERSION = "schemaVersion";
    private static final String KEY_BANNED_CHARACTER_IDS = "bannedCharacterIds";
    private static final Set<BanKey> VOLATILE_DENIALS = ConcurrentHashMap.newKeySet();
    private static final Map<BanKey, Integer> RETRIES_LEFT = new ConcurrentHashMap<>();

    private PermadeathBanStorage() {
    }

    public record TerminalBanReceipt(
            boolean terminalDenied,
            boolean persisted,
            boolean retryPending) {
    }

    enum DiskLoadStatus {
        MISSING,
        VALID,
        INVALID
    }

    record DiskLoadReceipt(
            DiskLoadStatus status,
            Set<String> characterIds,
            IOException failure) {
        DiskLoadReceipt {
            if (status == null) {
                throw new IllegalArgumentException("Disk load status is required");
            }
            characterIds = Collections.unmodifiableSet(
                    new LinkedHashSet<>(characterIds == null ? Set.of() : characterIds));
            if ((status == DiskLoadStatus.INVALID) != (failure != null)) {
                throw new IllegalArgumentException(
                        "Only an invalid disk receipt may carry a failure");
            }
        }

        static DiskLoadReceipt missing() {
            return new DiskLoadReceipt(DiskLoadStatus.MISSING, Set.of(), null);
        }

        static DiskLoadReceipt valid(Set<String> characterIds) {
            return new DiskLoadReceipt(DiskLoadStatus.VALID, characterIds, null);
        }

        static DiskLoadReceipt invalid(String message, Exception cause) {
            IOException failure = cause instanceof IOException ioFailure
                    ? ioFailure
                    : new IOException(message, cause);
            return new DiskLoadReceipt(DiskLoadStatus.INVALID, Set.of(), failure);
        }
    }

    private record BanKey(Path worldRoot, UUID ownerUuid, String characterId) {
        BanKey {
            worldRoot = worldRoot.toAbsolutePath().normalize();
        }
    }

    public static Path fileFor(MinecraftServer server, UUID ownerUuid) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return OwnerCharacterStoragePaths.permadeathBansFile(worldRoot, ownerUuid);
    }

    /**
     * Makes denial terminal in memory first, then attempts synchronous durable persistence.
     */
    public static synchronized TerminalBanReceipt recordTerminalBan(
            MinecraftServer server, UUID ownerUuid, String characterId) {
        if (server == null || ownerUuid == null
                || characterId == null || characterId.isBlank()) {
            return new TerminalBanReceipt(false, false, false);
        }
        BanKey key = key(server, ownerUuid, characterId);
        VOLATILE_DENIALS.add(key);
        try {
            persistMerged(server, ownerUuid, characterId);
            RETRIES_LEFT.remove(key);
            return new TerminalBanReceipt(true, true, false);
        } catch (IOException failure) {
            RETRIES_LEFT.put(key, MAX_RETRY_ATTEMPTS);
            LOGGER.warn("Permadeath denial is volatile after write failure owner={} characterId={}",
                    ownerUuid, characterId, failure);
            return new TerminalBanReceipt(true, false, true);
        }
    }

    /**
     * Loads durable bans, unions volatile terminal denials, and spends at most one bounded retry per
     * pending ban for this owner.
     */
    public static synchronized Set<String> load(
            MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) {
            return new LinkedHashSet<>();
        }
        retryPendingForOwner(server, ownerUuid);
        Path path = fileFor(server, ownerUuid);
        DiskLoadReceipt diskReceipt = loadDisk(path);
        if (diskReceipt.status() == DiskLoadStatus.INVALID) {
            LOGGER.warn("Failed to load permadeath bans at {}", path, diskReceipt.failure());
        }
        Set<String> out = new LinkedHashSet<>(diskReceipt.characterIds());
        Path worldRoot = worldRoot(server);
        for (BanKey key : VOLATILE_DENIALS) {
            if (key.worldRoot().equals(worldRoot)
                    && key.ownerUuid().equals(ownerUuid)) {
                out.add(key.characterId());
            }
        }
        return out;
    }

    public static boolean isBanned(
            MinecraftServer server, UUID ownerUuid, String characterId) {
        return characterId != null && !characterId.isBlank()
                && load(server, ownerUuid).contains(characterId);
    }

    /** Compatibility API. Prefer {@link #recordTerminalBan}. */
    public static void addBan(
            MinecraftServer server, UUID ownerUuid, String characterId) throws IOException {
        TerminalBanReceipt receipt =
                recordTerminalBan(server, ownerUuid, characterId);
        if (!receipt.terminalDenied()) {
            throw new IOException("Permadeath denial identity was incomplete");
        }
        if (!receipt.persisted()) {
            throw new IOException("Permadeath denial remains volatile; disk write failed");
        }
    }

    private static void retryPendingForOwner(
            MinecraftServer server, UUID ownerUuid) {
        Path worldRoot = worldRoot(server);
        List<BanKey> pending = new ArrayList<>(RETRIES_LEFT.keySet());
        for (BanKey key : pending) {
            if (!key.worldRoot().equals(worldRoot)
                    || !key.ownerUuid().equals(ownerUuid)) {
                continue;
            }
            int remaining = RETRIES_LEFT.getOrDefault(key, 0);
            if (remaining <= 0) {
                RETRIES_LEFT.remove(key);
                continue;
            }
            try {
                persistMerged(server, ownerUuid, key.characterId());
                RETRIES_LEFT.remove(key);
            } catch (IOException retryFailure) {
                if (remaining <= 1) {
                    RETRIES_LEFT.remove(key);
                    LOGGER.warn("Permadeath denial remains volatile after bounded retries owner={}"
                                    + " characterId={}",
                            ownerUuid, key.characterId(), retryFailure);
                } else {
                    RETRIES_LEFT.put(key, remaining - 1);
                }
            }
        }
    }

    private static void persistMerged(
            MinecraftServer server, UUID ownerUuid, String characterId) throws IOException {
        persistMerged(fileFor(server, ownerUuid), characterId);
    }

    static void persistMerged(Path path, String characterId) throws IOException {
        if (path == null || characterId == null || characterId.isBlank()) {
            throw new IOException("Permadeath ban path and character identity are required");
        }
        DiskLoadReceipt diskReceipt = loadDisk(path);
        if (diskReceipt.status() == DiskLoadStatus.INVALID) {
            throw new IOException(
                    "Refusing to overwrite an invalid existing permadeath ban file",
                    diskReceipt.failure());
        }
        Set<String> current = new LinkedHashSet<>(diskReceipt.characterIds());
        current.add(characterId);
        writeDiskAtomically(path, current);
    }

    static DiskLoadReceipt loadDisk(Path path) {
        Set<String> out = new LinkedHashSet<>();
        if (path == null) {
            return DiskLoadReceipt.invalid(
                    "Permadeath ban path is missing",
                    new IOException("Permadeath ban path is missing"));
        }
        try {
            BasicFileAttributes attributes =
                    Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                return DiskLoadReceipt.invalid(
                        "Permadeath ban path is not a regular file",
                        new IOException("Permadeath ban path is not a regular file"));
            }
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            var parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) {
                return DiskLoadReceipt.invalid(
                        "Permadeath ban file root is not an object",
                        new IOException("Permadeath ban file root is not an object"));
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has(KEY_SCHEMA_VERSION)
                    || !isSupportedSchema(root.get(KEY_SCHEMA_VERSION))) {
                return DiskLoadReceipt.invalid(
                        "Permadeath ban file has an unsupported schema",
                        new IOException("Permadeath ban file has an unsupported schema"));
            }
            if (!root.has(KEY_BANNED_CHARACTER_IDS)
                    || !root.get(KEY_BANNED_CHARACTER_IDS).isJsonArray()) {
                return DiskLoadReceipt.invalid(
                        "Permadeath ban file is missing its ban array",
                        new IOException("Permadeath ban file is missing its ban array"));
            }
            JsonArray array = root.getAsJsonArray(KEY_BANNED_CHARACTER_IDS);
            for (int i = 0; i < array.size(); i++) {
                if (!array.get(i).isJsonPrimitive()
                        || !array.get(i).getAsJsonPrimitive().isString()) {
                    return DiskLoadReceipt.invalid(
                            "Permadeath ban file contains a non-string identity",
                            new IOException(
                                    "Permadeath ban file contains a non-string identity"));
                }
                String id = array.get(i).getAsString();
                if (id == null || id.isBlank()) {
                    return DiskLoadReceipt.invalid(
                            "Permadeath ban file contains a blank identity",
                            new IOException("Permadeath ban file contains a blank identity"));
                }
                out.add(id);
            }
            return DiskLoadReceipt.valid(out);
        } catch (NoSuchFileException missing) {
            return DiskLoadReceipt.missing();
        } catch (Exception failure) {
            return DiskLoadReceipt.invalid(
                    "Permadeath ban file could not be read", failure);
        }
    }

    private static boolean isSupportedSchema(JsonElement schema) {
        if (schema == null
                || !schema.isJsonPrimitive()
                || !schema.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        try {
            return schema.getAsBigDecimal().compareTo(
                    BigDecimal.valueOf(SCHEMA_VERSION)) == 0;
        } catch (NumberFormatException invalidNumber) {
            return false;
        }
    }

    static void writeDiskAtomically(Path path, Set<String> characterIds) throws IOException {
        Files.createDirectories(path.getParent());
        JsonObject root = new JsonObject();
        root.addProperty(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        if (characterIds != null) {
            for (String id : characterIds) {
                if (id != null && !id.isBlank()) {
                    array.add(id);
                }
            }
        }
        root.add(KEY_BANNED_CHARACTER_IDS, array);
        Path temp = path.resolveSibling(path.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static int pendingRetriesForTest(
            Path worldRoot, UUID ownerUuid, String characterId) {
        return RETRIES_LEFT.getOrDefault(
                new BanKey(worldRoot, ownerUuid, characterId), 0);
    }

    static void clearVolatileForTest() {
        VOLATILE_DENIALS.clear();
        RETRIES_LEFT.clear();
    }

    private static BanKey key(
            MinecraftServer server, UUID ownerUuid, String characterId) {
        return new BanKey(worldRoot(server), ownerUuid, characterId);
    }

    private static Path worldRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }
}
