package com.goodbird.player2npc.companion;

import java.util.List;
import java.util.UUID;

/** Controller-free checks for stable owner/character orphan reconciliation. */
public final class CompanionIdentityReconciliationPolicySelfTest {
    private CompanionIdentityReconciliationPolicySelfTest() {
    }

    public static void main(String[] args) {
        runAll();
    }

    public static void runAll() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID otherOwner = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID farSameDimension = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID nearSameDimension = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID otherDimension = UUID.fromString("00000000-0000-0000-0000-000000000012");

        List<CompanionIdentityReconciliationPolicy.Candidate> candidates = List.of(
                candidate(otherDimension, owner, "character-a", true, false, false, 1.0D),
                candidate(farSameDimension, owner, "character-a", true, false, true, 25.0D),
                candidate(nearSameDimension, owner, "character-a", true, false, true, 4.0D),
                candidate(UUID.fromString("00000000-0000-0000-0000-000000000030"), otherOwner, "character-a", true, false, true, 0.0D),
                candidate(UUID.fromString("00000000-0000-0000-0000-000000000031"), owner, "character-b", true, false, true, 0.0D),
                candidate(UUID.fromString("00000000-0000-0000-0000-000000000032"), owner, "character-a", false, false, true, 0.0D),
                candidate(UUID.fromString("00000000-0000-0000-0000-000000000033"), owner, "character-a", true, true, true, 0.0D));

        List<UUID> selected = CompanionIdentityReconciliationPolicy.select(
                owner, "character-a", candidates);
        require(selected.equals(List.of(nearSameDimension, farSameDimension, otherDimension)),
                "selection must require exact stable identity and prefer same-dimension nearest candidates");
        require(CompanionIdentityReconciliationPolicy.select(owner, "", candidates).isEmpty(),
                "blank character ids must never fall back to display-name identity");
        require(CompanionIdentityReconciliationPolicy.select(null, "character-a", candidates).isEmpty(),
                "missing owner identity must fail closed");
        CompanionIdentityReconciliationPolicy.Decision mappedWins =
                CompanionIdentityReconciliationPolicy.decide(
                        nearSameDimension,
                        true,
                        true,
                        List.of(nearSameDimension, farSameDimension, otherDimension));
        require(mappedWins.action() == CompanionIdentityReconciliationPolicy.Action.KEEP_MAPPED,
                "an exact live mapped companion must win over a contradictory snapshot");
        require(nearSameDimension.equals(mappedWins.canonicalUuid()),
                "the exact mapped UUID must remain canonical");
        require(mappedWins.discardAfterSuccess().equals(List.of(farSameDimension, otherDimension)),
                "all other exact identity matches must be retired after canonical success");
        require(mappedWins.consumeExactSnapshot(),
                "a mapped canonical must consume its stale exact snapshot after success");

        CompanionIdentityReconciliationPolicy.Decision adopt =
                CompanionIdentityReconciliationPolicy.decide(
                        null,
                        false,
                        false,
                        List.of(nearSameDimension, farSameDimension, otherDimension));
        require(adopt.action() == CompanionIdentityReconciliationPolicy.Action.ADOPT_LOADED,
                "without a map or snapshot the best exact live match must be adopted");
        require(nearSameDimension.equals(adopt.canonicalUuid()),
                "adoption must preserve deterministic selection order");
        require(adopt.discardAfterSuccess().equals(List.of(farSameDimension, otherDimension)),
                "adoption must retire all additional exact clones after success");

        CompanionIdentityReconciliationPolicy.Decision snapshotFresh =
                CompanionIdentityReconciliationPolicy.decide(
                        null,
                        false,
                        true,
                        List.of(nearSameDimension, farSameDimension));
        require(snapshotFresh.action() == CompanionIdentityReconciliationPolicy.Action.SPAWN_FRESH,
                "an exact snapshot with no mapped canonical must choose canonical persistence");
        require(snapshotFresh.discardAfterSuccess().equals(List.of(nearSameDimension, farSameDimension)),
                "snapshot recovery must retire every stale exact live copy only after spawn succeeds");

