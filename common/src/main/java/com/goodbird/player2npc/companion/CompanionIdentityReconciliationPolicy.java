package com.goodbird.player2npc.companion;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pure selection/commit policy for reconciling canonical and duplicate loaded companions. */
final class CompanionIdentityReconciliationPolicy {
    enum Action {
        KEEP_MAPPED,
        ADOPT_LOADED,
        SPAWN_FRESH
    }

    /**
     * Pure world-independent decision. Destructive work is deliberately deferred until
     * {@link #complete(Decision, CanonicalReceipt)} receives a verified canonical receipt.
     */
    record Decision(
            Action action,
            UUID canonicalUuid,
            List<UUID> discardAfterSuccess,
            boolean consumeExactSnapshot) {
        Decision {
            Objects.requireNonNull(action, "action");
            discardAfterSuccess = discardAfterSuccess == null
                    ? List.of()
                    : List.copyOf(discardAfterSuccess);
        }
    }

    /** Exact postcondition receipt: failure never authorizes snapshot consumption or orphan discard. */
    record Completion(
            boolean canonicalReady,
            boolean consumeExactSnapshot,
            List<UUID> discardNow) {
        Completion {
            discardNow = discardNow == null ? List.of() : List.copyOf(discardNow);
        }
    }

    /**
     * A canonical receipt separates the world-add result from authoritative state initialization.
     * Snapshot/death/relocation commits require both; an ordinary fresh spawn has no authoritative
     * state requirement and therefore needs only the entity receipt.
     */
    record CanonicalReceipt(
            boolean entityAvailable,
            boolean authoritativeStateRequired,
            boolean authoritativeStateReady) {
        boolean ready() {
            return entityAvailable && (!authoritativeStateRequired || authoritativeStateReady);
        }

        static CanonicalReceipt existing() {
            return new CanonicalReceipt(true, false, true);
        }

        static CanonicalReceipt spawned(
                boolean entityAdded,
                boolean authoritativeStateRequired,
                boolean authoritativeStateReady) {
            return new CanonicalReceipt(
                    entityAdded,
                    authoritativeStateRequired,
                    authoritativeStateReady);
        }

        static CanonicalReceipt missing() {
            return new CanonicalReceipt(false, false, false);
        }
    }

    record Candidate(
            UUID entityUuid,
            UUID ownerUuid,
            String characterId,
            boolean alive,
            boolean removed,
            boolean sameDimension,
            double distanceSquared) {
        Candidate {
            Objects.requireNonNull(entityUuid, "entityUuid");
        }
    }

    private CompanionIdentityReconciliationPolicy() {
    }

    static List<UUID> select(
            UUID requestedOwnerUuid,
            String requestedCharacterId,
            List<Candidate> candidates) {
        if (requestedOwnerUuid == null
                || requestedCharacterId == null
                || requestedCharacterId.isBlank()
                || candidates == null
                || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .filter(candidate -> matches(requestedOwnerUuid, requestedCharacterId, candidate))
                .sorted(Comparator
                        .comparing(Candidate::sameDimension).reversed()
                        .thenComparingDouble(candidate -> finiteDistance(candidate.distanceSquared()))
                        .thenComparing(candidate -> candidate.entityUuid().toString()))
                .map(Candidate::entityUuid)
                .toList();
    }

    static Decision decide(
            UUID mappedUuid,
            boolean mappedLoadedExact,
            boolean exactSnapshot,
            List<UUID> sortedExactMatches) {
        List<UUID> exact = sortedExactMatches == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(sortedExactMatches));
        if (mappedLoadedExact && mappedUuid != null && exact.contains(mappedUuid)) {
            return new Decision(
                    Action.KEEP_MAPPED,
                    mappedUuid,
                    exact.stream().filter(uuid -> !mappedUuid.equals(uuid)).toList(),
                    exactSnapshot);
        }
        if (exactSnapshot) {
            return new Decision(Action.SPAWN_FRESH, null, exact, true);
        }
        if (!exact.isEmpty()) {
            UUID adopted = exact.get(0);
            return new Decision(
                    Action.ADOPT_LOADED,
                    adopted,
                    exact.stream().filter(uuid -> !adopted.equals(uuid)).toList(),
                    false);
        }
        return new Decision(Action.SPAWN_FRESH, null, List.of(), false);
    }

    /**
     * Death-specific precedence. A mapped, exact dying entity owns its post-death inventory semantics,
     * so it is replaced from that state and any historical live copies are retired only after success.
     * A noncanonical dying duplicate has no such authority and falls through to the normal live/snapshot
     * reconciliation rules.
     */
    static Decision decideAfterDeath(
            UUID mappedUuid,
            UUID dyingUuid,
            boolean dyingIdentityExact,
            boolean mappedLoadedExact,
            boolean exactSnapshot,
            List<UUID> sortedExactMatches) {
        List<UUID> exact = sortedExactMatches == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(sortedExactMatches));
        if (dyingIdentityExact && mappedUuid != null && mappedUuid.equals(dyingUuid)) {
            return new Decision(Action.SPAWN_FRESH, null, exact, exactSnapshot);
        }
        return decide(mappedUuid, mappedLoadedExact, exactSnapshot, exact);
    }

    static boolean mayUseDyingState(
            UUID mappedUuid,
            UUID dyingUuid,
            boolean dyingIdentityExact,
            Decision decision) {
        return decision != null
                && decision.action() == Action.SPAWN_FRESH
                && dyingIdentityExact
                && mappedUuid != null
                && mappedUuid.equals(dyingUuid);
    }

    static boolean deathSpawnAuthorized(Decision decision, boolean authoritativeStateReady) {
        return decision != null
                && (decision.action() != Action.SPAWN_FRESH || authoritativeStateReady);
    }

    static Completion complete(Decision decision, CanonicalReceipt receipt) {
        Objects.requireNonNull(decision, "decision");
        if (receipt == null || !receipt.ready()) {
            return new Completion(false, false, List.of());
        }
        return new Completion(
                true,
                decision.consumeExactSnapshot(),
                decision.discardAfterSuccess());
    }

    static boolean snapshotIdentityMatches(
            UUID requestedOwnerUuid,
            String requestedCharacterId,
            UUID snapshotOwnerUuid,
            String snapshotCharacterId) {
        return requestedOwnerUuid != null
                && requestedOwnerUuid.equals(snapshotOwnerUuid)
                && requestedCharacterId != null
                && !requestedCharacterId.isBlank()
                && requestedCharacterId.equals(snapshotCharacterId);
    }

    static boolean matches(
            UUID requestedOwnerUuid,
            String requestedCharacterId,
            Candidate candidate) {
        return candidate != null
                && candidate.alive()
                && !candidate.removed()
                && requestedOwnerUuid != null
                && requestedOwnerUuid.equals(candidate.ownerUuid())
                && requestedCharacterId != null
                && !requestedCharacterId.isBlank()
                && requestedCharacterId.equals(candidate.characterId());
    }

    static UUID ownerUuidForStorage(UUID attachedOwnerUuid, UUID cachedOwnerUuid) {
        return attachedOwnerUuid != null ? attachedOwnerUuid : cachedOwnerUuid;
    }

    static boolean shouldPersistInventoryForRemoval(boolean killed, boolean discarded) {
        return !killed && !discarded;
    }

    private static double finiteDistance(double value) {
        return Double.isFinite(value) && value >= 0.0D
                ? value
                : Double.POSITIVE_INFINITY;
    }
}
