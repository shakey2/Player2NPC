package com.goodbird.player2npc.companion.gui;

public final class GuiProtocol {
    public static final int GUI_PROTOCOL_VERSION = 4;
    public static final int MAX_FIELDS = 192;
    public static final int MAX_ITEM_FIELDS = 1024;
    public static final int MAX_KEY_LENGTH = 64;
    public static final int MAX_VALUE_LENGTH = 4096;
    public static final int MAX_ADDITIONAL_PROMPT_LENGTH = 300;

    private GuiProtocol() {
    }

    public static String clampValue(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH);
    }
}