        CompanionIdentityReconciliationPolicy.Decision nonmatchingSnapshot =
                CompanionIdentityReconciliationPolicy.decide(
                        null,
                        false,
                        false,
                        List.of(nearSameDimension));
        require(nonmatchingSnapshot.action() == CompanionIdentityReconciliationPolicy.Action.ADOPT_LOADED,
                "a malformed or same-name/different-id snapshot must have no destructive authority");

        CompanionIdentityReconciliationPolicy.Completion rejectedSpawn =
                CompanionIdentityReconciliationPolicy.complete(
                        snapshotFresh,
                        CompanionIdentityReconciliationPolicy.CanonicalReceipt.spawned(false, true, true));
        require(!rejectedSpawn.canonicalReady()
                        && !rejectedSpawn.consumeExactSnapshot()
                        && rejectedSpawn.discardNow().isEmpty(),
                "a rejected spawn must preserve the snapshot and every recoverable live candidate");
        CompanionIdentityReconciliationPolicy.Completion acceptedSpawn =
                CompanionIdentityReconciliationPolicy.complete(
                        snapshotFresh,
                        CompanionIdentityReconciliationPolicy.CanonicalReceipt.spawned(true, true, true));
        require(acceptedSpawn.canonicalReady()
                        && acceptedSpawn.consumeExactSnapshot()
                        && acceptedSpawn.discardNow().equals(List.of(nearSameDimension, farSameDimension)),
                "only a verified canonical receipt may commit snapshot consumption and clone discard");

        require(CompanionIdentityReconciliationPolicy.snapshotIdentityMatches(
                        owner, "character-a", owner, "character-a"),
                "snapshot authority must require exact owner and stable character id");
        require(!CompanionIdentityReconciliationPolicy.snapshotIdentityMatches(
                        owner, "character-a", otherOwner, "character-a"),
                "a snapshot for another owner must fail closed");
        require(!CompanionIdentityReconciliationPolicy.snapshotIdentityMatches(
                        owner, "character-a", owner, "character-b"),
                "a same-name snapshot for another character id must fail closed");
        require(!CompanionIdentityReconciliationPolicy.snapshotIdentityMatches(
                        owner, "character-a", owner, null),
                "a malformed snapshot without stable character identity must fail closed");
        require(owner.equals(CompanionIdentityReconciliationPolicy.ownerUuidForStorage(null, owner)),
                "offline entity saves must retain the cached stable owner UUID");
        require(otherOwner.equals(CompanionIdentityReconciliationPolicy.ownerUuidForStorage(
                        otherOwner, owner)),
                "an attached live owner UUID must refresh and override the cached value");

        UUID tieA = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID tieB = UUID.fromString("00000000-0000-0000-0000-000000000021");
        List<UUID> tied = CompanionIdentityReconciliationPolicy.select(owner, "character-a", List.of(
                candidate(tieB, owner, "character-a", true, false, true, Double.NaN),
                candidate(tieA, owner, "character-a", true, false, true, Double.POSITIVE_INFINITY)));
        require(tied.equals(List.of(tieA, tieB)),
                "non-finite distance ties must resolve by UUID deterministically");
        require(!CompanionIdentityReconciliationPolicy.shouldPersistInventoryForRemoval(false, true),
                "explicit discard must not overwrite owner+character persistence");
        require(!CompanionIdentityReconciliationPolicy.shouldPersistInventoryForRemoval(true, false),
                "death owns its separate persistence path");
        require(CompanionIdentityReconciliationPolicy.shouldPersistInventoryForRemoval(false, false),
                "ordinary chunk unload must keep persistence enabled");
    }

    private static CompanionIdentityReconciliationPolicy.Candidate candidate(
            UUID entityUuid,
            UUID ownerUuid,
            String characterId,
            boolean alive,
            boolean removed,
            boolean sameDimension,
            double distanceSquared) {
        return new CompanionIdentityReconciliationPolicy.Candidate(
                entityUuid,
                ownerUuid,
                characterId,
                alive,
                removed,
                sameDimension,
                distanceSquared);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
