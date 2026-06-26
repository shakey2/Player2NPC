package com.goodbird.player2npc.companion;

/**
 * Why an {@link AutomatoneEntity} was just (re)spawned. Threaded from the spawn call site through
 * {@code spawnCompanion -> constructor -> init()} so {@code init()} picks exactly one model-facing
 * message (or none) instead of unconditionally greeting.
 */
public enum SpawnReason {
    /**
     * First-ever spawn intent. <b>Currently routes identically to {@link #RETURNING}</b> in
     * {@code init()} (both call {@code sendReturnMessage}), because the real first-meeting decision
     * is made one level deeper in {@code AIPersistantData.getReturnEvent}, which greets when the
     * per-world history file does not yet exist. No call site passes this value today; it is kept to
     * document caller intent and to leave room for a future cross-world ("once-ever") first-meeting
     * gate (plan Q1-global). Do not assume passing this produces a greeting-only behavior distinct
     * from RETURNING.
     */
    FIRST_MEETING,
    /** Fresh spawn that is NOT a teleport: login, resummon, restart. */
    RETURNING,
    /** Auto-respawn after death; carries a death-cause string. */
    DEATH_RESPAWN
}
