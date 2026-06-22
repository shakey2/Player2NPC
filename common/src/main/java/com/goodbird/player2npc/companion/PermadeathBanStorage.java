package com.goodbird.player2npc.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Per-owner, per-world permadeath ban list. Stores the stable {@code character.id()} of every
 * companion that died a permadeath (hardcore) kill for this owner, so that characterId can no longer
 * be spawned/summoned for this owner on this world.
 *
 * <p>File: {@code owners/<ownerUuid>/permadeath_bans.json} ->
 * {@code { "schemaVersion": 1, "bannedCharacterIds": ["id1", ...] }}. World-scoped automatically via
 * {@link MinecraftServer#getWorldPath(LevelResource)} ({@link LevelResource#ROOT}). Bans are keyed by
 * {@code character.id()} (stable API id) — NEVER {@code character.name()} (display name). Per-owner
 * isolation is structural: a sibling owner's file is never read here.
 *
 * <p>No in-memory cache (consistent with {@link OwnerUserSettingsStorage} / {@link BotBlacklistStorage});
 * reads happen only at death and at summon, off the hot path.
 */
public final class PermadeathBanStorage {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static final int SCHEMA_VERSION = 1;
    private static final String KEY_SCHEMA_VERSION = "schemaVersion";
    private static final String KEY_BANNED_CHARACTER_IDS = "bannedCharacterIds";

    private PermadeathBanStorage() {
    }

    public static Path fileFor(MinecraftServer server, UUID ownerUuid) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return OwnerCharacterStoragePaths.permadeathBansFile(worldRoot, ownerUuid);
    }

    /** Loads the set of banned characterIds for this owner; a missing/malformed file yields an empty set. */
    public static Set<String> load(MinecraftServer server, UUID ownerUuid) {
        Path path = fileFor(server, ownerUuid);
        Set<String> out = new LinkedHashSet<>();
        if (!Files.isRegularFile(path)) {
            return out;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (root.has(KEY_BANNED_CHARACTER_IDS) && root.get(KEY_BANNED_CHARACTER_IDS).isJsonArray()) {
                JsonArray arr = root.getAsJsonArray(KEY_BANNED_CHARACTER_IDS);
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i).isJsonNull()) {
                        continue;
                    }
                    String id = arr.get(i).getAsString();
                    if (id != null && !id.isBlank()) {
                        out.add(id);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load permadeath bans at {}", path, e);
            return new LinkedHashSet<>();
        }
        return out;
    }

    public static boolean isBanned(MinecraftServer server, UUID ownerUuid, String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return false;
        }
        return load(server, ownerUuid).contains(characterId);
    }

    /**
     * Idempotently adds {@code characterId} to this owner's ban set (read-merge-write). A blank/null id
     * is skipped (no ban can be written for it). If the id is already present, the file is left unchanged.
     */
    public static void addBan(MinecraftServer server, UUID ownerUuid, String characterId) throws IOException {
        if (characterId == null || characterId.isBlank()) {
            LOGGER.warn("Skipping permadeath ban for owner {}: blank/null characterId", ownerUuid);
            return;
        }
        Set<String> current = load(server, ownerUuid);
        if (!current.add(characterId)) {
            return; // already banned; idempotent no-op
        }
        Path path = fileFor(server, ownerUuid);
        Files.createDirectories(path.getParent());
        JsonObject root = new JsonObject();
        root.addProperty(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        JsonArray arr = new JsonArray();
        for (String id : current) {
            arr.add(id);
        }
        root.add(KEY_BANNED_CHARACTER_IDS, arr);
        Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
    }
}
