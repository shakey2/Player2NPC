//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.goodbird.player2npc.companion;

import com.goodbird.player2npc.mixins.IEntityPersistentData;
import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CompanionManager {

    public enum SummonIntent {
        TELEPORT_ALIVE,
        RESTORE_DESPAWNED,
        CREATE_NEW
    }
    private static final Logger LOGGER = LogManager.getLogger();
    private final ServerPlayer _player;
    private final Map<String, UUID> _companionMap = new ConcurrentHashMap();
    private final Map<String, CompoundTag> _despawnedCompanionData = new ConcurrentHashMap();
    /**
     * Companions whose async chunk+entity reload we have kicked off (a hinted, merely-unloaded companion
     * cannot be surfaced synchronously — entity deserialization completes on a later entityManager tick).
     * Retried each {@link #serverTick()} until the entity loads (then relocate/TP, never clone) or the hint
     * proves stale after {@link #MAX_RELOAD_RETRY_TICKS} (then spawn a genuine replacement). Keyed by
     * character name to mirror {@link #_companionMap}.
     */
    private final Map<String, PendingReload> _pendingReloads = new ConcurrentHashMap();
    private List<Character> _assignedCharacters = new ArrayList();
    private boolean _needsToSummon = false;
    private static final Map<String, CompanionManager> cache = new HashMap<>();
    /** ~5s at 20 tps — an async chunk+entity load completes well within this; longer means the hint is stale. */
    private static final int MAX_RELOAD_RETRY_TICKS = 100;

    /** Mutable per-character retry state for a deferred (awaiting async reload) companion. */
    private static final class PendingReload {
        final Character character;
        int ticksLeft;

        PendingReload(Character character, int ticksLeft) {
            this.character = character;
            this.ticksLeft = ticksLeft;
        }
    }

    public CompanionManager(ServerPlayer player) {
        this._player = player;
    }

    public static CompanionManager get(ServerPlayer player) {
        return cache.computeIfAbsent(player.getUUID().toString(), (k) -> {
            CompanionManager manager = new CompanionManager(player);
            manager.readFromNbt();
            return manager;
        });
    }

    public static void remove(ServerPlayer player) {
        cache.remove(player.getUUID().toString());
    }

    public void summonAllCompanionsAsync() {
        this._needsToSummon = true;
        CompletableFuture.supplyAsync(() -> CharacterUtils.requestCharacters(this._player, "player2-ai-npc-minecraft")).thenAcceptAsync((characters) -> this._assignedCharacters = new ArrayList(Arrays.asList(characters)), this._player.getServer());
    }

    public SummonIntent classifySummon(Character character) {
        if (character == null) {
            return SummonIntent.CREATE_NEW;
        }
        String name = character.name();
        if (this._despawnedCompanionData.containsKey(name)) {
            return SummonIntent.CREATE_NEW;
        }
        UUID companionUuid = this._companionMap.get(name);
        if (companionUuid == null) {
            return SummonIntent.CREATE_NEW;
        }
        Located located = this.resolveLoadedCompanion(companionUuid);
        if (located != null && located.entity().isAlive()) {
            return SummonIntent.TELEPORT_ALIVE;
        }
        // Not loaded in any dimension, but a live location hint means the companion is alive-but-unloaded:
        // the hint is recorded while the entity ticks and cleared ONLY on terminal removal (death/discard,
        // via AutomatoneEntity.remove()'s shouldDestroy chokepoint) — it survives a chunk unload. A summon
        // will RELOCATE/reload such a companion, not create a new one, so it must NOT read as CREATE_NEW:
        // that would let denial()/filterForJoin wrongly count a live reunion against the spawn cap (deny it,
        // or drop it from the join batch). No force-load here — the hint's presence is sufficient and, unlike
        // resolveViaLocationHint's post-load getEntity, it is not subject to the async entity-load race.
        if (CompanionLocationTracker.get(companionUuid) != null) {
            return SummonIntent.TELEPORT_ALIVE;
        }
        return SummonIntent.CREATE_NEW;
    }

    /** A companion entity resolved to the {@link ServerLevel} it is currently loaded in. */
    private record Located(AutomatoneEntity entity, ServerLevel level) {
    }

    /**
     * Resolve the mapped companion across ALL registered dimensions (dimension-generic — no hardcoded
     * dimension keys), returning both the entity and the level it is loaded in, or {@code null} if it
     * is not loaded in any level. The shared existence check behind {@link #classifySummon},
     * {@link #ensureCompanionExists} and {@link #dismissCompanion}: keeping them symmetric on this (plus
     * the {@link CompanionLocationTracker} hint for the merely-unloaded case) is what stops the drift that
     * was the original cross-dimension cloning bug. Sees LOADED entities only — {@link ServerLevel#getEntity}
     * does not consult unloaded chunks; the unloaded case is handled by {@link #resolveViaLocationHint}.
     */
    private Located resolveLoadedCompanion(UUID uuid) {
        if (uuid == null || this._player.getServer() == null) {
            return null;
        }
        for (ServerLevel w : this._player.getServer().getAllLevels()) {
            Entity e = w.getEntity(uuid);
            if (e instanceof AutomatoneEntity auto) {
                return new Located(auto, w);
            }
        }
        return null;
    }

    /**
     * Last-resort resolve for a mapped companion that is not loaded in any level: a merely-unloaded
     * companion returns null from {@link #resolveLoadedCompanion} exactly like a discarded one, so before
     * concluding "gone -&gt; spawn" (which would clone next to a healthy, sleeping companion) we consult the
     * {@link CompanionLocationTracker} hint and force-load that ONE chunk. Dimension-generic: uses whatever
     * {@code ResourceKey<Level>} was recorded, resolved through the server's own live registry, so modded
     * dimensions work too.
     *
     * <p><b>Async caveat (verified against decompiled-sources/1.21.1):</b> forcing a chunk to
     * {@code ChunkStatus.FULL} adds a FULL ticket -&gt; {@code Visibility.TRACKED} -&gt;
     * {@code PersistentEntitySectionManager.requestChunkLoad}, whose entity deserialization is drained on a
     * LATER {@code entityManager.tick()}, not synchronously. So the immediate {@code getEntity} below only
     * succeeds when the entity was ALREADY loaded (e.g. a prior tick's force-load completed). A miss here
     * therefore does NOT prove "gone" — {@link #ensureCompanionExists} keeps the hint, DEFERS, and retries in
     * {@link #serverTick()} once the load lands, rather than cloning. We deliberately do NOT clear the hint on
     * a miss (that would discard a valid hint mid async-load); terminal removal clears it via
     * {@link AutomatoneEntity#remove}'s {@code shouldDestroy()} chokepoint.
     */
    private Located resolveViaLocationHint(UUID uuid) {
        MinecraftServer server = this._player.getServer();
        if (uuid == null || server == null) {
            return null;
        }
        CompanionLocationTracker.LocationHint hint = CompanionLocationTracker.get(uuid);
        if (hint == null) {
            return null;
        }
        ServerLevel level = server.getLevel(hint.dimension());
        if (level == null) {
            return null;
        }
        // Kick off the (async, per the caveat above) chunk+entity load at the last-known position, then
        // opportunistically re-check in case it was already resolved by an earlier force-load.
        level.getChunk(hint.pos().getX() >> 4, hint.pos().getZ() >> 4, ChunkStatus.FULL, true);
        Entity e = level.getEntity(uuid);
        if (e instanceof AutomatoneEntity auto) {
            return new Located(auto, level);
        }
        return null;
    }

    /** Read-only snapshot of the canonical character-name -&gt; companion-UUID map (for the cleanup command). */
    public Map<String, UUID> getCompanionMap() {
        return Collections.unmodifiableMap(this._companionMap);
    }

    private void summonCompanions() {
        if (!this._assignedCharacters.isEmpty()) {
            this._assignedCharacters = CompanionSpawnPolicy.filterForJoin(this._player, this._assignedCharacters, this);
            List<String> assignedNames = this._assignedCharacters.stream().map((c) -> c.name()).toList();
            List<String> toDismiss = new ArrayList();
            this._companionMap.forEach((name, uuid) -> {
                if (!assignedNames.contains(name)) {
                    toDismiss.add(name);
                }

            });
            toDismiss.forEach(this::dismissCompanion);
            this._assignedCharacters.stream().filter((character) -> character != null).forEach((character) -> {
                LOGGER.info("summonCompanions for character={}", character);
                this.ensureCompanionExists(character);
            });
            this._assignedCharacters.clear();
            writeToNbt();
        }
    }

    public void ensureCompanionExists(Character character) {
        LOGGER.info("ensureCompanionExists for character={}", character);
        Optional<Component> deny = CompanionSpawnPolicy.denial(this._player, character, this);
        if (deny.isPresent()) {
            this._player.sendSystemMessage(deny.get());
            return;
        }
        if (this._player.level() != null && this._player.getServer() != null) {
            LOGGER.info("ensureCompanionExists NOTNULL");
            // Dismiss stores a snapshot in _despawnedCompanionData. The old "restore from NBT" path was
            // commented out to stop auto-respawns; leaving the key set with an empty branch made re-summon a no-op.
            // Drop the stale snapshot so we can spawn/teleport like a fresh request.
            if (this._despawnedCompanionData.containsKey(character.name())) {
                LOGGER.info("ensureCompanionExists: clearing despawned snapshot (restore disabled) so summon can proceed");
                this._despawnedCompanionData.remove(character.name());
                writeToNbt();
            }
            UUID companionUuid = (UUID) this._companionMap.get(character.name());
            // Resolve the mapped companion across ALL dimensions. The old code used
            // this._player.serverLevel().getEntity(uuid) — a single-dimension check that returned null
            // whenever the companion sat in another dimension, so ensureCompanionExists then SPAWNED a
            // CLONE and overwrote the map, orphaning the still-alive original (one clone per dimension
            // change). classifySummon already searched all levels; this makes the two symmetric.
            Located located = this.resolveLoadedCompanion(companionUuid);
            // "Not loaded in any level" can also mean "merely unloaded", not "gone". Before spawning
            // (which would clone next to a healthy, sleeping companion) consult the location hint and
            // force-load that one chunk. That entity load is ASYNC (see resolveViaLocationHint), so a
            // hinted-but-not-yet-surfaced companion cannot be confirmed this tick.
            if (located == null && companionUuid != null) {
                located = this.resolveViaLocationHint(companionUuid);
                if (located == null && CompanionLocationTracker.get(companionUuid) != null) {
                    // A hint exists but the entity is not in the live lookup yet (async chunk+entity load in
                    // flight). DEFER rather than clone: serverTick retries until it loads (then relocate/TP)
                    // or the hint proves stale after MAX_RELOAD_RETRY_TICKS (then spawn). resolveViaLocationHint
                    // above already kicked off the load.
                    this._pendingReloads.put(character.name(), new PendingReload(character, MAX_RELOAD_RETRY_TICKS));
                    LOGGER.info("ensureCompanionExists DEFER (awaiting async reload) for {}", character.name());
                    return;
                }
            }
            // Reached a decision (found, or genuinely absent with no hint): cancel any stale deferral.
            this._pendingReloads.remove(character.name());
            if (located != null && located.entity().isAlive()) {
                this.teleportOrRelocate(character, located);
            } else {
                LOGGER.info("ensureCompanionExists SPAWN");
                try {
                    spawnCompanion(character);
                    System.out.println("Summoned new companion: " + character.name() + " for player " + this._player.getName().getString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                writeToNbt();
            }

        }
    }

    /**
     * Act on a resolved, alive companion: same dimension -&gt; teleport in place; different dimension -&gt;
     * relocate via dismiss+respawn (MC {@code moveTo} cannot cross levels). Shared by the direct
     * {@link #ensureCompanionExists} path and the deferred {@link #processReloadRetries} path so both treat
     * a reunion identically (and neither ever clones a still-alive companion).
     */
    private void teleportOrRelocate(Character character, Located located) {
        AutomatoneEntity automatone = located.entity();
        if (located.level() == this._player.serverLevel()) {
            LOGGER.info("ensureCompanionExists TP");
            // Same dimension: teleport in place (unchanged behavior).
            // Reattach owner: world load / server restart paths build the controller without an owner,
            // and prior teleport-on-rejoin left it unset. Without this, ConversationManager.process
            // skips the bot (owner != null filter) and the user must despawn+resummon to recover.
            automatone.reattachOwner(this._player);
            BlockPos spawnPos = this._player.blockPosition().offset(this._player.getRandom().nextInt(3) - 1, 1, this._player.getRandom().nextInt(3) - 1);
            automatone.moveTo((double) spawnPos.getX() + (double) 0.5F, (double) spawnPos.getY(), (double) spawnPos.getZ() + (double) 0.5F);
            System.out.println("Teleported existing companion: " + character.name() + " for player " + this._player.getName().getString());
        } else {
            LOGGER.info("ensureCompanionExists RELOCATE");
            // Different dimension: MC moveTo cannot cross levels. Reuse the proven dismiss+respawn
            // path (the exact mechanism run on every relog) to bring the companion to the player's
            // current dimension, preserving inventory via the per-owner inventory file. The
            // companion UUID changes — already normal on relog.
            this.relocateAcrossDimension(character, automatone);
        }
    }

    /**
     * Bring a still-alive companion currently loaded in a DIFFERENT dimension to the owner's current
     * dimension. MC {@code moveTo} cannot move an entity between levels, so we reuse the proven
     * dismiss+respawn mechanism (identical to what a relog already does): persist the inventory to the
     * per-owner file, run the truthfulness/cleanup hooks, discard the old entity, then respawn a fresh
     * one in the owner's current level whose {@code init()} reloads the just-saved inventory. Runs on
     * the server thread synchronously (like {@link #dismissCompanion}), so the save completes before the
     * old entity is discarded and the new one constructed.
     */
    private void relocateAcrossDimension(Character character, AutomatoneEntity oldCompanion) {
        // Persist inventory + history BEFORE discard so the respawn reloads it (keyed by owner+characterId).
        PersistentDataManager.saveInventoryNow(oldCompanion);
        if (oldCompanion.controller != null) {
            // Truthfulness hook: only notifies player+model when a task/agentic run was actually active
            // (internal check); an idle relocate stays silent. Matches DESIGN.md §3.
            oldCompanion.controller.stopWithRespawnNotification(this._player);
            oldCompanion.controller.unregisterFromGlobalRegistry();
        }
        ConversationManager.despwnCompanion(oldCompanion.getUUID());
        CompanionLocationTracker.clear(oldCompanion.getUUID());
        oldCompanion.discard();
        // Respawn in the owner's CURRENT dimension; DIMENSION_RELOCATE = silent (no greeting/return spam).
        // The old entity is ALREADY discarded, so a spawn failure would otherwise leave the character with
        // NO companion and propagate out of the tick/packet handler. Log and let the next summon self-heal:
        // the stale UUID + hint were cleared above, so classifySummon then reads CREATE_NEW cleanly.
        try {
            spawnCompanion(character, SpawnReason.DIMENSION_RELOCATE, null);
        } catch (Exception e) {
            // The old entity is ALREADY discarded above, so a respawn failure here means the companion has
            // silently VANISHED. Make the failure VISIBLE to the owner (DESIGN.md §3 "visible degradation,
            // never silent") instead of only printing a trace: a short keyed chat line telling them to
            // summon again. There is no model surface to reach — the entity failed to spawn, so there is no
            // bot to speak; the player-visible line + a console warn is the required outcome. The throwable
            // stays on the console only (never routed into a prompt — data-egress rule).
            if (character != null) {
                this._player.sendSystemMessage(Component.translatable(
                        "message.player2npc.companion.relocate_failed", character.shortName()));
            }
            LOGGER.warn("Failed to respawn relocated companion {} for {}",
                    character != null ? character.name() : "?", this._player.getName().getString(), e);
        }
        writeToNbt();
        System.out.println("Relocated companion across dimension: " + character.name() + " for player " + this._player.getName().getString());
    }

    public void spawnCompanion(Character character){
        spawnCompanion(character, SpawnReason.RETURNING, null);
    }

    public void spawnCompanion(Character character, SpawnReason reason, String deathCause){
        BlockPos spawnPos = this._player.blockPosition().offset(this._player.getRandom().nextInt(3) - 1, 1, this._player.getRandom().nextInt(3) - 1);
        AutomatoneEntity newCompanion = new AutomatoneEntity(this._player.level(), character, this._player, reason, deathCause);
        newCompanion.moveTo((double) spawnPos.getX() + (double) 0.5F, (double) spawnPos.getY(), (double) spawnPos.getZ() + (double) 0.5F, this._player.getYRot(), 0.0F);
        this._player.level().addFreshEntity(newCompanion);
        this._companionMap.put(character.name(), newCompanion.getUUID());
    }

    public void dismissCompanion(String characterName) {
        // Cancel any in-flight deferred reload for this character before touching its mapping.
        this._pendingReloads.remove(characterName);
        UUID companionUuid = (UUID) this._companionMap.get(characterName);
        if (companionUuid == null || this._player.getServer() == null) {
            // Nothing mapped (or no server to resolve against): drop any stale key and return.
            this._companionMap.remove(characterName);
            return;
        }
        // Resolve across ALL dimensions BEFORE mutating the canonical mapping (cross-dimension companions
        // are found here — the same all-levels search classifySummon uses). We deliberately do NOT force-load
        // a merely-unloaded companion's chunk here: that entity load is async (see resolveViaLocationHint) so
        // it would not surface this tick anyway, and fail-open below is the correct, safe outcome for it.
        Located located = this.resolveLoadedCompanion(companionUuid);
        if (located == null) {
            // Could not positively confirm the entity is loaded/present (merely unloaded, or genuinely gone).
            // FAIL-OPEN: do NOT silently delete the canonical mapping — that would orphan a still-alive
            // companion from the cleanup command's "canonical entry must exist" rail and make
            // getActiveCompanions under-count it (letting the cap be exceeded once a fresh spawn is later
            // triggered for the same name while the old entity is still alive somewhere). A stale mapping to a
            // truly-gone entity self-corrects on the next spawn (which overwrites the UUID); a merely-unloaded
            // companion keeps its correct mapping and is reunited by the ensureCompanionExists reload path.
            return;
        }
        AutomatoneEntity automatone = located.entity();
        // Persist inventory to per-world file before we discard the entity, so re-summon can load it.
        PersistentDataManager.saveInventoryNow(automatone);
        // Detect an interrupted active task and notify player + model BEFORE the
        // conversation queue is wiped by despwnCompanion (which would drop the InfoMessage).
        if (automatone.controller != null) {
            automatone.controller.stopWithRespawnNotification(this._player);
            automatone.controller.unregisterFromGlobalRegistry();
        }
        // Ensure no prompts are processed for a despawned companion.
        ConversationManager.despwnCompanion(automatone.getUUID());
        CompoundTag savedState = new CompoundTag();
        automatone.addAdditionalSaveData(savedState);
        this._despawnedCompanionData.put(characterName, savedState);
        // Only now remove the mapping — the entity was positively found. (The hint is cleared by
        // AutomatoneEntity.remove()'s shouldDestroy chokepoint when discard() lands.)
        this._companionMap.remove(characterName);
        automatone.discard();
        System.out.println("Dismissed companion: " + characterName + " for player " + this._player.getName().getString());
        writeToNbt();
    }

    public void dismissAllCompanions() {
        // Each dismissCompanion already maintains _companionMap on its own: it removes the mapping in the
        // branch where the entity was positively found, and deliberately FAIL-OPENs (leaves the mapping
        // intact) for a merely-unloaded companion it could not confirm. An unconditional _companionMap.clear()
        // here would wipe those fail-open-preserved mappings, re-introducing the under-count / orphan the
        // revised dismissCompanion exists to prevent — so it is intentionally omitted.
        List<String> names = new ArrayList(this._companionMap.keySet());
        names.forEach(this::dismissCompanion);
    }

    /** Removes companion map and despawned snapshot entries without spawning discard logic (used when purging storage). */
    public void removeCompanionMapping(String characterName) {
        if (characterName == null) {
            return;
        }
        this._companionMap.remove(characterName);
        this._despawnedCompanionData.remove(characterName);
        writeToNbt();
    }

    public List<AutomatoneEntity> getActiveCompanions() {
        List<AutomatoneEntity> companions = new ArrayList();
        if (this._player.getServer() == null) {
            return companions;
        } else {
            for(UUID uuid : this._companionMap.values()) {
                for(ServerLevel world : this._player.getServer().getAllLevels()) {
                    Entity entity = world.getEntity(uuid);
                    if (entity instanceof AutomatoneEntity) {
                        AutomatoneEntity companion = (AutomatoneEntity)entity;
                        if (companion.isAlive()) {
                            companions.add(companion);
                            break;
                        }
                    }
                }
            }

            return companions;
        }
    }

    public void serverTick() {
        if (this._needsToSummon && !this._assignedCharacters.isEmpty()) {
            this.summonCompanions();
            this._needsToSummon = false;
        }
        if (!this._pendingReloads.isEmpty()) {
            this.processReloadRetries();
        }
    }

    /**
     * Drives the deferred reunion for a hinted-but-unloaded companion whose async chunk+entity reload was
     * kicked off by {@link #ensureCompanionExists}. Each tick: if the entity has finished loading, relocate/TP
     * it (never cloning); if the hint proves stale after {@link #MAX_RELOAD_RETRY_TICKS} ticks, clear it and
     * spawn a genuine replacement; if the mapping was removed meanwhile, abandon the retry. This is what makes
     * the location-hint mechanism actually correct given that entity deserialization is asynchronous.
     */
    private void processReloadRetries() {
        List<String> done = new ArrayList();
        for (Map.Entry<String, PendingReload> entry : this._pendingReloads.entrySet()) {
            String name = entry.getKey();
            PendingReload pending = entry.getValue();
            UUID uuid = this._companionMap.get(name);
            if (uuid == null) {
                // Mapping was cleared elsewhere (dismiss / storage purge); nothing to reunite.
                done.add(name);
                continue;
            }
            Located located = this.resolveLoadedCompanion(uuid);
            if (located != null && located.entity().isAlive()) {
                done.add(name);
                this.teleportOrRelocate(pending.character, located);
                writeToNbt();
                continue;
            }
            if (--pending.ticksLeft <= 0) {
                // The hint never resolved to a loaded entity within the budget: treat it as stale, drop it,
                // and spawn a genuine replacement (the cleanup command remains the backstop if the original
                // somehow reappears loaded later).
                done.add(name);
                CompanionLocationTracker.clear(uuid);
                LOGGER.info("processReloadRetries: hint stale for {}, spawning fresh", name);
                try {
                    spawnCompanion(pending.character);
                } catch (Exception e) {
                    // Replacement spawn failed — the companion is now absent with no signal. Surface it to
                    // the owner (DESIGN.md §3 "visible degradation, never silent") rather than only printing a
                    // trace; the console warn keeps the stack for diagnostics (never routed to a model/prompt
                    // — data-egress rule).
                    if (pending.character != null) {
                        this._player.sendSystemMessage(Component.translatable(
                                "message.player2npc.companion.relocate_failed", pending.character.shortName()));
                    }
                    LOGGER.warn("Failed to spawn replacement companion {} for {}",
                            pending.character != null ? pending.character.name() : "?", this._player.getName().getString(), e);
                }
                writeToNbt();
            }
        }
        done.forEach(this._pendingReloads::remove);
    }

    public void readFromNbt() {
        CompoundTag tag = ((IEntityPersistentData)_player).getPersistentData();
        if(tag.isEmpty()) return;
        this._companionMap.clear();
        this._despawnedCompanionData.clear();
        CompoundTag companionsTag = tag.getCompound("companions");

        for(String key : companionsTag.getAllKeys()) {
            this._companionMap.put(key, companionsTag.getUUID(key));
        }

        CompoundTag despawnedTag = tag.getCompound("despawnedCompanions");

        for(String key : despawnedTag.getAllKeys()) {
            this._despawnedCompanionData.put(key, despawnedTag.getCompound(key));
        }

        // Restore persisted location hints into the (session-scoped) tracker. Without this a post-CRASH
        // restart (no clean PLAYER_QUIT -> dismissAllCompanions ran) would leave the tracker EMPTY, so a
        // companion saved in an unloaded far/other-dimension chunk would resolve as "gone" and be CLONED —
        // the exact bug for the very scenario the hint exists to cover. Persisting the last-known coarse
        // dimension+position makes a crash restart no worse than a mid-session unload.
        CompoundTag locationsTag = tag.getCompound("companionLocations");
        for (String uuidKey : locationsTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                CompoundTag h = locationsTag.getCompound(uuidKey);
                ResourceLocation dimLoc = ResourceLocation.tryParse(h.getString("dim"));
                if (dimLoc == null) {
                    continue;
                }
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimLoc);
                CompanionLocationTracker.record(uuid, dim, new BlockPos(h.getInt("x"), h.getInt("y"), h.getInt("z")));
            } catch (IllegalArgumentException malformed) {
                // Malformed UUID / dimension string in stored NBT — skip this ONE hint, never crash readback.
                // Log the offending (bounded) NBT key so a corrupt hint is diagnosable; no unbounded strings.
                LOGGER.warn("Skipping malformed persisted companion location hint for key {}", uuidKey);
            }
        }
    }

    public void writeToNbt() {
        CompoundTag tag = ((IEntityPersistentData)_player).getPersistentData();
        CompoundTag companionsTag = new CompoundTag();
        this._companionMap.forEach(companionsTag::putUUID);
        tag.put("companions", companionsTag);
        CompoundTag despawnedTag = new CompoundTag();
        this._despawnedCompanionData.forEach(despawnedTag::put);
        tag.put("despawnedCompanions", despawnedTag);
        // Persist a coarse last-known location hint per canonical companion (dimension-generic: the
        // dimension is stored as its ResourceLocation string, so modded dimensions round-trip). Only
        // companions that have ticked at least once (tracker has an entry) contribute a hint; it is a
        // best-effort backstop, snapshotted whenever this map is written.
        CompoundTag locationsTag = new CompoundTag();
        this._companionMap.forEach((name, uuid) -> {
            CompanionLocationTracker.LocationHint hint = CompanionLocationTracker.get(uuid);
            if (hint != null) {
                CompoundTag h = new CompoundTag();
                h.putString("dim", hint.dimension().location().toString());
                h.putInt("x", hint.pos().getX());
                h.putInt("y", hint.pos().getY());
                h.putInt("z", hint.pos().getZ());
                locationsTag.put(uuid.toString(), h);
            }
        });
        tag.put("companionLocations", locationsTag);
    }
}
