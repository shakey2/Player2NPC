package com.goodbird.player2npc.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-wide persistent Mojang username (case-insensitive) to UUID index at
 * {@code player2npc/persistentdata/server_username_uuid_cache.json}.
 * <p>
 * Updated whenever a player finishes joining the world so offline resolution (e.g. {@code /botblacklist})
 * does not depend on the bounded vanilla profile cache. Per-UUID records include optional {@code nickname}
 * for future display-name / nickname features (mods); currently written as JSON null.
 * <p>
 * Schema (version 1): {@code schemaVersion}, {@code byLowerName} map (lowercase name → uuid string),
 * {@code byUuid} map (uuid string → object with username, lastSeenEpochMs, nickname).
 */
public final class ServerUsernameUuidCache {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final int SCHEMA_VERSION = 1;

    private ServerUsernameUuidCache() {
    }

    private static Path pathFor(MinecraftServer server) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return OwnerCharacterStoragePaths.serverUsernameUuidCacheFile(worldRoot);
    }

    /**
     * Call from the logical server thread when a player has connected (e.g. Architectury {@code PLAYER_JOIN}).
     */
    public static void recordConnectedPlayer(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }
        String name = player.getGameProfile().getName();
        if (name == null || name.isBlank()) {
            return;
        }
        UUID uuid = player.getUUID();
        Path path = pathFor(server);
        try {
            JsonObject root = loadOrEmpty(path);
            JsonObject byLower = getOrCreateObject(root, "byLowerName");
            JsonObject byUuid = getOrCreateObject(root, "byUuid");
            String uuidStr = uuid.toString();
            JsonElement existing = byUuid.get(uuidStr);
            if (existing != null && existing.isJsonObject()) {
                JsonObject prev = existing.getAsJsonObject();
                if (prev.has("username") && !prev.get("username").isJsonNull()) {
                    String old = prev.get("username").getAsString();
                    if (old != null && !old.isBlank() && !old.equalsIgnoreCase(name)) {
                        byLower.remove(old.toLowerCase(Locale.ROOT));
                    }
                }
            }
            byLower.addProperty(name.toLowerCase(Locale.ROOT), uuidStr);
            JsonObject entry = new JsonObject();
            entry.addProperty("username", name);
            entry.addProperty("lastSeenEpochMs", System.currentTimeMillis());
            entry.add("nickname", JsonNull.INSTANCE);
            byUuid.add(uuidStr, entry);
            root.addProperty("schemaVersion", SCHEMA_VERSION);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Failed updating server username UUID cache at {}", path, e);
        }
    }

    public static Optional<UUID> lookupUsername(MinecraftServer server, String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String key = username.trim().toLowerCase(Locale.ROOT);
        Path path = pathFor(server);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("byLowerName")) {
                return Optional.empty();
            }
            JsonObject byLower = root.getAsJsonObject("byLowerName");
            if (!byLower.has(key)) {
                return Optional.empty();
            }
            JsonElement el = byLower.get(key);
            if (el == null || !el.isJsonPrimitive()) {
                return Optional.empty();
            }
            String uuidStr = el.getAsString();
            if (uuidStr == null || uuidStr.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(uuidStr.trim()));
        } catch (Exception e) {
            LOGGER.warn("Failed reading server username UUID cache at {}", path, e);
            return Optional.empty();
        }
    }

    private static JsonObject loadOrEmpty(Path path) throws java.io.IOException {
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.warn("Resetting corrupt server username UUID cache at {}", path, e);
            return new JsonObject();
        }
    }

    private static JsonObject getOrCreateObject(JsonObject root, String key) {
        if (root.has(key) && root.get(key).isJsonObject()) {
            return root.getAsJsonObject(key);
        }
        JsonObject o = new JsonObject();
        root.add(key, o);
        return o;
    }
}
