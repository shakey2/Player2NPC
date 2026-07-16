package com.goodbird.player2npc.companion.gui.settings;

import java.util.Objects;

/** Stable enum wire token paired with localized presentation. */
public record GuiSettingOption(String token, String translationKey) {
    public GuiSettingOption {
        token = Objects.requireNonNull(token, "token");
        translationKey = Objects.requireNonNull(translationKey, "translationKey");
    }
}
