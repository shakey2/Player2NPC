package com.goodbird.player2npc.companion.gui;

import java.util.Map;

public record GuiActionResult(boolean success, String reasonCode, Map<String, String> responseFields) {
    public GuiActionResult {
        reasonCode = reasonCode == null || reasonCode.isBlank() ? (success ? "ok" : "rejected") : reasonCode;
        responseFields = responseFields == null ? Map.of() : Map.copyOf(responseFields);
    }

    public static GuiActionResult ok() {
        return ok("ok");
    }

    public static GuiActionResult ok(String reasonCode) {
        return new GuiActionResult(true, reasonCode, Map.of());
    }

    public static GuiActionResult ok(String reasonCode, Map<String, String> responseFields) {
        return new GuiActionResult(true, reasonCode, responseFields);
    }

    public static GuiActionResult rejected(String reasonCode) {
        return new GuiActionResult(false, reasonCode, Map.of());
    }
}
