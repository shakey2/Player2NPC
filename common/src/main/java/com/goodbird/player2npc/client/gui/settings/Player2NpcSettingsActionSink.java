package com.goodbird.player2npc.client.gui.settings;

/** Receives the catalog-derived action name and its validated wire value. */
@FunctionalInterface
public interface Player2NpcSettingsActionSink {
    void send(String action, String value);
}
