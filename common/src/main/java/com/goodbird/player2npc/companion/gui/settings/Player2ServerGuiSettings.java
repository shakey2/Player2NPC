package com.goodbird.player2npc.companion.gui.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Player2 server-owned settings approved for the mod settings UI. */
final class Player2ServerGuiSettings {
    private static final GuiSettingSource SOURCE = GuiSettingSource.PLAYER2_SERVER;
    private static final String BEHAVIOR = "behavior";
    private static final String BUDGET = "budget";
    private static final String AI_MEMORY = "ai_memory";
    private static final String MOD_INTELLIGENCE = "mod_intelligence";
    private static final GuiSettingEffect LIVE = GuiSettingEffect.LIVE;

    static final List<GuiSettingDefinition> ALL = List.of(
            enumerated("payerMode", BUDGET, GuiSettingSections.BILLING_ROUTING,
                    LIVE, "PROMPTER_PAYS", "OWNER_PAYS_ALL"),
            bool("ownerOfflineServerContinuation", BUDGET, GuiSettingSections.BILLING_ROUTING, LIVE),
            bool("callByNameChat", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS, LIVE),
            integer("maxSpawnedCompanionsPerPlayer", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS,
                    1, 20, false, GuiSettingZeroMeaning.NONE),
            integer("maxStoredCharacterIdsPerPlayer", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS,
                    0, 100, false, GuiSettingZeroMeaning.UNLIMITED),
            integer("chatCompletionMaxOutputTokens", BUDGET, GuiSettingSections.BILLING_ROUTING,
                    1, 100000, true, GuiSettingZeroMeaning.NONE),
            bool("serverOverridesPlayerConfig", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS, LIVE),
            enumerated("botKeepInventoryOverride", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL,
                    LIVE, "FOLLOW_GAMERULE", "FORCE_KEEP", "FORCE_DROP"),
            bool("botTtsPlaybackAckEnabled", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS, LIVE),
            bool("ragFallbackToFullList", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT, LIVE),
            bool("ragLiveEnabled", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT, LIVE),
            integer("ragMinGoalChars", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    1, 16, false, GuiSettingZeroMeaning.NONE),
            integer("deepCheckMaxAttemptsPerTurn", AI_MEMORY,
                    GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    0, 3, false, GuiSettingZeroMeaning.OFF),
            integer("deepCheckCallsPerWindow", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    0, 100, false, GuiSettingZeroMeaning.UNLIMITED),
            integer("deepCheckWindowMinutes", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    1, 1440, true, GuiSettingZeroMeaning.NONE),
            percent("deepCheckWeakBelowScore", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT),
            percent("deepCheckWeakGapRatio", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT),
            percent("deepCheckWeakTokenCoverage", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT),
            bool("forceDeepCheckOnEmpty", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT, LIVE),
            integer("memoryWindowMinutes", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    1, 1440, true, GuiSettingZeroMeaning.NONE),
            integer("memoryExtractionBatchMin", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    1, 100, false, GuiSettingZeroMeaning.NONE),
            integer("memoryExtractionBatchMax", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    1, 100, false, GuiSettingZeroMeaning.NONE),
            integer("memoryExtractionLengthThreshold", AI_MEMORY,
                    GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    1, 32768, true, GuiSettingZeroMeaning.NONE),
            integer("memoryMaxHops", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    1, 6, false, GuiSettingZeroMeaning.NONE),
            integer("memoryMaxEgoNodes", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    1, 4096, true, GuiSettingZeroMeaning.NONE),
            integer("memoryBlockCharCap", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    0, 8000, false, GuiSettingZeroMeaning.NONE),
            percent("memoryMinConfidence", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT),
            GuiSettingDefinition.permille(SOURCE, "memoryDecayBase", AI_MEMORY,
                    GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT, 1, 1000, LIVE),
            integer("memoryRetrievalTopK", AI_MEMORY, GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    1, 50, false, GuiSettingZeroMeaning.NONE),
            integer("reflectionImportanceThreshold", AI_MEMORY,
                    GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    1, 100000, true, GuiSettingZeroMeaning.NONE),
            integer("relationshipSummaryCharCap", AI_MEMORY,
                    GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
                    0, 1000, false, GuiSettingZeroMeaning.OFF),
            GuiSettingDefinition.bool(SOURCE, "modIntelligenceInspectOnLaunch", MOD_INTELLIGENCE,
                    GuiSettingSections.MOD_INTELLIGENCE, GuiSettingEffect.NEXT_LAUNCH),
            integer("modIntelligenceMaxInspectEntriesPerLaunch", MOD_INTELLIGENCE,
                    GuiSettingSections.MOD_INTELLIGENCE,
                    0, 50000, false, GuiSettingZeroMeaning.UNLIMITED),
            integer("modIntelligenceMaxEnrichmentFailuresPerLaunch", MOD_INTELLIGENCE,
                    GuiSettingSections.MOD_INTELLIGENCE,
                    1, 500, false, GuiSettingZeroMeaning.NONE),
            integer("modIntelligenceQueryTopK", MOD_INTELLIGENCE, GuiSettingSections.MOD_INTELLIGENCE,
                    1, 50, false, GuiSettingZeroMeaning.NONE),
            percent("modIntelligenceMinQueryConfidence", MOD_INTELLIGENCE,
                    GuiSettingSections.MOD_INTELLIGENCE));

    private Player2ServerGuiSettings() {
    }

    private static GuiSettingDefinition bool(
            String key, String tab, GuiSettingSection section, GuiSettingEffect effect) {
        return GuiSettingDefinition.bool(SOURCE, key, tab, section, effect);
    }

    private static GuiSettingDefinition integer(
            String key,
            String tab,
            GuiSettingSection section,
            int minimum,
            int maximum,
            boolean logarithmicHint,
            GuiSettingZeroMeaning zeroMeaning) {
        return GuiSettingDefinition.integer(SOURCE, key, tab, section, minimum, maximum, 1,
                logarithmicHint, LIVE, zeroMeaning);
    }

    private static GuiSettingDefinition percent(
            String key, String tab, GuiSettingSection section) {
        return GuiSettingDefinition.percent(SOURCE, key, tab, section, LIVE);
    }

    private static GuiSettingDefinition enumerated(
            String key,
            String tab,
            GuiSettingSection section,
            GuiSettingEffect effect,
            String... tokens) {
        List<GuiSettingOption> options = Arrays.stream(tokens)
                .map(token -> new GuiSettingOption(token, optionKey(key, token)))
                .toList();
        return GuiSettingDefinition.enumerated(SOURCE, key, tab, section, options, effect);
    }

    private static String optionKey(String key, String token) {
        return "screen.player2npc.ui.settings.option.server." + key + "."
                + token.toLowerCase(Locale.ROOT);
    }
}
