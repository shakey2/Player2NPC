package com.goodbird.player2npc.companion.gui.settings;

import java.util.Objects;

/** Stable section identifier and its initial disclosure state. */
public record GuiSettingSection(String key, boolean collapsedByDefault) {
    private static final String TRANSLATION_PREFIX = "screen.player2npc.ui.settings.section.";

    public GuiSettingSection {
        key = Objects.requireNonNull(key, "key");
    }

    public String translationKey() {
        return TRANSLATION_PREFIX + key;
    }
}
