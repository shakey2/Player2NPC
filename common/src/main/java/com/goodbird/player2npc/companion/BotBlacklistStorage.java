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
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * Per-owner bot blacklist persisted next to character folders under the owner UUID directory.
 */
public final class BotBlacklistStorage {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public record Entry(
            String targetUsername,
            @Nullable String targetUuid,
            boolean allBots,
            @Nullable String characterId,
            @Nullable String characterName) {
    }

    private BotBlacklistStorage() {
    }

    public static Path fileFor(MinecraftServer server, UUID blacklistOwnerUuid) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return OwnerCharacterStoragePaths.botBlacklistFile(worldRoot, blacklistOwnerUuid);
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
                boolean allBots = o.has("allBots") && o.get("allBots").getAsBoolean();
                String cid = o.has("characterId") && !o.get("characterId").isJsonNull() ? o.get("characterId").getAsString() : null;
                String cname = o.has("characterName") && !o.get("characterName").isJsonNull() ? o.get("characterName").getAsString() : null;
                out.add(new Entry(tu.trim(), tUuid, allBots, cid, cname));
            }
            return dedupe(out);
        } catch (Exception e) {
            LOGGER.warn("Failed to load bot blacklist at {}", path, e);
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
            String key = key(e);
            if (seen.add(key)) {
                out.add(e);
            }
        }
        return out;
    }

    public static String key(Entry e) {
        String scope = e.allBots() ? "all" : ("id:" + Objects.toString(e.characterId(), "") + ":name:" + Objects.toString(e.characterName(), ""));
        return e.targetUsername().toLowerCase(Locale.ROOT) + "|" + scope;
    }

    /**
     * Resolves a target Minecraft account UUID for blacklist commands. Order: online players,
     * {@link ServerUsernameUuidCache} (populated on player join), then the server profile cache.
     * The persistent cache is not written from this path; it is maintained when players connect.
     */
    public static Optional<UUID> resolveTargetUuid(MinecraftServer server, String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String want = username.trim();
        for (var sp : server.getPlayerList().getPlayers()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(want)) {
                return Optional.of(sp.getUUID());
            }
        }
        Optional<UUID> fromDisk = ServerUsernameUuidCache.lookupUsername(server, want);
        if (fromDisk.isPresent()) {
            return fromDisk;
        }
        try {
            var cache = server.getProfileCache();
            if (cache != null) {
                var opt = cache.get(want);
                if (opt.isPresent()) {
                    return Optional.of(opt.get().getId());
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("Profile cache lookup failed for {}", want, ex);
        }
        return Optional.empty();
    }
}
