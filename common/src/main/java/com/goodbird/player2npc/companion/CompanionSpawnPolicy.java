package com.goodbird.player2npc.companion;

import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Enforces {@link com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig} companion limits.
 */
public final class CompanionSpawnPolicy {

    private CompanionSpawnPolicy() {
    }

    public static Set<String> listStoredCharacterIds(MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) {
            return Collections.emptySet();
        }
        Path ownerDir = OwnerCharacterStoragePaths.ownersRoot(server.getWorldPath(LevelResource.ROOT))
                .resolve(ownerUuid.toString());
        if (!Files.isDirectory(ownerDir)) {
            return Collections.emptySet();
        }
        try (Stream<Path> stream = Files.list(ownerDir)) {
            return stream.filter(Files::isDirectory).map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    public static Optional<Component> denial(ServerPlayer player, Character character, CompanionManager mgr) {
        if (character == null || player.getServer() == null) {
            return Optional.empty();
        }
        // Permadeath ban check first: a banned characterID can never be summoned for its owner in this
        // world (GUI/packet/join all route through denial()). Keyed on character.id() (Non-negotiable #3).
        String banId = characterIdOrEmpty(character);
        if (!banId.isEmpty() && PermadeathBanStorage.isBanned(player.getServer(), player.getUUID(), banId)) {
            return Optional.of(Component.translatable(
                    "message.player2npc.spawn.denied_permadeath", character.shortName())
                    .withStyle(ChatFormatting.RED));
        }
        var cfg = Player2ServerConfigHolder.get();
        CompanionManager.SummonIntent intent = mgr.classifySummon(character);
        int live = mgr.getLiveCompanionCount();
        int maxLive = cfg.getMaxSpawnedCompanionsPerPlayer();
        if ((intent == CompanionManager.SummonIntent.CREATE_NEW || intent == CompanionManager.SummonIntent.RESTORE_DESPAWNED)
                && live >= maxLive) {
            return Optional.of(Component.translatable(
                    "message.player2npc.spawn.denied_active_limit", maxLive)
                    .withStyle(ChatFormatting.RED));
        }
        int maxStored = cfg.getMaxStoredCharacterIdsPerPlayer();
        if (maxStored > 0 && intent == CompanionManager.SummonIntent.CREATE_NEW) {
            String id = characterIdOrEmpty(character);
            if (!id.isEmpty()) {
                Set<String> stored = listStoredCharacterIds(player.getServer(), player.getUUID());
                if (!stored.contains(id) && stored.size() >= maxStored) {
                    return Optional.of(Component.translatable(
                            "message.player2npc.spawn.denied_storage_limit", maxStored)
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
        return Optional.empty();
    }

    public static List<Character> filterForJoin(ServerPlayer player, List<Character> incoming, CompanionManager mgr) {
        if (incoming == null || incoming.isEmpty() || player.getServer() == null) {
            return incoming == null ? List.of() : incoming;
        }
        var cfg = Player2ServerConfigHolder.get();
        int maxLive = cfg.getMaxSpawnedCompanionsPerPlayer();
        int maxStored = cfg.getMaxStoredCharacterIdsPerPlayer();
        Set<String> storedOnDisk = listStoredCharacterIds(player.getServer(), player.getUUID());
        List<Character> sorted = new ArrayList<>(incoming);
        sorted.removeIf(Objects::isNull);
        // Drop permadeath-banned characterIDs before any limit logic. Keep the degradation visible
        // (DESIGN.md §3): send a one-time chat line to the owner for each dropped banned bot so a
        // rejoining player understands why a banned bot is missing rather than assuming a bug.
        Set<String> bannedIds = PermadeathBanStorage.load(player.getServer(), player.getUUID());
        if (!bannedIds.isEmpty()) {
            sorted.removeIf(c -> {
                String id = characterIdOrEmpty(c);
                if (!id.isEmpty() && bannedIds.contains(id)) {
                    player.sendSystemMessage(Component.translatable(
                            "message.player2npc.spawn.skipped_permadeath", c.shortName())
                            .withStyle(ChatFormatting.RED));
                    return true;
                }
                return false;
            });
        }
        sorted.sort(Comparator
                .comparing((Character c) -> {
                    String id = characterIdOrEmpty(c);
                    return id.isEmpty() || !storedOnDisk.contains(id);
                })
                .thenComparing(CompanionSpawnPolicy::characterIdOrEmpty));
        List<Character> out = new ArrayList<>();
        int virtualLive = mgr.getLiveCompanionCount();
        Set<String> virtualStored = new HashSet<>(storedOnDisk);
        for (Character c : sorted) {
            CompanionManager.SummonIntent intent = mgr.classifySummon(c);
            if (intent == CompanionManager.SummonIntent.TELEPORT_ALIVE) {
                out.add(c);
                continue;
            }
            if (intent == CompanionManager.SummonIntent.RESTORE_DESPAWNED) {
                if (virtualLive >= maxLive) {
                    continue;
                }
                out.add(c);
                virtualLive++;
                continue;
            }
            String id = characterIdOrEmpty(c);
            if (virtualLive >= maxLive) {
                continue;
            }
            if (maxStored > 0 && !id.isEmpty() && !virtualStored.contains(id) && virtualStored.size() >= maxStored) {
                continue;
            }
            out.add(c);
            virtualLive++;
            if (!id.isEmpty()) {
                virtualStored.add(id);
            }
        }
        return out;
    }

    private static String characterIdOrEmpty(Character c) {
        if (c == null || c.id() == null || c.id().isBlank()) {
            return "";
        }
        return c.id();
    }
}
