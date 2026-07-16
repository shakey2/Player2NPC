package com.goodbird.player2npc.client.gui;

import java.util.Arrays;

public enum Player2NpcTab {
    UNKNOWN(-1, "unknown", "screen.player2npc.ui.state.unsupported_tab"),
    GETTING_STARTED(0, "getting_started", "screen.player2npc.ui.tab.getting_started"),
    COMPANIONS(1, "companions", "screen.player2npc.ui.tab.companions"),
    BEHAVIOR(2, "behavior", "screen.player2npc.ui.tab.behavior"),
    BUDGET(3, "budget", "screen.player2npc.ui.tab.budget"),
    ACCESS_LISTS(4, "access_lists", "screen.player2npc.ui.tab.access_lists"),
    MOD_INTELLIGENCE(5, "mod_intelligence", "screen.player2npc.ui.tab.mod_intelligence"),
    AI_MEMORY(6, "ai_memory", "screen.player2npc.ui.tab.ai_memory"),
    PROFILES_MODELS(7, "profiles_models", "screen.player2npc.ui.tab.profiles_models"),
    AUTOMATION_ITEMS(8, "automation_items", "screen.player2npc.ui.tab.automation_items");

    private final int id;
    private final String wireName;
    private final String translationKey;

    Player2NpcTab(int id, String wireName, String translationKey) {
        this.id = id;
        this.wireName = wireName;
        this.translationKey = translationKey;
    }

    public int id() {
        return id;
    }

    public String wireName() {
        return wireName;
    }

    public String translationKey() {
        return translationKey;
    }

    public static Player2NpcTab fromId(int id) {
        return Arrays.stream(values())
                .filter(tab -> tab.id == id)
                .findFirst()
                .orElse(UNKNOWN);
    }

    public static Player2NpcTab fromWireName(String wireName) {
        return Arrays.stream(values())
                .filter(tab -> tab.wireName.equals(wireName))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public static Player2NpcTab[] visibleTabs() {
        return Arrays.stream(values())
                .filter(tab -> tab != UNKNOWN)
                .toArray(Player2NpcTab[]::new);
    }
}
