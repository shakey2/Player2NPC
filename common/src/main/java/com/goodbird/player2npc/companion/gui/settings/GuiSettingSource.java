package com.goodbird.player2npc.companion.gui.settings;

/** Persisted configuration source for a setting. */
public enum GuiSettingSource {
    PLAYER_ENGINE("pe"),
    PLAYER2_SERVER("server");

    private final String prefix;

    GuiSettingSource(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
