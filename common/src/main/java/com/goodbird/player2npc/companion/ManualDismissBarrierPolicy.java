package com.goodbird.player2npc.companion;

/** Pure policy for preserving stable manual-dismiss intent across companion UUID changes and death. */
final class ManualDismissBarrierPolicy {
    record DeathDecision(boolean requireManualRestore, boolean clearBarrier) {
    }

    private ManualDismissBarrierPolicy() {
    }

    static DeathDecision afterDeath(boolean matchingBarrier, boolean terminal) {
        if (!matchingBarrier) {
            return new DeathDecision(false, false);
        }
        return terminal
                ? new DeathDecision(false, true)
                : new DeathDecision(true, false);
    }

    /**
     * A mapless explicit dismiss may only make an existing snapshot sticky. It must never manufacture
     * state or treat a same-name/non-authoritative snapshot as the requested companion's receipt.
     */
    static boolean canCommitMaplessDismiss(
            boolean explicitRequest,
            boolean exactRequestedCharacter,
            boolean exactAuthoritativeSnapshot) {
        return explicitRequest && exactRequestedCharacter && exactAuthoritativeSnapshot;
    }
}
