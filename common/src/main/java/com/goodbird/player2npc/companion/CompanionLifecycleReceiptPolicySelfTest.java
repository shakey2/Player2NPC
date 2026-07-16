package com.goodbird.player2npc.companion;

import java.util.List;
import java.util.UUID;

/** Pure lifecycle receipt/death-authority regression checks; no Minecraft runtime required. */
public final class CompanionLifecycleReceiptPolicySelfTest {
    private CompanionLifecycleReceiptPolicySelfTest() {
    }

    public static void main(String[] args) {
        UUID mapped = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID duplicate = UUID.fromString("00000000-0000-0000-0000-000000000102");
        CompanionIdentityReconciliationPolicy.Decision snapshotSpawn =
                CompanionIdentityReconciliationPolicy.decide(
                        null, false, true, List.of(mapped, duplicate));

        assertNotCommitted(snapshotSpawn,
                CompanionIdentityReconciliationPolicy.CanonicalReceipt.spawned(true, true, false),
                "world add without authoritative state must not consume a snapshot or discard alternatives");
        assertNotCommitted(snapshotSpawn,
                CompanionIdentityReconciliationPolicy.CanonicalReceipt.spawned(false, true, true),
                "state application without a world receipt must not commit");
        CompanionIdentityReconciliationPolicy.Completion committed =
                CompanionIdentityReconciliationPolicy.complete(
                        snapshotSpawn,
                        CompanionIdentityReconciliationPolicy.CanonicalReceipt.spawned(true, true, true));
        require(committed.canonicalReady()
                        && committed.consumeExactSnapshot()
                        && committed.discardNow().equals(List.of(mapped, duplicate)),
                "snapshot commit requires both authoritative state and entity receipts");

        CompanionIdentityReconciliationPolicy.Decision ordinaryFresh =
                CompanionIdentityReconciliationPolicy.decide(null, false, false, List.of());
        require(CompanionIdentityReconciliationPolicy.complete(
                        ordinaryFresh,
                        CompanionIdentityReconciliationPolicy.CanonicalReceipt.spawned(true, false, false))
                        .canonicalReady(),
                "an ordinary fresh spawn has no authoritative-state prerequisite");

        CompanionIdentityReconciliationPolicy.Decision canonicalDeath =
                CompanionIdentityReconciliationPolicy.decideAfterDeath(
                        mapped, mapped, true, false, false, List.of(duplicate));
        require(canonicalDeath.action() == CompanionIdentityReconciliationPolicy.Action.SPAWN_FRESH
                        && canonicalDeath.discardAfterSuccess().equals(List.of(duplicate)),
                "mapped exact death must replace from post-death state instead of adopting a clone");
        require(CompanionIdentityReconciliationPolicy.mayUseDyingState(
                        mapped, mapped, true, canonicalDeath),
                "only the mapped exact death may provide replacement inventory state");

        CompanionIdentityReconciliationPolicy.Decision duplicateDeath =
                CompanionIdentityReconciliationPolicy.decideAfterDeath(
                        mapped, duplicate, true, true, false, List.of(mapped));
        require(duplicateDeath.action() == CompanionIdentityReconciliationPolicy.Action.KEEP_MAPPED
                        && mapped.equals(duplicateDeath.canonicalUuid()),
                "death of a duplicate must keep the healthy mapped canonical");
        require(!CompanionIdentityReconciliationPolicy.mayUseDyingState(
                        mapped, duplicate, true, duplicateDeath),
                "a dying duplicate must never overwrite canonical inventory state");

        CompanionIdentityReconciliationPolicy.Decision orphanDeath =
                CompanionIdentityReconciliationPolicy.decideAfterDeath(
                        null, duplicate, true, false, false, List.of(mapped));
        require(orphanDeath.action() == CompanionIdentityReconciliationPolicy.Action.ADOPT_LOADED,
                "a noncanonical death must adopt a healthy exact candidate rather than clone it");

        CompanionIdentityReconciliationPolicy.Decision unacknowledgedDeathSpawn =
                CompanionIdentityReconciliationPolicy.decideAfterDeath(
                        null, duplicate, true, false, false, List.of());
        require(!CompanionIdentityReconciliationPolicy.deathSpawnAuthorized(
                        unacknowledgedDeathSpawn, false),
                "death recovery must not reload stale disk inventory without an authoritative state receipt");
        require(CompanionIdentityReconciliationPolicy.deathSpawnAuthorized(
                        unacknowledgedDeathSpawn, true),
                "an acknowledged post-death or exact-snapshot state may authorize replacement");

        UUID cachedOwner = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID attachedOwner = UUID.fromString("00000000-0000-0000-0000-000000000202");
        require(cachedOwner.equals(CompanionIdentityReconciliationPolicy.ownerUuidForStorage(
                        null, cachedOwner)),
                "offline persistence must fall back to the cached stable owner UUID");
        require(attachedOwner.equals(CompanionIdentityReconciliationPolicy.ownerUuidForStorage(
                        attachedOwner, cachedOwner)),
                "an attached owner must take precedence over cached identity");

        ManualDismissBarrierPolicy.DeathDecision pendingDismissDeath =
                ManualDismissBarrierPolicy.afterDeath(true, false);
        require(pendingDismissDeath.requireManualRestore()
                        && !pendingDismissDeath.clearBarrier(),
                "a nonterminal death must preserve pending manual-dismiss intent and suppress auto-restore");
        ManualDismissBarrierPolicy.DeathDecision terminalDismissDeath =
                ManualDismissBarrierPolicy.afterDeath(true, true);
        require(!terminalDismissDeath.requireManualRestore()
                        && terminalDismissDeath.clearBarrier(),
                "a completed terminal death may consume the matching manual-dismiss barrier");
        ManualDismissBarrierPolicy.DeathDecision unrelatedDeath =
                ManualDismissBarrierPolicy.afterDeath(false, false);
        require(!unrelatedDeath.requireManualRestore() && !unrelatedDeath.clearBarrier(),
                "death without a matching barrier must not invent manual-dismiss semantics");

        require(ManualDismissBarrierPolicy.canCommitMaplessDismiss(true, true, true),
                "mapless dismissal may make an exact authoritative snapshot manual-only");
        require(!ManualDismissBarrierPolicy.canCommitMaplessDismiss(true, false, true),
                "mapless dismissal must reject a snapshot for a nonmatching requested character");
        require(!ManualDismissBarrierPolicy.canCommitMaplessDismiss(true, true, false),
                "mapless dismissal must not invent or bless a non-authoritative snapshot");
        require(!ManualDismissBarrierPolicy.canCommitMaplessDismiss(false, true, true),
                "an internal retry must not claim mapless dismissal without removing its target entity");
    }

    private static void assertNotCommitted(
            CompanionIdentityReconciliationPolicy.Decision decision,
            CompanionIdentityReconciliationPolicy.CanonicalReceipt receipt,
            String message) {
        CompanionIdentityReconciliationPolicy.Completion completion =
                CompanionIdentityReconciliationPolicy.complete(decision, receipt);
        require(!completion.canonicalReady()
                        && !completion.consumeExactSnapshot()
                        && completion.discardNow().isEmpty(),
                message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
