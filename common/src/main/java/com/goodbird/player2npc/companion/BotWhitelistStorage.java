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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * Per-owner bot whitelist (same schema as {@link BotBlacklistStorage}), next to character folders.
 */
public final class BotWhitelistStorage {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public record Entry(
            String targetUsername,
            @Nullable String targetUuid,
            boolean allBots,
            @Nullable String characterId,
            @Nullable String characterName) {
    }

    private BotWhitelistStorage() {
    }

    public static Path fileFor(MinecraftServer server, UUID ownerUuid) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return OwnerCharacterStoragePaths.botWhitelistFile(worldRoot, ownerUuid);
    }

    public static List<Entry> load(MinecraftServer server, UUID ownerUuid) {
        Path path = fileFor(server, ownerUuid);
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
                boolean allBots = o.has("allBots") && o.get("allBots").getAsBoolean();
                String cid = o.has("characterId") && !o.get("characterId").isJsonNull() ? o.get("characterId").getAsString() : null;
                String cname = o.has("characterName") && !o.get("characterName").isJsonNull() ? o.get("characterName").getAsString() : null;
                out.add(new Entry(tu.trim(), tUuid, allBots, cid, cname));
            }
            return dedupe(out);
        } catch (Exception e) {
            LOGGER.warn("Failed to load bot whitelist at {}", path, e);
            return List.of();
        }
    }

    public static void save(MinecraftServer server, UUID ownerUuid, List<Entry> entries) throws IOException {
        Path path = fileFor(server, ownerUuid);
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
            o.addProperty("allBots", e.allBots());
            if (e.characterId() != null) {
                o.addProperty("characterId", e.characterId());
            }
            if (e.characterName() != null) {
                o.addProperty("characterName", e.characterName());
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
            String k = key(e);
            if (seen.add(k)) {
                out.add(e);
            }
        }
        return out;
    }

    public static String key(Entry e) {
        String scope = e.allBots() ? "all" : ("id:" + Objects.toString(e.characterId(), "") + ":name:" + Objects.toString(e.characterName(), ""));
        return e.targetUsername().toLowerCase(Locale.ROOT) + "|" + scope;
    }

    public static Optional<UUID> resolveTargetUuid(MinecraftServer server, String username) {
        return BotBlacklistStorage.resolveTargetUuid(server, username);
    }
}
