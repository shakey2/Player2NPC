package com.goodbird.player2npc.companion;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.help.ArgNote;
import com.player2.playerengine.help.HelpEntry;
import com.player2.playerengine.help.HelpRegistry;
import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.manager.ConversationManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * {@code /player2npc cleanup clones} — removes duplicate companion "clone" entities left behind by the
 * cross-dimension spawn bug (a still-alive companion in another dimension was not found by the
 * single-dimension resolve, so a new one was spawned and the mapping overwritten, orphaning the
 * original). Wired into the {@code /player2npc} tree from {@link CharacterStorageCommands#register}.
 *
 * <p><b>Two tiers</b> (mirroring {@code storage} / {@code op}): {@code cleanup clones} (permission 0,
 * self only — the direct fix-it button for the actual victim), {@code cleanup clones player <targets>}
 * and {@code cleanup clones all} (permission 2, operator — act on other players' / every online
 * player's squad).
 *
 * <p><b>Safety rails</b> (see {@link #isRemovableOrphan}): only a LOADED {@link AutomatoneEntity} that
 * is provably an orphan is ever removed — same owner, a character with a canonical {@code _companionMap}
 * entry, and a UUID that differs from that canonical entry. The canonical (loaded or unloaded) is never
 * touched; a merely chunk-unloaded companion is never even enumerated; an entity running a user task /
 * agentic run is never removed (belt-and-suspenders). Offline owners are skipped — their in-memory
 * canonical map cannot be safely resolved.
 */
public final class CompanionCleanupCommands {

    private CompanionCleanupCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        registerHelpEntries();
        return Commands.literal("cleanup")
                .then(Commands.literal("clones")
                        .executes(CompanionCleanupCommands::cleanupSelf)
                        .then(Commands.literal("player").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                        .executes(CompanionCleanupCommands::cleanupPlayer)))
                        .then(Commands.literal("all").requires(s -> s.hasPermission(2))
                                .executes(CompanionCleanupCommands::cleanupAll)));
    }

    /** Contributes a {@link HelpEntry} for each {@code /player2npc cleanup clones} leaf. */
    private static void registerHelpEntries() {
        HelpRegistry.register(new HelpEntry("player2npc", "cleanup clones",
                "/player2npc cleanup clones", "help.player2npc.cleanup-clones.short", "help.player2npc.cleanup-clones.long",
                List.of(), 0, null, "cleanup"));
        HelpRegistry.register(new HelpEntry("player2npc", "cleanup clones player",
                "/player2npc cleanup clones player <targets>", "help.player2npc.cleanup-clones-player.short", "help.player2npc.cleanup-clones-player.long",
                List.of(new ArgNote("targets", "help.player2npc.cleanup-clones-player.arg.targets")), 2, null, "cleanup"));
        HelpRegistry.register(new HelpEntry("player2npc", "cleanup clones all",
                "/player2npc cleanup clones all", "help.player2npc.cleanup-clones-all.short", "help.player2npc.cleanup-clones-all.long",
                List.of(), 2, null, "cleanup"));
    }

    private static int cleanupSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.getServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.cleanup.error_no_server"));
            return 0;
        }
        int removed = cleanupForOwner(server, player);
        if (removed == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.cleanup.success_none")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            final int r = removed;
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.cleanup.success_removed", r)
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    private static int cleanupPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.cleanup.error_no_server"));
            return 0;
        }
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "targets");
        int handled = 0;
        for (GameProfile profile : profiles) {
            ServerPlayer online = server.getPlayerList().getPlayer(profile.getId());
            if (online == null) {
                ctx.getSource().sendFailure(Component.translatable("command.player2npc.cleanup.error_offline"));
                continue;
            }
            final int r = cleanupForOwner(server, online);
            final String name = online.getName().getString();
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.cleanup.success_removed_named", r, name)
                    .withStyle(ChatFormatting.GREEN), true);
            handled++;
        }
        return handled > 0 ? 1 : 0;
    }

    private static int cleanupAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.cleanup.error_no_server"));
            return 0;
        }
        int totalRemoved = 0;
        int players = 0;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            totalRemoved += cleanupForOwner(server, online);
            players++;
        }
        final int pc = players;
        final int tr = totalRemoved;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.cleanup.success_all_summary", pc, tr)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * Scan every loaded dimension for this owner's orphan clones and discard them. Returns the count
     * removed. Requires an ONLINE owner (the canonical {@code _companionMap} lives only in the live
     * per-player {@link CompanionManager}).
     */
    private static int cleanupForOwner(MinecraftServer server, ServerPlayer owner) {
        Map<String, UUID> canonical = CompanionManager.get(owner).getCompanionMap();
        if (canonical.isEmpty()) {
            return 0;
        }
        UUID ownerUuid = owner.getUUID();
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            // Collect first, then discard, so we never mutate the entity iterable mid-iteration.
            List<AutomatoneEntity> orphans = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AutomatoneEntity auto && isRemovableOrphan(server, auto, ownerUuid, canonical)) {
                    orphans.add(auto);
                }
            }
            for (AutomatoneEntity orphan : orphans) {
                discardOrphan(orphan, owner);
                removed++;
            }
        }
        return removed;
    }

    /**
     * The exact keep/remove predicate. REMOVE only when ALL hold: (owner matches) AND (character has a
     * canonical squad entry) AND (UUID != that canonical UUID) AND (alive, not mid-removal) AND (not
     * running a user task / agentic run). Any miss -&gt; KEEP.
     */
    private static boolean isRemovableOrphan(MinecraftServer server, AutomatoneEntity auto, UUID ownerUuid, Map<String, UUID> canonical) {
        // Owner match — use the in-memory mirror so it holds even if the controller/owner is detached.
        UUID autoOwner = auto.getOwnerUuid();
        if (autoOwner == null || !autoOwner.equals(ownerUuid)) {
            return false;
        }
        if (auto.character == null) {
            return false;
        }
        UUID canonicalUuid = canonical.get(auto.character.name());
        // No canonical entry for this character -> not a provable duplicate of anything; leave it alone.
        if (canonicalUuid == null) {
            return false;
        }
        // Rail (c): never remove the canonical companion itself (loaded or unloaded).
        if (auto.getUUID().equals(canonicalUuid)) {
            return false;
        }
        // Disambiguate the name-keyed map: character NAMES are user-set and not guaranteed unique, so if
        // an owner legitimately has TWO distinct characters sharing a display name, the map holds only one
        // (name-keyed) entry and a name-only match could DESTROY the other, genuinely-different, live
        // companion. When the canonical entity is loaded we can compare stable character id()s: a mismatch
        // means "different character, same name" -> KEEP. If the canonical is unloaded (id unavailable) we
        // fall back to the name match rather than weaken cleanup of a real clone whose canonical is unloaded.
        AutomatoneEntity canonicalEntity = findLoaded(server, canonicalUuid);
        if (canonicalEntity != null && canonicalEntity.character != null) {
            String canonId = characterIdOrNull(canonicalEntity.character);
            String autoId = characterIdOrNull(auto.character);
            if (canonId != null && autoId != null && !canonId.equals(autoId)) {
                return false;
            }
        }
        // Rail (a): never act on an entity mid-death / already being removed.
        if (!auto.isAlive() || auto.isRemoved()) {
            return false;
        }
        // Rail (a), belt-and-suspenders: never remove a bot actively running a user task / agentic run,
        // even if the identity check above somehow flagged it.
        if (auto.controller != null
                && (auto.controller.hasActiveNonIdleUserTask()
                        || AgenticRunRegistry.get(auto.getUUID()).isPresent())) {
            return false;
        }
        // Rail (b) is satisfied structurally: only LOADED entities are ever enumerated here, so a
        // merely chunk-unloaded (legit) companion is never a candidate.
        return true;
    }

    /** Resolve a companion UUID to its loaded entity across all dimensions, or null if not loaded anywhere. */
    private static AutomatoneEntity findLoaded(MinecraftServer server, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(uuid) instanceof AutomatoneEntity auto) {
                return auto;
            }
        }
        return null;
    }

    /** The character's stable id (used as the storage/permadeath key), or null when blank/absent. */
    private static String characterIdOrNull(Character character) {
        if (character == null || character.id() == null || character.id().isBlank()) {
            return null;
        }
        return character.id();
    }

    /**
     * Graceful discard of a proven orphan clone. Deliberately does NOT persist the orphan's inventory:
     * the per-character inventory file is keyed by owner+characterId (NOT entity UUID), so saving a
     * clone would overwrite the canonical companion's correct on-disk inventory. Mirrors
     * {@code CharacterStorageOperations.dismissLiveByCharacterId}, which also discards without a save.
     * Does NOT touch {@code _companionMap} — the clone's UUID was never the canonical entry.
     */
    private static void discardOrphan(AutomatoneEntity orphan, ServerPlayer owner) {
        if (orphan.controller != null) {
            orphan.controller.stopWithRespawnNotification(owner);
            orphan.controller.unregisterFromGlobalRegistry();
        }
        ConversationManager.despwnCompanion(orphan.getUUID());
        CompanionLocationTracker.clear(orphan.getUUID());
        orphan.discard();
    }
}
