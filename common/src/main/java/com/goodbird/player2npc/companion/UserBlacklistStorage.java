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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * Per-owner list of Minecraft accounts blocked from prompting that owner's companions.
 * Stored next to {@link OwnerCharacterStoragePaths#BOT_BLACKLIST_FILE_NAME}.
 */
public final class UserBlacklistStorage {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public record Entry(String targetUsername, @Nullable String targetUuid) {
    }

    private UserBlacklistStorage() {
    }

    public static Path fileFor(MinecraftServer server, UUID blacklistOwnerUuid) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return OwnerCharacterStoragePaths.userBlacklistFile(worldRoot, blacklistOwnerUuid);
    }

    public static List<Entry> load(MinecraftServer server, UUID blacklistOwnerUuid) {
        Path path = fileFor(server, blacklistOwnerUuid);
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("entries");
            List<Entry> out = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                String tu = o.has("targetUsername") ? o.get("targetUsername").getAsString() : null;
                if (tu == null || tu.isBlank()) {
                    continue;
                }
                String tUuid = o.has("targetUuid") && !o.get("targetUuid").isJsonNull() ? o.get("targetUuid").getAsString() : null;
                out.add(new Entry(tu.trim(), tUuid));
            }
            return dedupe(out);
        } catch (Exception e) {
            LOGGER.warn("Failed to load user blacklist at {}", path, e);
            return List.of();
        }
    }

    public static void save(MinecraftServer server, UUID blacklistOwnerUuid, List<Entry> entries) throws IOException {
        Path path = fileFor(server, blacklistOwnerUuid);
        Files.createDirectories(path.getParent());
        List<Entry> clean = dedupe(entries);
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Entry e : clean) {
            JsonObject o = new JsonObject();
            o.addProperty("targetUsername", e.targetUsername());
            if (e.targetUuid() != null) {
                o.addProperty("targetUuid", e.targetUuid());
            }
            arr.add(o);
        }
        root.add("entries", arr);
        Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static List<Entry> dedupe(List<Entry> entries) {
        List<Entry> out = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (Entry e : entries) {
            if (seen.add(key(e))) {
                out.add(e);
            }
        }
        return out;
    }

    public static String key(Entry e) {
        return e.targetUsername().toLowerCase(Locale.ROOT);
    }
}
