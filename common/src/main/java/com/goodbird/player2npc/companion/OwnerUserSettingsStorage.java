package com.goodbird.player2npc.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Per-owner {@code user-settings.json}: list modes and reserved keys for future settings.
 */
public final class OwnerUserSettingsStorage {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public static final String KEY_USER_LIST_MODE = "userListMode";
    public static final String KEY_BOT_LIST_MODE = "botListMode";

    public enum ListMode {
        BLACKLIST,
        WHITELIST;

        public String toJson() {
            return this == WHITELIST ? "whitelist" : "blacklist";
        }

        public static ListMode fromJson(String raw) {
            if (raw != null && raw.trim().equalsIgnoreCase("whitelist")) {
                return WHITELIST;
            }
            return BLACKLIST;
        }
    }

    public record Snapshot(ListMode userListMode, ListMode botListMode) {
        public static Snapshot defaults() {
            return new Snapshot(ListMode.BLACKLIST, ListMode.BLACKLIST);
        }
    }

    private OwnerUserSettingsStorage() {
    }

    public static Path fileFor(MinecraftServer server, UUID ownerUuid) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return OwnerCharacterStoragePaths.userSettingsFile(worldRoot, ownerUuid);
    }

    public static Snapshot load(MinecraftServer server, UUID ownerUuid) {
        Path path = fileFor(server, ownerUuid);
        if (!Files.isRegularFile(path)) {
            return Snapshot.defaults();
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            ListMode user = root.has(KEY_USER_LIST_MODE)
                    ? ListMode.fromJson(root.get(KEY_USER_LIST_MODE).getAsString())
                    : ListMode.BLACKLIST;
            ListMode bot = root.has(KEY_BOT_LIST_MODE)
                    ? ListMode.fromJson(root.get(KEY_BOT_LIST_MODE).getAsString())
                    : ListMode.BLACKLIST;
            return new Snapshot(user, bot);
        } catch (Exception e) {
            LOGGER.warn("Failed to load user settings at {}", path, e);
            return Snapshot.defaults();
        }
    }

    /**
     * Reads existing JSON (if any), updates known keys, preserves unknown keys, writes atomically via replace.
     */
    public static void saveSnapshot(MinecraftServer server, UUID ownerUuid, Snapshot snapshot) throws IOException {
        Path path = fileFor(server, ownerUuid);
        Files.createDirectories(path.getParent());
        JsonObject root = new JsonObject();
        if (Files.isRegularFile(path)) {
            try {
                String raw = Files.readString(path, StandardCharsets.UTF_8);
                root = JsonParser.parseString(raw).getAsJsonObject();
            } catch (Exception e) {
                LOGGER.warn("Replacing malformed user settings at {}", path, e);
                root = new JsonObject();
            }
        }
        root.addProperty(KEY_USER_LIST_MODE, snapshot.userListMode().toJson());
        root.addProperty(KEY_BOT_LIST_MODE, snapshot.botListMode().toJson());
        Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
    }
}
