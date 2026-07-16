//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.goodbird.player2npc.companion;

import com.goodbird.player2npc.mixins.IEntityPersistentData;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.AiConversationFeedback;
import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.world.level.chunk.ChunkStatus;
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
     * Retried each {@link #serverTick()} until the entity loads or the hint proves stale after
     * {@link #MAX_RELOAD_RETRY_TICKS}; either outcome re-enters the common reconciliation state machine.
     * Keyed by character name to mirror {@link #_companionMap}.
     */
    private final Map<String, PendingReload> _pendingReloads = new ConcurrentHashMap();
    /** Two delayed, bounded passes catch exact historical clones whose chunks load after summon. */
    private final Map<String, PendingIdentityRescan> _pendingIdentityRescans = new ConcurrentHashMap();
    private final Map<String, PendingManualDismiss> _pendingManualDismissals = new ConcurrentHashMap();
    private final Set<String> _explicitBarrierRestorePending = ConcurrentHashMap.newKeySet();
    private List<Character> _assignedCharacters = new ArrayList();
    private boolean _needsToSummon = false;
    private static final Map<String, CompanionManager> cache = new HashMap<>();
    /** ~5s at 20 tps — an async chunk+entity load completes well within this; longer means the hint is stale. */
    private static final int MAX_RELOAD_RETRY_TICKS = 100;
    private static final int IDENTITY_RESCAN_INTERVAL_TICKS = 40;
    private static final int MAX_IDENTITY_RESCAN_ATTEMPTS = 2;
    private static final int PASSIVE_MANUAL_DISMISS_POLL_TICKS = 20;
    private static final String DEATH_TASK_CANCELLED_MODEL_NOTE =
            "A companion instance died while a task was running. That task was cancelled during"
                    + " death recovery and did not complete.";
    private static final String NO_AUTO_DEATH_MODEL_NOTE =
            "You died while auto-respawn was disabled. You were not respawned until your owner"
                    + " explicitly summoned you.";
    private static final String NO_AUTO_DEATH_TASK_MODEL_NOTE =
            "You died while auto-respawn was disabled. You were not respawned until your owner"
                    + " explicitly summoned you. Your interrupted task was cancelled and did not complete.";
    private static final String OPERATOR_DISMISS_MODEL_NOTE =
            "Your owner dismissed you. You remained despawned until the owner explicitly summoned you again.";
    private static final String OPERATOR_DISMISS_TASK_MODEL_NOTE =
            "Your owner dismissed you while a task was running. That task was cancelled and did not complete."
                    + " You remained despawned until explicitly summoned again.";
    private static final String SESSION_UNLOAD_MODEL_NOTE =
            "You were temporarily unloaded while your owner session changed. Your state was preserved"
                    + " for automatic restoration.";
    private static final String SESSION_UNLOAD_TASK_MODEL_NOTE =
            "You were temporarily unloaded while a task was running. That task was cancelled and did not"
                    + " complete. Your state was preserved for automatic restoration.";
    private static final String KEY_DEATH_RESTORE = "lifecycle_death_restore";
    private static final String KEY_DEATH_CAUSE = "lifecycle_death_cause";
    private static final String KEY_TOMBSTONE_UUID = "lifecycle_tombstone_uuid";
    private static final String KEY_MANUAL_SUMMON_REQUIRED = "lifecycle_manual_summon_required";
    private static final String KEY_MANUAL_DISMISS_BARRIERS = "companionManualDismissBarriers";
    private static final String KEY_BARRIER_OWNER = "ownerUuid";
    private static final String KEY_BARRIER_CHARACTER_ID = "characterId";
    private static final String KEY_BARRIER_CHARACTER_NAME = "characterName";
    private static final String KEY_BARRIER_MAPPED_UUID = "mappedUuid";

    /** Mutable per-character retry state for a deferred (awaiting async reload) companion. */
    private static final class PendingReload {
        final Character character;
        final String deferredLifecycleNote;
        int ticksLeft;

        PendingReload(Character character, int ticksLeft) {
            this(character, ticksLeft, null);
        }

        PendingReload(Character character, int ticksLeft, String deferredLifecycleNote) {
            this.character = character;
            this.ticksLeft = ticksLeft;
            this.deferredLifecycleNote = deferredLifecycleNote;
        }
    }

    private static final class PendingIdentityRescan {
        final Character character;
        int ticksLeft;
        int attemptsLeft;

        PendingIdentityRescan(Character character, int ticksLeft, int attemptsLeft) {
            this.character = character;
            this.ticksLeft = ticksLeft;
            this.attemptsLeft = attemptsLeft;
        }
    }

    private static final class PendingManualDismiss {
        final Character character;
        final UUID mappedUuid;
        int ticksLeft;
        boolean forceLoadPhase = true;

        PendingManualDismiss(Character character, UUID mappedUuid, int ticksLeft) {
            this.character = character;
            this.mappedUuid = mappedUuid;
            this.ticksLeft = ticksLeft;
        }
    }

    public record DeathRespawnResult(boolean canonicalReady, boolean spawnedReplacement) {
    }

    record TerminalDeathResult(boolean mappedCanonical, boolean snapshotReady) {
    }

    record DeathStateReceipt(
            UUID dyingUuid,
            boolean exactIdentity,
            boolean mappedAuthority,
            boolean stateReady,
            CompoundTag inventoryState,
            boolean persistedSynchronously) {
        DeathStateReceipt {
            inventoryState = inventoryState == null ? null : inventoryState.copy();
        }
    }

    private record ReconciliationOutcome(AutomatoneEntity canonical, boolean spawnedFresh) {
    }

    private record SpawnedCompanion(
            AutomatoneEntity entity,
            CompanionIdentityReconciliationPolicy.CanonicalReceipt receipt) {
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
        UUID companionUuid = this._companionMap.get(name);
        Located located = this.resolveLoadedCompanion(companionUuid);
        List<Located> exactMatches = this.resolveLoadedCompanionsByIdentity(character);
        boolean mappedLoadedExact = located != null
                && located.entity().isAlive()
                && exactMatches.stream().anyMatch(match -> match.entity().getUUID().equals(companionUuid));
        if (mappedLoadedExact || !exactMatches.isEmpty()) {
            return SummonIntent.TELEPORT_ALIVE;
        }
        // Not loaded in any dimension, but a live location hint means the companion is alive-but-unloaded:
        // the hint is recorded while the entity ticks and cleared ONLY on terminal removal (death/discard,
        // via AutomatoneEntity.remove()'s shouldDestroy chokepoint) — it survives a chunk unload. A summon
        // will RELOCATE/reload such a companion, not create a new one, so it must NOT read as CREATE_NEW:
        // that would let denial()/filterForJoin wrongly count a live reunion against the spawn cap (deny it,
        // or drop it from the join batch). No force-load here — the hint's presence is sufficient and, unlike
        // resolveViaLocationHint's post-load getEntity, it is not subject to the async entity-load race.
        if (companionUuid != null && CompanionLocationTracker.get(companionUuid) != null) {
            return SummonIntent.TELEPORT_ALIVE;
        }
        if (this.hasExactDespawnedSnapshot(character)) {
            return SummonIntent.RESTORE_DESPAWNED;
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
     * Loaded-only fallback for a lost/stale canonical UUID. Matching is deliberately strict: both
     * owner UUID and nonblank Player2 character id must match. Display names are never identity.
     */
    private List<Located> resolveLoadedCompanionsByIdentity(Character requestedCharacter) {
        MinecraftServer server = this._player.getServer();
        String requestedCharacterId = stableCharacterId(requestedCharacter);
        if (server == null || requestedCharacterId == null) {
            return List.of();
        }
        Map<UUID, Located> locatedByUuid = new HashMap<>();
        List<CompanionIdentityReconciliationPolicy.Candidate> candidates = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof AutomatoneEntity auto)) {
                    continue;
                }
                boolean sameDimension = level == this._player.serverLevel();
                double distanceSquared = sameDimension
                        ? auto.distanceToSqr(this._player)
                        : Double.POSITIVE_INFINITY;
                locatedByUuid.put(auto.getUUID(), new Located(auto, level));
                candidates.add(new CompanionIdentityReconciliationPolicy.Candidate(
                        auto.getUUID(),
                        OwnerCharacterStoragePaths.ownerUuidOrNull(auto),
                        stableCharacterId(auto.character),
                        auto.isAlive(),
                        auto.isRemoved(),
                        sameDimension,
                        distanceSquared));
            }
        }
        List<Located> matches = new ArrayList<>();
        for (UUID selected : CompanionIdentityReconciliationPolicy.select(
                this._player.getUUID(), requestedCharacterId, candidates)) {
            Located candidate = locatedByUuid.get(selected);
            if (candidate != null) {
                matches.add(candidate);
            }
        }
        return List.copyOf(matches);
    }

    private static String stableCharacterId(Character character) {
        if (character == null || character.id() == null || character.id().isBlank()) {
            return null;
        }
        return character.id();
    }

    /**
     * A legacy name-keyed snapshot may only authorize destructive recovery when its embedded stable
     * identity is exact. Same-name, malformed, or ownerless snapshots remain untouched and inert.
     */
    private boolean hasExactDespawnedSnapshot(Character requestedCharacter) {
        String requestedId = stableCharacterId(requestedCharacter);
        if (requestedCharacter == null || requestedCharacter.name() == null || requestedId == null) {
            return false;
        }
        CompoundTag snapshot = this._despawnedCompanionData.get(requestedCharacter.name());
        if (snapshot == null || !snapshot.contains("character", 10) || !snapshot.hasUUID("owner_uuid")) {
            return false;
        }
        try {
            Character snapshotCharacter = CharacterUtils.readFromNBT(snapshot.getCompound("character"));
            return CompanionIdentityReconciliationPolicy.snapshotIdentityMatches(
                    this._player.getUUID(),
                    requestedId,
                    snapshot.getUUID("owner_uuid"),
                    stableCharacterId(snapshotCharacter));
        } catch (RuntimeException malformed) {
            LOGGER.warn("Ignoring malformed despawned companion identity snapshot for owner={}",
                    this._player.getUUID());
            return false;
        }
    }

    /**
     * Last-resort resolve for a mapped companion that is not loaded in any level: a merely-unloaded
     * companion returns null from {@link #resolveLoadedCompanion} exactly like a discarded one, so before
     * concluding "gone -&gt; spawn" (which would clone next to a healthy, sleeping companion) we consult the
     * {@link CompanionLocationTracker} hint and force-load that ONE chunk. Dimension-generic: uses whatever
     * {@code ResourceKey<Level>} was recorded, resolved through the server's own live registry, so modded
     * dimensions work too.
     *
     * <p><b>Async caveat (verified against decompiled-sources/1.20.1):</b> forcing a chunk to
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
            toDismiss.forEach(name -> this.dismissCompanion(name, false, null));
            this._assignedCharacters.stream().filter((character) -> character != null).forEach((character) -> {
                LOGGER.info("summonCompanions for character={}", character);
                this.ensureCompanionExists(character, true);
            });
            this._assignedCharacters.clear();
            writeToNbt();
        }
    }

    public void ensureCompanionExists(Character character) {
        this.ensureCompanionExists(character, false);
    }

    private void ensureCompanionExists(Character character, boolean automatic) {
        LOGGER.info("ensureCompanionExists for character={}", character);
        if (character == null) {
            return;
        }
        Optional<Component> deny = CompanionSpawnPolicy.denial(this._player, character, this);
        if (deny.isPresent()) {
            this._player.sendSystemMessage(deny.get());
            return;
        }
        UUID manualDismissBarrier = this.manualDismissBarrier(character);
        if (automatic && manualDismissBarrier != null) {
            UUID currentMapped = this._companionMap.get(character.name());
            this.queuePendingManualDismiss(
                    character, currentMapped != null ? currentMapped : manualDismissBarrier);
            LOGGER.info("Automatic companion restore blocked by explicit-dismiss barrier for character={}",
                    character.name());
            return;
        }
        if (!automatic && manualDismissBarrier != null) {
            // An explicit summon deliberately supersedes the pending cleanup attempt. The persisted
            // barrier itself remains until reconciliation has an accepted canonical receipt.
            this._pendingManualDismissals.remove(character.name());
        }
        CompoundTag exactSnapshot = this.exactSnapshotState(character);
        if (automatic && exactSnapshot != null
                && exactSnapshot.getBoolean(KEY_MANUAL_SUMMON_REQUIRED)) {
            LOGGER.info("Automatic companion restore deferred until explicit summon for character={}",
                    character.name());
            return;
        }
        if (this._player.level() != null && this._player.getServer() != null) {
            LOGGER.info("ensureCompanionExists NOTNULL");
            UUID companionUuid = this._companionMap.get(character.name());
            Located located = this.resolveLoadedCompanion(companionUuid);
            if (located == null && companionUuid != null) {
                located = this.resolveViaLocationHint(companionUuid);
                if (located == null && CompanionLocationTracker.get(companionUuid) != null) {
                    // Retain every receipt, including any exact despawn snapshot, while the mapped
                    // entity's async chunk load is pending. Success and timeout both re-enter the same
                    // reconciliation state machine below; neither may direct-spawn around it.
                    if (!automatic && manualDismissBarrier != null) {
                        this._explicitBarrierRestorePending.add(character.name());
                    }
                    this.queuePendingReload(character, null);
                    LOGGER.info("ensureCompanionExists DEFER (awaiting async reload) for {}", character.name());
                    return;
                }
            }
            this._pendingReloads.remove(character.name());
            ReconciliationOutcome outcome = this.reconcileCompanion(character, companionUuid, located);
            if (!automatic && manualDismissBarrier != null && outcome.canonical() != null) {
                this.clearManualDismissBarrier(character, manualDismissBarrier);
            }
        }
    }

    /** Reconciles map, exact stable identity, snapshot and world/state receipts as one commit. */
    private ReconciliationOutcome reconcileCompanion(Character character, UUID mappedUuid, Located mappedLocated) {
        List<Located> identityMatches = this.resolveLoadedCompanionsByIdentity(character);
        List<UUID> exactUuids = identityMatches.stream().map(match -> match.entity().getUUID()).toList();
        boolean mappedLoadedExact = mappedLocated != null
                && mappedLocated.entity().isAlive()
                && !mappedLocated.entity().isRemoved()
                && exactUuids.contains(mappedUuid);
        boolean exactSnapshot = this.hasExactDespawnedSnapshot(character);
        CompanionIdentityReconciliationPolicy.Decision decision =
                CompanionIdentityReconciliationPolicy.decide(
                        mappedUuid, mappedLoadedExact, exactSnapshot, exactUuids);
        // Keep the exact receipt available even on KEEP_MAPPED so a lifecycle note whose first
        // delivery failed is retried before that snapshot/tombstone is consumed.
        CompoundTag authoritativeState = exactSnapshot
                ? this.exactSnapshotState(character) : null;
        SpawnReason restoreReason = authoritativeState != null
                        && authoritativeState.getBoolean(KEY_DEATH_RESTORE)
                ? SpawnReason.DEATH_RESPAWN : SpawnReason.RETURNING;
        String restoreDeathCause = restoreReason == SpawnReason.DEATH_RESPAWN
                ? boundedSingleLine(authoritativeState.getString(KEY_DEATH_CAUSE), 256) : null;
        return this.executeReconciliation(
                character,
                mappedLocated,
                identityMatches,
                decision,
                restoreReason,
                restoreDeathCause,
                authoritativeState,
                true);
    }

    private void queuePendingReload(Character character, String deferredLifecycleNote) {
        if (character == null || character.name() == null) {
            return;
        }
        this._pendingReloads.compute(character.name(), (name, existing) -> {
            String note = deferredLifecycleNote != null
                    ? deferredLifecycleNote
                    : existing == null ? null : existing.deferredLifecycleNote;
            return new PendingReload(character, MAX_RELOAD_RETRY_TICKS, note);
        });
    }

    private void queuePendingManualDismiss(Character character, UUID mappedUuid) {
        if (character == null || character.name() == null || mappedUuid == null) {
            return;
        }
        this._pendingManualDismissals.compute(character.name(), (name, existing) ->
                existing != null && mappedUuid.equals(existing.mappedUuid)
                        ? existing
                        : new PendingManualDismiss(character, mappedUuid, MAX_RELOAD_RETRY_TICKS));
        this.resolveViaLocationHint(mappedUuid);
    }

    private UUID manualDismissBarrier(Character character) {
        if (character == null || character.name() == null || stableCharacterId(character) == null) {
            return null;
        }
        CompoundTag playerTag = ((IEntityPersistentData) this._player).getPersistentData();
        CompoundTag barriers = playerTag.getCompound(KEY_MANUAL_DISMISS_BARRIERS);
        if (!barriers.contains(character.name(), 10)) {
            return null;
        }
        CompoundTag barrier = barriers.getCompound(character.name());
        if (!barrier.hasUUID(KEY_BARRIER_OWNER)
                || !this._player.getUUID().equals(barrier.getUUID(KEY_BARRIER_OWNER))
                || !barrier.hasUUID(KEY_BARRIER_MAPPED_UUID)
                || !barrier.contains(KEY_BARRIER_CHARACTER_ID, 8)
                || !stableCharacterId(character).equals(barrier.getString(KEY_BARRIER_CHARACTER_ID))
                || !barrier.contains(KEY_BARRIER_CHARACTER_NAME, 8)
                || !character.name().equals(barrier.getString(KEY_BARRIER_CHARACTER_NAME))) {
            return null;
        }
        return barrier.getUUID(KEY_BARRIER_MAPPED_UUID);
    }

    private void storeManualDismissBarrier(Character character, UUID mappedUuid) {
        CompoundTag playerTag = ((IEntityPersistentData) this._player).getPersistentData();
        CompoundTag barriers = playerTag.getCompound(KEY_MANUAL_DISMISS_BARRIERS);
        CompoundTag barrier = new CompoundTag();
        barrier.putUUID(KEY_BARRIER_OWNER, this._player.getUUID());
        barrier.putString(KEY_BARRIER_CHARACTER_ID, stableCharacterId(character));
        barrier.putString(KEY_BARRIER_CHARACTER_NAME, character.name());
        barrier.putUUID(KEY_BARRIER_MAPPED_UUID, mappedUuid);
        barriers.put(character.name(), barrier);
        playerTag.put(KEY_MANUAL_DISMISS_BARRIERS, barriers);
    }

    private void clearManualDismissBarrier(Character character, UUID mappedUuid) {
        if (character == null || character.name() == null || mappedUuid == null) {
            return;
        }
        CompoundTag playerTag = ((IEntityPersistentData) this._player).getPersistentData();
        CompoundTag barriers = playerTag.getCompound(KEY_MANUAL_DISMISS_BARRIERS);
        CompoundTag barrier = barriers.getCompound(character.name());
        if (barrier.hasUUID(KEY_BARRIER_OWNER)
                && this._player.getUUID().equals(barrier.getUUID(KEY_BARRIER_OWNER))
                && barrier.contains(KEY_BARRIER_CHARACTER_ID, 8)
                && stableCharacterId(character) != null
                && stableCharacterId(character).equals(barrier.getString(KEY_BARRIER_CHARACTER_ID))
                && barrier.contains(KEY_BARRIER_CHARACTER_NAME, 8)
                && character.name().equals(barrier.getString(KEY_BARRIER_CHARACTER_NAME))) {
            barriers.remove(character.name());
            playerTag.put(KEY_MANUAL_DISMISS_BARRIERS, barriers);
            this._pendingManualDismissals.remove(character.name());
            this._explicitBarrierRestorePending.remove(character.name());
        }
    }

    boolean hasManualDismissIntent(Character character) {
        return this.manualDismissBarrier(character) != null;
    }

    private ReconciliationOutcome executeReconciliation(
            Character character,
            Located mappedLocated,
            List<Located> identityMatches,
            CompanionIdentityReconciliationPolicy.Decision decision,
            SpawnReason spawnReason,
            String deathCause,
            CompoundTag authoritativeState,
            boolean repositionExisting) {
        AutomatoneEntity canonical = null;
        boolean spawnedFresh = false;
        CompanionIdentityReconciliationPolicy.CanonicalReceipt canonicalReceipt =
                CompanionIdentityReconciliationPolicy.CanonicalReceipt.missing();
        switch (decision.action()) {
            case KEEP_MAPPED -> {
                Located kept = findLocated(identityMatches, decision.canonicalUuid());
                if (kept == null) {
                    kept = mappedLocated;
                }
                canonical = repositionExisting
                        ? this.teleportOrRelocate(character, kept)
                        : this.retainWithoutMoving(kept);
                if (canonical != null) {
                    canonicalReceipt = CompanionIdentityReconciliationPolicy.CanonicalReceipt.existing();
                }
            }
            case ADOPT_LOADED -> {
                Located adopted = findLocated(identityMatches, decision.canonicalUuid());
                if (adopted != null) {
                    // Persist the selected live identity before any later cleanup. A failed
                    // cross-dimension relocation leaves this still-live entity recoverable.
                    this._companionMap.put(character.name(), adopted.entity().getUUID());
                    writeToNbt();
                    canonical = repositionExisting
                            ? this.teleportOrRelocate(character, adopted)
                            : this.retainWithoutMoving(adopted);
                    if (canonical != null) {
                        canonicalReceipt = CompanionIdentityReconciliationPolicy.CanonicalReceipt.existing();
                    }
                }
            }
            case SPAWN_FRESH -> {
                try {
                    SpawnedCompanion spawned = this.spawnCompanionVerified(
                            character, spawnReason, deathCause, authoritativeState);
                    canonical = spawned.entity();
                    canonicalReceipt = spawned.receipt();
                    spawnedFresh = true;
                } catch (RuntimeException spawnFailure) {
                    this.reportCompanionSpawnFailure(character, spawnFailure);
                    if (!decision.discardAfterSuccess().isEmpty()) {
                        this.deferPreservedRecoveryFailure(identityMatches);
                    }
                }
            }
        }

        CompanionIdentityReconciliationPolicy.Completion completion =
                CompanionIdentityReconciliationPolicy.complete(decision, canonicalReceipt);
        if (!completion.canonicalReady()) {
            return new ReconciliationOutcome(null, false);
        }
        boolean lifecycleNoteDelivered = this.deliverSnapshotLifecycleNote(
                canonical, authoritativeState);
        if (completion.consumeExactSnapshot() && lifecycleNoteDelivered) {
            // exactSnapshot was validated by owner UUID + stable character id above. A malformed or
            // same-name/different-id snapshot is never removed by this recovery path.
            this._despawnedCompanionData.remove(character.name());
            this.deleteConsumedTombstone(character, authoritativeState);
        }
        boolean cancelledDuplicateTask = false;
        for (UUID discardUuid : completion.discardNow()) {
            Located orphan = findLocated(identityMatches, discardUuid);
            if (orphan != null && orphan.entity() != canonical && orphan.entity().isAlive()
                    && !orphan.entity().isRemoved()) {
                cancelledDuplicateTask |= this.discardLoadedIdentityOrphan(orphan.entity());
            }
        }
        writeToNbt();
        this.scheduleIdentityRescan(character);
        if (cancelledDuplicateTask) {
            this.reportDuplicateTaskCancellation(
                    character,
                    canonical.controller,
                    "A duplicate companion instance was removed during identity recovery. Any task running"
                            + " on that duplicate was cancelled and did not complete.");
        }
        return new ReconciliationOutcome(canonical, spawnedFresh);
    }

    private boolean deliverSnapshotLifecycleNote(
            AutomatoneEntity canonical, CompoundTag authoritativeState) {
        String note = PersistentDataManager.lifecycleNote(authoritativeState);
        if (note.isEmpty()) {
            return true;
        }
        if (canonical == null || canonical.controller == null) {
            return false;
        }
        try {
            AiConversationFeedback.deferInfo(canonical.controller, note);
            return true;
        } catch (RuntimeException deliveryFailure) {
            LOGGER.warn("Failed to transfer lifecycle note to canonical companion owner={} uuid={}",
                    this._player.getUUID(), canonical.getUUID(), deliveryFailure);
            return false;
        }
    }

    private void deleteConsumedTombstone(
            Character character, CompoundTag authoritativeState) {
        if (authoritativeState == null || !authoritativeState.hasUUID(KEY_TOMBSTONE_UUID)
                || this._player.getServer() == null || stableCharacterId(character) == null) {
            return;
        }
        OfflineDeathTombstoneStorage.deleteExact(
                this._player.getServer(),
                this._player.getUUID(),
                stableCharacterId(character),
                authoritativeState.getUUID(KEY_TOMBSTONE_UUID));
    }

    private CompoundTag exactSnapshotState(Character character) {
        if (character == null || character.name() == null) {
            return null;
        }
        CompoundTag snapshot = this._despawnedCompanionData.get(character.name());
        return snapshot == null ? null : snapshot.copy();
    }

    private AutomatoneEntity retainWithoutMoving(Located located) {
        if (located == null || !located.entity().isAlive() || located.entity().isRemoved()) {
            return null;
        }
        located.entity().reattachOwner(this._player);
        return located.entity();
    }

    private void deferPreservedRecoveryFailure(List<Located> identityMatches) {
        if (identityMatches == null) {
            return;
        }
        for (Located preserved : identityMatches) {
            if (preserved != null && preserved.entity().isAlive() && !preserved.entity().isRemoved()
                    && preserved.entity().controller != null) {
                AiConversationFeedback.deferInfo(preserved.entity().controller,
                        "Companion identity recovery could not establish the replacement, so this loaded"
                                + " instance was preserved. The recovery did not complete.");
                return;
            }
        }
    }

    private static Located findLocated(List<Located> matches, UUID uuid) {
        if (uuid == null || matches == null) {
            return null;
        }
        for (Located match : matches) {
            if (match != null && uuid.equals(match.entity().getUUID())) {
                return match;
            }
        }
        return null;
    }

    private static String boundedSingleLine(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= maximumLength
                ? singleLine : singleLine.substring(0, maximumLength);
    }

    private void reportDuplicateTaskCancellation(
            Character character,
            com.player2.playerengine.PlayerEngineController canonicalController,
            String modelMessage) {
        try {
            this._player.sendSystemMessage(Component.translatable(
                    "message.player2npc.companion.duplicate_task_cancelled",
                    character.shortName()));
        } catch (RuntimeException playerFeedbackFailure) {
            LOGGER.warn("Could not report duplicate-task cancellation to owner={} characterId={}",
                    this._player.getUUID(), stableCharacterId(character), playerFeedbackFailure);
        }
        if (canonicalController != null) {
            try {
                AiConversationFeedback.deferInfo(
                        canonicalController, boundedSingleLine(modelMessage, 240));
            } catch (RuntimeException modelFeedbackFailure) {
                LOGGER.warn("Could not defer duplicate-task cancellation owner={} characterId={}",
                        this._player.getUUID(), stableCharacterId(character), modelFeedbackFailure);
            }
        }
    }

    private boolean hasActiveWork(AutomatoneEntity entity) {
        return entity != null
                && entity.controller != null
                && (entity.controller.hasActiveNonIdleUserTask()
                        || AgenticRunRegistry.get(entity.getUUID()).isPresent());
    }

    boolean hasActiveWorkForLifecycle(AutomatoneEntity entity) {
        return this.hasActiveWork(entity);
    }

    /** Pre-vanilla-death authority gate used to prevent duplicate inventory/loot drops. */
    boolean isMappedDeathAuthority(AutomatoneEntity dying) {
        return dying != null && dying.character != null && dying.character.name() != null
                && dying.getUUID().equals(this._companionMap.get(dying.character.name()))
                && this.isExactStableIdentity(dying, dying.character);
    }

    /**
     * Captures post-vanilla-death inventory only when the dying entity is the mapped exact canonical.
     * Calling this after {@code super.die} preserves keepInventory semantics: kept items remain in the
     * snapshot, while dropped items have already been removed and therefore cannot be duplicated.
     */
    DeathStateReceipt capturePostDeathState(AutomatoneEntity dying) {
        if (dying == null || dying.character == null || dying.character.name() == null) {
            return new DeathStateReceipt(null, false, false, false, null, false);
        }
        UUID mappedUuid = this._companionMap.get(dying.character.name());
        boolean exactIdentity = this.isExactStableIdentity(dying, dying.character);
        boolean mappedAuthority = exactIdentity
                && mappedUuid != null
                && mappedUuid.equals(dying.getUUID());
        if (!mappedAuthority) {
            return new DeathStateReceipt(
                    dying.getUUID(), exactIdentity, false, false, null, false);
        }
        PersistentDataManager.InventoryStateReceipt state =
                PersistentDataManager.captureInventoryStateNow(dying);
        boolean persisted = state.ready()
                && PersistentDataManager.saveInventoryNowWithReceipt(dying);
        if (state.ready() && !persisted) {
            LOGGER.warn("Canonical post-death inventory could not be persisted synchronously for owner={}"
                            + " characterId={}; direct state transfer remains available",
                    this._player.getUUID(), stableCharacterId(dying.character));
        }
        return new DeathStateReceipt(
                dying.getUUID(),
                exactIdentity,
                true,
                state.ready(),
                state.state(),
                persisted);
    }

    /**
     * Commits a canonical death that must not auto-spawn. The mapped UUID is the authority gate:
     * a historical duplicate can be cleaned up, but can never clear/overwrite the canonical map or
     * store owner+character state.
     */
    TerminalDeathResult reconcileTerminalDeath(
            AutomatoneEntity dying,
            Character character,
            DeathStateReceipt deathState,
            boolean preserveForManualSummon,
            String lifecycleNote,
            String deathCause) {
        if (dying == null || character == null || character.name() == null
                || deathState == null || !deathState.mappedAuthority()
                || !dying.getUUID().equals(this._companionMap.get(character.name()))) {
            // A noncanonical death may expose other loaded duplicates, but it never changes the map.
            this.cleanupDelayedIdentityExtras(character);
            return new TerminalDeathResult(false, false);
        }

        boolean snapshotReady = !preserveForManualSummon;
        if (preserveForManualSummon) {
            if (!deathState.stateReady() || deathState.inventoryState() == null) {
                this.reportCompanionSpawnFailure(
                        character,
                        new IllegalStateException("Canonical terminal death state was unavailable"));
            } else {
                CompoundTag snapshot = PersistentDataManager.withLifecycleNote(
                        deathState.inventoryState(), lifecycleNote);
                snapshot.putBoolean(KEY_DEATH_RESTORE, true);
                snapshot.putBoolean(KEY_MANUAL_SUMMON_REQUIRED, true);
                if (deathCause != null && !deathCause.isBlank()) {
                    snapshot.putString(KEY_DEATH_CAUSE, deathCause);
                }
                this._despawnedCompanionData.put(character.name(), snapshot);
                snapshotReady = true;
            }
        } else {
            this._despawnedCompanionData.remove(character.name());
        }

        this._companionMap.remove(character.name(), dying.getUUID());
        this._pendingReloads.remove(character.name());
        String stableId = stableCharacterId(character);
        if (stableId != null) {
            this._pendingIdentityRescans.remove(stableId);
        }
        for (Located exact : this.resolveLoadedCompanionsByIdentity(character)) {
            AutomatoneEntity duplicate = exact.entity();
            if (!dying.getUUID().equals(duplicate.getUUID())
                    && duplicate.isAlive() && !duplicate.isRemoved()) {
                this.discardLoadedIdentityOrphan(duplicate);
            }
        }
        writeToNbt();
        if (!preserveForManualSummon && this.hasManualDismissIntent(character)) {
            this.clearManualDismissBarrier(character, dying.getUUID());
        }
        return new TerminalDeathResult(true, snapshotReady);
    }

    DeathRespawnResult reconcileAfterDeath(
            AutomatoneEntity dying,
            Character character,
            String deathCause,
            DeathStateReceipt deathState,
            boolean interruptedTask) {
        if (dying == null || character == null || character.name() == null) {
            return new DeathRespawnResult(false, false);
        }
        UUID mappedUuid = this._companionMap.get(character.name());
        Located mappedLocated = this.resolveLoadedCompanion(mappedUuid);
        if (mappedLocated == null && mappedUuid != null && !mappedUuid.equals(dying.getUUID())) {
            mappedLocated = this.resolveViaLocationHint(mappedUuid);
            if (mappedLocated == null && CompanionLocationTracker.get(mappedUuid) != null) {
                this.queuePendingReload(
                        character,
                        interruptedTask ? DEATH_TASK_CANCELLED_MODEL_NOTE : null);
                return new DeathRespawnResult(false, false);
            }
        }
        List<Located> identityMatches = this.resolveLoadedCompanionsByIdentity(character);
        List<UUID> exactUuids = identityMatches.stream().map(match -> match.entity().getUUID()).toList();
        boolean mappedLoadedExact = mappedLocated != null
                && mappedLocated.entity().isAlive()
                && !mappedLocated.entity().isRemoved()
                && exactUuids.contains(mappedUuid);
        boolean exactSnapshot = this.hasExactDespawnedSnapshot(character);
        boolean dyingIdentityExact = deathState != null && deathState.exactIdentity();
        CompanionIdentityReconciliationPolicy.Decision decision =
                CompanionIdentityReconciliationPolicy.decideAfterDeath(
                        mappedUuid,
                        dying.getUUID(),
                        dyingIdentityExact,
                        mappedLoadedExact,
                        exactSnapshot,
                        exactUuids);

        CompoundTag authoritativeState = null;
        if (CompanionIdentityReconciliationPolicy.mayUseDyingState(
                mappedUuid, dying.getUUID(), dyingIdentityExact, decision)) {
            if (deathState == null || !deathState.mappedAuthority()
                    || !deathState.stateReady() || deathState.inventoryState() == null) {
                RuntimeException missingState =
                        new IllegalStateException("Canonical post-death inventory state was unavailable");
                this.reportCompanionSpawnFailure(character, missingState);
                this.deferPreservedRecoveryFailure(identityMatches);
                return new DeathRespawnResult(false, false);
            }
            CompoundTag deathCheckpoint = PersistentDataManager.withLifecycleNote(
                    deathState.inventoryState(),
                    interruptedTask ? DEATH_TASK_CANCELLED_MODEL_NOTE : "");
            deathCheckpoint.putBoolean(KEY_DEATH_RESTORE, true);
            String boundedDeathCause = boundedSingleLine(deathCause, 256);
            if (!boundedDeathCause.isEmpty()) {
                deathCheckpoint.putString(KEY_DEATH_CAUSE, boundedDeathCause);
            }
            this._despawnedCompanionData.put(character.name(), deathCheckpoint);
            writeToNbt();
            authoritativeState = deathCheckpoint;
            // The newly durable exact checkpoint is now part of this transaction and is consumed
            // only after executeReconciliation receives state+world+note acknowledgement.
            decision = new CompanionIdentityReconciliationPolicy.Decision(
                    decision.action(),
                    decision.canonicalUuid(),
                    decision.discardAfterSuccess(),
                    true);
        } else if (decision.action() == CompanionIdentityReconciliationPolicy.Action.SPAWN_FRESH
                && exactSnapshot) {
            authoritativeState = this.exactSnapshotState(character);
        }
        if (!CompanionIdentityReconciliationPolicy.deathSpawnAuthorized(
                decision, authoritativeState != null)) {
            RuntimeException missingAuthority =
                    new IllegalStateException("Death recovery had no authoritative inventory state");
            this.reportCompanionSpawnFailure(character, missingAuthority);
            return new DeathRespawnResult(false, false);
        }

        ReconciliationOutcome outcome = this.executeReconciliation(
                character,
                mappedLocated,
                identityMatches,
                decision,
                SpawnReason.DEATH_RESPAWN,
                deathCause,
                authoritativeState,
                false);
        return new DeathRespawnResult(outcome.canonical() != null, outcome.spawnedFresh());
    }

    private boolean isExactStableIdentity(AutomatoneEntity entity, Character requestedCharacter) {
        String requestedId = stableCharacterId(requestedCharacter);
        return entity != null
                && this._player.getUUID().equals(OwnerCharacterStoragePaths.ownerUuidOrNull(entity))
                && requestedId != null
                && requestedId.equals(stableCharacterId(entity.character));
    }

    /** @return whether an active orphan task was cancelled and must be reflected to the canonical model. */
    private boolean discardLoadedIdentityOrphan(AutomatoneEntity orphan) {
        boolean active = this.hasActiveWork(orphan);
        if (orphan.controller != null) {
            // The duplicate's queue is deleted below; report any cancellation to the retained
            // canonical model instead of enqueueing information onto a doomed queue.
            orphan.controller.stop();
            orphan.controller.unregisterFromGlobalRegistry();
        }
        ConversationManager.despwnCompanion(orphan.getUUID());
        CompanionLocationTracker.clear(orphan.getUUID());
        orphan.discard();
        return active;
    }

    private void reportCompanionSpawnFailure(Character character, RuntimeException failure) {
        if (character != null) {
            this._player.sendSystemMessage(Component.translatable(
                    "message.player2npc.companion.relocate_failed", character.shortName()));
        }
        LOGGER.warn("Failed to establish canonical companion for owner={} characterId={}",
                this._player.getUUID(), stableCharacterId(character), failure);
    }

    /**
     * Act on a resolved, alive companion: same dimension -&gt; teleport in place; different dimension -&gt;
     * relocate via dismiss+respawn (MC {@code moveTo} cannot cross levels). Shared by the direct
     * {@link #ensureCompanionExists} path and the deferred {@link #processReloadRetries} path so both treat
     * a reunion identically (and neither ever clones a still-alive companion).
     */
    private AutomatoneEntity teleportOrRelocate(Character character, Located located) {
        if (located == null || !located.entity().isAlive() || located.entity().isRemoved()) {
            return null;
        }
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
            return automatone;
        } else {
            LOGGER.info("ensureCompanionExists RELOCATE");
            // Different dimension: MC moveTo cannot cross levels. Reuse the proven dismiss+respawn
            // path (the exact mechanism run on every relog) to bring the companion to the player's
            // current dimension, preserving inventory through an acknowledged in-memory transfer. The
            // companion UUID changes — already normal on relog.
            return this.relocateAcrossDimension(character, automatone);
        }
    }

    /**
     * Bring a still-alive companion currently loaded in a DIFFERENT dimension to the owner's current
     * dimension. MC {@code moveTo} cannot move an entity between levels, so we reuse the proven
     * dismiss+respawn mechanism, but transactionally: capture authoritative inventory state, construct,
     * synchronously apply it, and verify the replacement in the owner's current level before cleanup.
     * A rejected replacement therefore leaves the original canonical alive and recoverable.
     */
    private AutomatoneEntity relocateAcrossDimension(Character character, AutomatoneEntity oldCompanion) {
        // Reattach first so both persistence and the replacement use the same owner+character path even
        // when this entity was loaded while its owner was offline.
        oldCompanion.reattachOwner(this._player);
        PersistentDataManager.InventoryStateReceipt inventoryState =
                PersistentDataManager.captureInventoryStateNow(oldCompanion);
        boolean interruptedTask = this.hasActiveWork(oldCompanion);
        if (!inventoryState.ready() || inventoryState.state() == null) {
            RuntimeException missingState =
                    new IllegalStateException("Canonical relocation inventory state was unavailable");
            this.reportCompanionSpawnFailure(character, missingState);
            if (oldCompanion.controller != null) {
                AiConversationFeedback.deferInfo(oldCompanion.controller,
                        "You could not relocate because your inventory state could not be transferred."
                                + " You remained in your original dimension.");
            }
            return null;
        }
        if (!PersistentDataManager.saveInventoryNowWithReceipt(oldCompanion)) {
            LOGGER.warn("Relocation inventory could not be persisted synchronously for owner={} characterId={};"
                            + " using acknowledged in-memory transfer",
                    this._player.getUUID(), stableCharacterId(character));
        }
        AutomatoneEntity replacement;
        try {
            replacement = this.spawnCompanionVerified(
                    character,
                    SpawnReason.DIMENSION_RELOCATE,
                    null,
                    inventoryState.state()).entity();
        } catch (RuntimeException spawnFailure) {
            this.reportCompanionSpawnFailure(character, spawnFailure);
            if (oldCompanion.controller != null) {
                AiConversationFeedback.deferInfo(oldCompanion.controller,
                        "You could not relocate to your owner, so you remained in your original dimension."
                                + " The owner can try summoning you again.");
            }
            return null;
        }
        if (oldCompanion.controller != null) {
            oldCompanion.controller.stopWithRespawnNotification(this._player);
            oldCompanion.controller.unregisterFromGlobalRegistry();
        }
        ConversationManager.despwnCompanion(oldCompanion.getUUID());
        CompanionLocationTracker.clear(oldCompanion.getUUID());
        oldCompanion.discard();
        if (interruptedTask && replacement.controller != null) {
            AiConversationFeedback.deferInfo(replacement.controller,
                    "You were relocated across dimensions while a task was running. That task was cancelled"
                            + " and did not complete.");
        }
        writeToNbt();
        System.out.println("Relocated companion across dimension: " + character.name() + " for player " + this._player.getName().getString());
        return replacement;
    }

    public void spawnCompanion(Character character){
        spawnCompanionVerified(character, SpawnReason.RETURNING, null, null);
    }

    public void spawnCompanion(Character character, SpawnReason reason, String deathCause){
        spawnCompanionVerified(character, reason, deathCause, null);
    }

    private SpawnedCompanion spawnCompanionVerified(
            Character character,
            SpawnReason reason,
            String deathCause,
            CompoundTag authoritativeState){
        BlockPos spawnPos = this._player.blockPosition().offset(this._player.getRandom().nextInt(3) - 1, 1, this._player.getRandom().nextInt(3) - 1);
        AutomatoneEntity newCompanion = new AutomatoneEntity(
                this._player.level(),
                character,
                this._player,
                reason,
                deathCause,
                true);
        PersistentDataManager.InventoryStateReceipt stateReceipt = authoritativeState != null
                ? new PersistentDataManager.InventoryStateReceipt(true, authoritativeState)
                : PersistentDataManager.loadInventoryStateNow(newCompanion);
        boolean stateReady = stateReceipt.ready() && stateReceipt.state() != null
                && PersistentDataManager.applyInventoryStateNow(
                        newCompanion, stateReceipt.state());
        if (!stateReady) {
            this.teardownRejectedSpawn(newCompanion);
            throw new IllegalStateException(
                    "Persisted companion state was unavailable or rejected");
        }
        newCompanion.moveTo((double) spawnPos.getX() + (double) 0.5F, (double) spawnPos.getY(), (double) spawnPos.getZ() + (double) 0.5F, this._player.getYRot(), 0.0F);
        boolean added;
        try {
            added = this._player.level().addFreshEntity(newCompanion);
        } catch (RuntimeException addFailure) {
            this.teardownRejectedSpawn(newCompanion);
            throw addFailure;
        }
        if (!added) {
            this.teardownRejectedSpawn(newCompanion);
            throw new IllegalStateException("Server rejected companion entity spawn");
        }
        this._companionMap.put(character.name(), newCompanion.getUUID());
        try {
            newCompanion.finishManagedSpawnInitialization(reason, deathCause, this._player);
        } catch (RuntimeException lifecycleMessageFailure) {
            // World + full-state receipts are already committed. A greeting failure is a bounded
            // communication degradation, not permission to tear down or duplicate the canonical.
            LOGGER.warn("Canonical companion lifecycle message failed owner={} characterId={}",
                    this._player.getUUID(), stableCharacterId(character), lifecycleMessageFailure);
            this.reportPostCommitLifecycleDegradation(newCompanion, character);
        }
        CompanionIdentityReconciliationPolicy.CanonicalReceipt receipt =
                CompanionIdentityReconciliationPolicy.CanonicalReceipt.spawned(
                        true, true, stateReady);
        return new SpawnedCompanion(newCompanion, receipt);
    }

    /** Post-commit feedback is best-effort and must never invalidate an accepted canonical entity. */
    private void reportPostCommitLifecycleDegradation(
            AutomatoneEntity companion, Character character) {
        try {
            this._player.sendSystemMessage(Component.translatable(
                    "message.player2npc.companion.lifecycle_message_degraded",
                    character.shortName()));
        } catch (RuntimeException playerFeedbackFailure) {
            LOGGER.warn("Could not report lifecycle-message degradation to player owner={} characterId={}",
                    this._player.getUUID(), stableCharacterId(character), playerFeedbackFailure);
        }
        if (companion.controller != null) {
            try {
                AiConversationFeedback.deferInfo(companion.controller,
                        "Your persisted state was restored and you are active, but the initial lifecycle"
                                + " message could not be delivered.");
            } catch (RuntimeException modelFeedbackFailure) {
                LOGGER.warn("Could not defer lifecycle-message degradation owner={} characterId={}",
                        this._player.getUUID(), stableCharacterId(character), modelFeedbackFailure);
            }
        }
    }

    private void teardownRejectedSpawn(AutomatoneEntity rejected) {
        if (rejected.controller != null) {
            rejected.controller.stop();
            rejected.controller.unregisterFromGlobalRegistry();
        }
        ConversationManager.despwnCompanion(rejected.getUUID());
        CompanionLocationTracker.clear(rejected.getUUID());
        rejected.discard();
    }

    public void dismissCompanion(String characterName) {
        this.dismissCompanion(characterName, true, null);
    }

    public void dismissCompanion(Character character) {
        if (character != null) {
            this.dismissCompanion(character.name(), true, character, true);
        }
    }

    private boolean dismissCompanion(
            String characterName,
            boolean manualSummonRequired,
            Character requestedCharacter) {
        return this.dismissCompanion(
                characterName, manualSummonRequired, requestedCharacter, false);
    }

    private boolean dismissCompanion(
            String characterName,
            boolean manualSummonRequired,
            Character requestedCharacter,
            boolean explicitRequest) {
        this._pendingReloads.remove(characterName);
        UUID companionUuid = this._companionMap.get(characterName);
        if (this._player.getServer() == null) {
            return false;
        }
        if (companionUuid == null) {
            String requestedId = stableCharacterId(requestedCharacter);
            boolean exactRequestedCharacter = requestedCharacter != null
                    && characterName != null
                    && characterName.equals(requestedCharacter.name())
                    && requestedId != null;
            CompoundTag exactSnapshot = exactRequestedCharacter
                    ? this.exactSnapshotState(requestedCharacter) : null;
            boolean exactAuthoritativeSnapshot = exactSnapshot != null
                    && this.hasExactDespawnedSnapshot(requestedCharacter)
                    && PersistentDataManager.isFullStateForIdentity(
                            exactSnapshot, this._player.getUUID(), requestedId);
            if (!ManualDismissBarrierPolicy.canCommitMaplessDismiss(
                    explicitRequest, exactRequestedCharacter, exactAuthoritativeSnapshot)) {
                return false;
            }

            // The exact snapshot is already the durable world/inventory receipt. Making that receipt
            // manual-only records stable owner+character intent without inventing an entity UUID.
            exactSnapshot.putBoolean(KEY_MANUAL_SUMMON_REQUIRED, true);
            this._despawnedCompanionData.put(characterName, exactSnapshot);
            this._pendingManualDismissals.remove(characterName);
            writeToNbt();
            return true;
        }

        Located located = this.resolveLoadedCompanion(companionUuid);
        if (located == null && manualSummonRequired && requestedCharacter != null
                && characterName.equals(requestedCharacter.name())
                && stableCharacterId(requestedCharacter) != null) {
            located = this.resolveViaLocationHint(companionUuid);
        }
        if (located == null) {
            // Persist the operator's intent without deleting an entity whose live receipt is merely
            // unloaded. Automatic join restore is blocked while a bounded reload/cleanup attempt runs.
            if (manualSummonRequired && requestedCharacter != null
                    && characterName.equals(requestedCharacter.name())
                    && stableCharacterId(requestedCharacter) != null) {
                this.storeManualDismissBarrier(requestedCharacter, companionUuid);
                this.queuePendingManualDismiss(requestedCharacter, companionUuid);
            }
            return false;
        }

        AutomatoneEntity automatone = located.entity();
        if (!companionUuid.equals(automatone.getUUID())
                || (requestedCharacter != null
                        && !this.isExactStableIdentity(automatone, requestedCharacter))) {
            if (manualSummonRequired && requestedCharacter != null) {
                this.storeManualDismissBarrier(requestedCharacter, companionUuid);
                this.queuePendingManualDismiss(requestedCharacter, companionUuid);
            }
            return false;
        }

        UUID inheritedManualBarrier = this.manualDismissBarrier(automatone.character);
        boolean effectiveManualSummonRequired = manualSummonRequired
                || companionUuid.equals(inheritedManualBarrier);
        Character effectiveRequestedCharacter = requestedCharacter != null
                ? requestedCharacter
                : effectiveManualSummonRequired ? automatone.character : null;

        PersistentDataManager.InventoryStateReceipt state =
                PersistentDataManager.captureInventoryStateNow(automatone);
        if (!state.ready() || state.state() == null) {
            this._player.sendSystemMessage(Component.translatable(
                    "message.player2npc.companion.relocate_failed", automatone.character.shortName()));
            if (effectiveManualSummonRequired && effectiveRequestedCharacter != null) {
                this.storeManualDismissBarrier(effectiveRequestedCharacter, companionUuid);
                this.queuePendingManualDismiss(effectiveRequestedCharacter, companionUuid);
            }
            return false;
        }
        boolean persisted = PersistentDataManager.saveInventoryNowWithReceipt(automatone);
        boolean interruptedTask = this.hasActiveWork(automatone);
        String lifecycleNote = effectiveManualSummonRequired
                ? (interruptedTask
                        ? OPERATOR_DISMISS_TASK_MODEL_NOTE : OPERATOR_DISMISS_MODEL_NOTE)
                : (interruptedTask
                        ? SESSION_UNLOAD_TASK_MODEL_NOTE : SESSION_UNLOAD_MODEL_NOTE);
        if (!persisted) {
            lifecycleNote += " The separate inventory backup could not be updated; restoration used"
                    + " the exact dismissal snapshot.";
            this._player.sendSystemMessage(Component.translatable(
                    "message.player2npc.companion.dismiss_persistence_degraded",
                    automatone.character.shortName()));
        }
        CompoundTag savedState = PersistentDataManager.withLifecycleNote(
                state.state(), lifecycleNote);
        if (effectiveManualSummonRequired) {
            savedState.putBoolean(KEY_MANUAL_SUMMON_REQUIRED, true);
        }
        this._despawnedCompanionData.put(characterName, savedState);

        if (automatone.controller != null) {
            if (interruptedTask) {
                this._player.sendSystemMessage(Component.translatable(
                        "message.player2npc.companion.despawned_with_task",
                        automatone.character.shortName()));
            }
            automatone.controller.stop();
            automatone.controller.unregisterFromGlobalRegistry();
        }
        ConversationManager.despwnCompanion(automatone.getUUID());
        this._companionMap.remove(characterName, companionUuid);
        automatone.discard();
        if (effectiveRequestedCharacter != null) {
            this.clearManualDismissBarrier(effectiveRequestedCharacter, companionUuid);
        }
        this._pendingManualDismissals.remove(characterName);
        System.out.println("Dismissed companion: " + characterName
                + " for player " + this._player.getName().getString());
        writeToNbt();
        return true;
    }

    public void dismissAllCompanions() {
        // Each dismissCompanion already maintains _companionMap on its own: it removes the mapping in the
        // branch where the entity was positively found, and deliberately FAIL-OPENs (leaves the mapping
        // intact) for a merely-unloaded companion it could not confirm. An unconditional _companionMap.clear()
        // here would wipe those fail-open-preserved mappings, re-introducing the under-count / orphan the
        // revised dismissCompanion exists to prevent — so it is intentionally omitted.
        List<String> names = new ArrayList(this._companionMap.keySet());
        names.forEach(name -> this.dismissCompanion(name, false, null));
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

    /**
     * The owner's loaded companion entities (alive, resolved across all levels). Returns the actual
     * {@link AutomatoneEntity} objects, so by nature it can only ever include companions whose chunk is
     * currently loaded — an alive-but-unloaded companion has no entity object to return and is omitted.
     *
     * <p>Do NOT use {@code getActiveCompanions().size()} for spawn-cap or liveness decisions: it
     * undercounts a live-but-unloaded companion (the bug fixed on 2026-07-03). Use
     * {@link #getLiveCompanionCount()} for any count/cap check. This method is kept only for callers that
     * need the real loaded entity objects.
     */
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

    /**
     * Owner-scoped count of this player's companions that are currently LIVE for spawn-cap purposes.
     * Unlike {@link #getActiveCompanions()} (which enumerates only entities loaded in some level via
     * {@link ServerLevel#getEntity}), this is hint-aware: a canonical companion counts as live if it is
     * EITHER loaded-and-alive in any dimension OR has a live {@link CompanionLocationTracker} hint — the
     * exact "alive-but-unloaded, will relocate not clone" signal {@link #classifySummon} already treats as
     * TELEPORT_ALIVE (the hint is cleared only at terminal removal, never on a mere chunk unload). This
     * closes the follow-up noted in bugfixing-logs/2026-07-03-companion-cross-dimension-clone.md, where a
     * companion temporarily unloaded in another dimension was undercounted, letting a different character be
     * summoned on top of it past the configured cap.
     *
     * <p>Iterates {@code this._companionMap} once per character NAME, so a companion that is simultaneously
     * loaded AND hinted contributes exactly 1 (single OR, never a sum). Owner-scoped by construction — only
     * this manager's map is consulted, never another owner's companions.
     */
    public int getLiveCompanionCount() {
        if (this._player.getServer() == null) {
            return 0;
        }
        int count = 0;
        for (UUID uuid : this._companionMap.values()) {
            Located located = this.resolveLoadedCompanion(uuid);
            boolean live = (located != null && located.entity().isAlive())
                    || CompanionLocationTracker.get(uuid) != null;
            if (live) {
                count++;
            }
        }
        return count;
    }

    public void serverTick() {
        if (this._needsToSummon && !this._assignedCharacters.isEmpty()) {
            this.summonCompanions();
            this._needsToSummon = false;
        }
        if (!this._pendingManualDismissals.isEmpty()) {
            this.processPendingManualDismissals();
        }
        if (!this._pendingReloads.isEmpty()) {
            this.processReloadRetries();
        }
        if (!this._pendingIdentityRescans.isEmpty()) {
            this.processIdentityRescans();
        }
    }

    /**
     * Drives the deferred reunion for a hinted-but-unloaded companion whose async chunk+entity reload was
     * kicked off by {@link #ensureCompanionExists}. Each tick: if the entity has finished loading, relocate/TP
     * it; if the hint proves stale after {@link #MAX_RELOAD_RETRY_TICKS} ticks, clear it. Both terminal paths
     * enter {@link #reconcileCompanion} so a loaded stable-identity candidate or exact snapshot can never be
     * bypassed by a direct replacement spawn.
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
                ReconciliationOutcome outcome =
                        this.reconcileCompanion(pending.character, uuid, located);
                this.completeExplicitBarrierRestore(pending.character, outcome.canonical());
                this.deliverPendingLifecycleNote(
                        pending,
                        outcome.canonical() != null ? outcome.canonical() : located.entity());
                continue;
            }
            if (--pending.ticksLeft <= 0) {
                done.add(name);
                CompanionLocationTracker.clear(uuid);
                LOGGER.info("processReloadRetries: hint stale for {}; reconciling live identity receipts", name);
                ReconciliationOutcome outcome =
                        this.reconcileCompanion(pending.character, uuid, null);
                this.completeExplicitBarrierRestore(pending.character, outcome.canonical());
                this.deliverPendingLifecycleNote(pending, outcome.canonical());
            }
        }
        done.forEach(this._pendingReloads::remove);
    }

    private void processPendingManualDismissals() {
        List<String> done = new ArrayList<>();
        for (Map.Entry<String, PendingManualDismiss> entry
                : this._pendingManualDismissals.entrySet()) {
            String name = entry.getKey();
            PendingManualDismiss pending = entry.getValue();
            UUID barrier = this.manualDismissBarrier(pending.character);
            if (barrier == null) {
                done.add(name);
                continue;
            }
            if (!pending.forceLoadPhase && --pending.ticksLeft > 0) {
                continue;
            }
            if (!pending.forceLoadPhase) {
                pending.ticksLeft = PASSIVE_MANUAL_DISMISS_POLL_TICKS;
            }
            UUID currentMapped = this._companionMap.get(name);
            UUID cleanupTarget = currentMapped != null ? currentMapped : pending.mappedUuid;
            Located located = this.resolveLoadedCompanion(cleanupTarget);
            if (located == null && pending.forceLoadPhase) {
                located = this.resolveViaLocationHint(cleanupTarget);
            }
            if (located != null && located.entity().isAlive()
                    && this.dismissCompanion(name, true, pending.character)) {
                done.add(name);
                continue;
            }
            if (pending.forceLoadPhase && --pending.ticksLeft <= 0) {
                pending.forceLoadPhase = false;
                pending.ticksLeft = PASSIVE_MANUAL_DISMISS_POLL_TICKS;
                // Passive monitoring never force-loads. It cheaply catches a later natural chunk load
                // while the sticky barrier continues blocking automatic restore.
                LOGGER.info("Manual dismiss force-load phase ended; retaining passive monitor character={}",
                        name);
            }
        }
        done.forEach(this._pendingManualDismissals::remove);
    }

    private void completeExplicitBarrierRestore(
            Character character, AutomatoneEntity canonical) {
        if (character == null || character.name() == null || canonical == null
                || !this._explicitBarrierRestorePending.remove(character.name())) {
            return;
        }
        UUID barrier = this.manualDismissBarrier(character);
        if (barrier != null) {
            this.clearManualDismissBarrier(character, barrier);
        }
    }

    private void deliverPendingLifecycleNote(PendingReload pending, AutomatoneEntity canonical) {
        if (pending != null && pending.deferredLifecycleNote != null
                && canonical != null && canonical.isAlive() && !canonical.isRemoved()
                && canonical.controller != null) {
            AiConversationFeedback.deferInfo(
                    canonical.controller, pending.deferredLifecycleNote);
        }
    }

    private void scheduleIdentityRescan(Character character) {
        String characterId = stableCharacterId(character);
        if (characterId == null) {
            return;
        }
        // A successful reconciliation starts a fresh bounded observation window. Reusing an older,
        // nearly-expired window could miss a clone whose chunk begins loading after this summon.
        this._pendingIdentityRescans.put(
                characterId,
                new PendingIdentityRescan(
                        character,
                        IDENTITY_RESCAN_INTERVAL_TICKS,
                        MAX_IDENTITY_RESCAN_ATTEMPTS));
    }

    private void processIdentityRescans() {
        List<String> done = new ArrayList<>();
        for (Map.Entry<String, PendingIdentityRescan> entry : this._pendingIdentityRescans.entrySet()) {
            PendingIdentityRescan pending = entry.getValue();
            if (--pending.ticksLeft > 0) {
                continue;
            }
            this.cleanupDelayedIdentityExtras(pending.character);
            if (--pending.attemptsLeft <= 0) {
                done.add(entry.getKey());
            } else {
                pending.ticksLeft = IDENTITY_RESCAN_INTERVAL_TICKS;
            }
        }
        done.forEach(this._pendingIdentityRescans::remove);
    }

    /**
     * Cleanup-only delayed pass. It acts only when the currently mapped entity is loaded and itself
     * validates against exact owner+character identity; otherwise it fails closed and spends no receipt.
     */
    private void cleanupDelayedIdentityExtras(Character character) {
        if (character == null || character.name() == null) {
            return;
        }
        UUID mappedUuid = this._companionMap.get(character.name());
        Located mapped = this.resolveLoadedCompanion(mappedUuid);
        if (mapped == null || !mapped.entity().isAlive() || mapped.entity().isRemoved()) {
            return;
        }
        List<Located> exactMatches = this.resolveLoadedCompanionsByIdentity(character);
        if (findLocated(exactMatches, mappedUuid) == null) {
            return;
        }
        boolean cancelledDuplicateTask = false;
        boolean changed = false;
        for (Located match : exactMatches) {
            AutomatoneEntity candidate = match.entity();
            if (!mappedUuid.equals(candidate.getUUID()) && candidate.isAlive() && !candidate.isRemoved()) {
                cancelledDuplicateTask |= this.discardLoadedIdentityOrphan(candidate);
                changed = true;
            }
        }
        if (changed) {
            writeToNbt();
        }
        if (cancelledDuplicateTask) {
            this.reportDuplicateTaskCancellation(
                    character,
                    mapped.entity().controller,
                    "A duplicate companion instance loaded after identity recovery. Its unfinished task was"
                            + " cancelled and the duplicate was removed.");
        }
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
        this.reconcileOfflineDeathTombstones();
    }

    /**
     * Imports offline death receipts only when their dying UUID is still the owner's mapped
     * authority. Non-matching tombstones remain untouched for diagnosis/recovery and can never
     * overwrite a newer canonical companion.
     */
    private void reconcileOfflineDeathTombstones() {
        MinecraftServer server = this._player.getServer();
        if (server == null) {
            return;
        }
        for (OfflineDeathTombstoneStorage.Tombstone tombstone
                : OfflineDeathTombstoneStorage.loadForOwner(server, this._player.getUUID())) {
            Character character = tombstone.character();
            String characterName = tombstone.stableCharacterName();
            String characterId = tombstone.stableCharacterId();
            UUID dyingUuid = tombstone.dyingUuid();
            CompoundTag tombstoneState = tombstone.state();
            if (character == null || characterName == null || characterId == null
                    || !characterName.equals(character.name())
                    || !characterId.equals(stableCharacterId(character))
                    || !PersistentDataManager.isFullStateForIdentity(
                            tombstoneState, this._player.getUUID(), characterId)
                    || !dyingUuid.equals(this._companionMap.get(characterName))) {
                continue;
            }
            boolean exactManualDismissBarrier =
                    dyingUuid.equals(this.manualDismissBarrier(character));

            boolean banned = PermadeathBanStorage.isBanned(
                    server, this._player.getUUID(), characterId);
            PermadeathBanStorage.TerminalBanReceipt banReceipt = null;
            if (tombstone.permadeathKill() && tombstone.botPermadeath()) {
                banReceipt = PermadeathBanStorage.recordTerminalBan(
                        server, this._player.getUUID(), characterId);
                banned = banReceipt.terminalDenied();
            }
            ManualDismissBarrierPolicy.DeathDecision manualDismissDeath =
                    ManualDismissBarrierPolicy.afterDeath(exactManualDismissBarrier, banned);

            if (banned) {
                this._companionMap.remove(characterName, dyingUuid);
                this._despawnedCompanionData.remove(characterName);
                this._pendingReloads.remove(characterName);
                this._pendingIdentityRescans.remove(characterId);
                for (Located exact : this.resolveLoadedCompanionsByIdentity(character)) {
                    AutomatoneEntity duplicate = exact.entity();
                    if (duplicate.isAlive() && !duplicate.isRemoved()) {
                        this.discardLoadedIdentityOrphan(duplicate);
                    }
                }
                writeToNbt();
                OfflineDeathTombstoneStorage.delete(server, tombstone);
                if (manualDismissDeath.clearBarrier()) {
                    this.clearManualDismissBarrier(character, dyingUuid);
                }
                this._player.sendSystemMessage(Component.translatable(
                        "message.player2npc.companion.died_permadeath", character.shortName()));
                if (banReceipt != null && !banReceipt.persisted()) {
                    this._player.sendSystemMessage(Component.translatable(
                            "message.player2npc.companion.permadeath_persistence_degraded",
                            character.shortName()));
                }
                continue;
            }

            CompoundTag snapshot = PersistentDataManager.withLifecycleNote(
                    tombstoneState, tombstone.lifecycleModelNote());
            snapshot.putBoolean(KEY_DEATH_RESTORE, true);
            if (!tombstone.deathCause().isBlank()) {
                snapshot.putString(KEY_DEATH_CAUSE, tombstone.deathCause());
            }
            snapshot.putUUID(KEY_TOMBSTONE_UUID, dyingUuid);
            if (!tombstone.autoRespawn() || manualDismissDeath.requireManualRestore()) {
                snapshot.putBoolean(KEY_MANUAL_SUMMON_REQUIRED, true);
            }
            this._despawnedCompanionData.put(characterName, snapshot);
            this._companionMap.remove(characterName, dyingUuid);
            this._pendingReloads.remove(characterName);
            this._pendingIdentityRescans.remove(characterId);
            writeToNbt();
            this._player.sendSystemMessage(Component.translatable(
                    manualDismissDeath.requireManualRestore()
                            ? "message.player2npc.companion.died_dismiss_pending"
                            : tombstone.autoRespawn()
                            ? "message.player2npc.companion.died"
                            : "message.player2npc.companion.died_no_respawn",
                    character.shortName()));
            // Non-terminal tombstones are consumed only after a future accepted world+state receipt
            // and successful lifecycle-note transfer; executeReconciliation owns that exact receipt.
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
