package com.goodbird.player2npc.companion.gui.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable, validated catalog of every setting exposed by the companion settings protocol. */
public final class GuiSettingsCatalog {
    private static final int EXPECTED_TOTAL = 106;
    private static final int EXPECTED_PLAYER_ENGINE = 70;
    private static final int EXPECTED_PLAYER2_SERVER = 36;
    private static final int COMMON_SNAPSHOT_FIELD_OVERHEAD = 12;
    private static final int MAX_SAFE_SNAPSHOT_FIELDS_PER_TAB = 176;
    private static final int MAX_WIRE_NAME_LENGTH = 64;
    private static final Pattern CONFIG_KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9]*");
    private static final Pattern SECTION_KEY = Pattern.compile("[a-z][a-z0-9_]*");

    private static final Set<String> ALLOWED_TABS = Set.of(
            "getting_started",
            "companions",
            "behavior",
            "budget",
            "mod_intelligence",
            "ai_memory",
            "automation_items");

    private static final Set<GuiSettingSection> ALLOWED_SECTIONS = Set.of(
            GuiSettingSections.GENERAL_LIMITS,
            GuiSettingSections.SURVIVAL_COMBAT_TRAVEL,
            GuiSettingSections.BILLING_ROUTING,
            GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT,
            GuiSettingSections.ELLIE_GPS,
            GuiSettingSections.MOD_INTELLIGENCE,
            GuiSettingSections.TASK_PLANNING_GATHERING,
            GuiSettingSections.CRAFTING_SMELTING_MINING,
            GuiSettingSections.STORAGE_DEPOSIT_LABELS,
            GuiSettingSections.INVENTORY_FUEL);

    private static final Set<String> EXPECTED_PLAYER_ENGINE_KEYS = Set.of(
            "commandPrefix",
            "resourcePickupDropRange",
            "minimumFoodAllowed",
            "foodUnitsToCollect",
            "resourceChestLocateRange",
            "resourceMineRange",
            "avoidSearchingDungeonChests",
            "avoidOceanBlocks",
            "entityReachRange",
            "collectPickaxeFirst",
            "mobDefense",
            "forceFieldStrategy",
            "dodgeProjectiles",
            "killOrAvoidAnnoyingHostiles",
            "avoidDrowning",
            "extinguishSelfWithWater",
            "autoEat",
            "hungerEnabled",
            "autoMLGBucket",
            "overworldToNetherBehaviour",
            "netherFastTravelWalkingRange",
            "idleCommand",
            "fastCraftMacrosEnabled",
            "craftDelaySeconds",
            "craftTableLookHoldSeconds",
            "preferLocalCraftingTable",
            "craftingTableReuseRadius",
            "enableAgenticPlanner",
            "enableLookAtOwnerIdle",
            "agenticPlannerRagTopK",
            "agenticPlannerMaxSteps",
            "agenticPlannerFallbackGather",
            "gatherLooseItemsRadius",
            "gatherLooseItemsMaxItems",
            "gatherLooseItemsTimeoutSeconds",
            "gatherLooseItemsSettleSeconds",
            "agenticStorageSearchRadius",
            "agenticStoragePlacementRadius",
            "agenticMaxTravelRadius",
            "agenticStorageAllowPlacement",
            "agenticStoragePreferExisting",
            "agenticStorageAvoidLootChests",
            "agenticStorageResolveTimeoutSeconds",
            "agenticDepositTimeoutSeconds",
            "agenticDepositKeepTools",
            "agenticEnableLabelChest",
            "agenticLabelTimeoutSeconds",
            "agenticLabelUseModelText",
            "enableDeferredSmelt",
            "deferredSmeltMaxBatch",
            "enableSmithing",
            "smithMaxBatch",
            "deferredSmeltStallPolls",
            "deferredSmeltTimeoutSeconds",
            "aggregateCountDropRadius",
            "aggregateLocalSourceBlockRadius",
            "mineCollectSettleSeconds",
            "wanderBoundDefaultSeconds",
            "wanderNoImprovementSeconds",
            "ellieGpsEnabled",
            "ellieGpsSnapshotSlotThreshold",
            "ellieGpsUseModelDescription",
            "throwawayItems",
            "dontThrowAwayCustomNameItems",
            "dontThrowAwayEnchantedItems",
            "throwAwayUnusedItems",
            "importantItems",
            "limitFuelsToSupportedFuels",
            "supportedFuels",
            "homeBasePosition");

    private static final Set<String> EXPECTED_PLAYER2_SERVER_KEYS = Set.of(
            "payerMode",
            "ownerOfflineServerContinuation",
            "callByNameChat",
            "maxSpawnedCompanionsPerPlayer",
            "maxStoredCharacterIdsPerPlayer",
            "chatCompletionMaxOutputTokens",
            "serverOverridesPlayerConfig",
            "botKeepInventoryOverride",
            "botTtsPlaybackAckEnabled",
            "ragFallbackToFullList",
            "ragLiveEnabled",
            "ragMinGoalChars",
            "deepCheckMaxAttemptsPerTurn",
            "deepCheckCallsPerWindow",
            "deepCheckWindowMinutes",
            "deepCheckWeakBelowScore",
            "deepCheckWeakGapRatio",
            "deepCheckWeakTokenCoverage",
            "forceDeepCheckOnEmpty",
            "memoryWindowMinutes",
            "memoryExtractionBatchMin",
            "memoryExtractionBatchMax",
            "memoryExtractionLengthThreshold",
            "memoryMaxHops",
            "memoryMaxEgoNodes",
            "memoryBlockCharCap",
            "memoryMinConfidence",
            "memoryDecayBase",
            "memoryRetrievalTopK",
            "reflectionImportanceThreshold",
            "relationshipSummaryCharCap",
            "modIntelligenceInspectOnLaunch",
            "modIntelligenceMaxInspectEntriesPerLaunch",
            "modIntelligenceMaxEnrichmentFailuresPerLaunch",
            "modIntelligenceQueryTopK",
            "modIntelligenceMinQueryConfidence");

    private static final List<GuiSettingDefinition> ALL = buildAll();
    private static final Map<String, List<GuiSettingDefinition>> BY_TAB = buildByTab();
    private static final Map<String, GuiSettingDefinition> BY_ID = buildById();

    static {
        validateCatalog();
    }

    private GuiSettingsCatalog() {
    }

    public static List<GuiSettingDefinition> all() {
        return ALL;
    }

    public static List<GuiSettingDefinition> forTab(String tabWireName) {
        return BY_TAB.getOrDefault(tabWireName, List.of());
    }

    public static Optional<GuiSettingDefinition> find(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Optional<GuiSettingDefinition> find(GuiSettingSource source, String configKey) {
        if (source == null || configKey == null) {
            return Optional.empty();
        }
        return find(source.prefix() + "." + configKey);
    }

    /**
     * Deterministic fail-fast validation. Public so protocol tests can exercise the same invariants as
     * class initialization.
     */
    public static void validateCatalog() {
        require(ALL.size() == EXPECTED_TOTAL, "Catalog must contain exactly 106 settings");

        EnumMap<GuiSettingSource, Integer> sourceCounts = new EnumMap<>(GuiSettingSource.class);
        EnumMap<GuiSettingSource, Set<String>> sourceKeys = new EnumMap<>(GuiSettingSource.class);
        Set<String> ids = new HashSet<>();
        Set<String> snapshotFields = new HashSet<>();
        Set<String> actions = new HashSet<>();
        Map<String, Integer> tabCounts = new LinkedHashMap<>();

        for (GuiSettingDefinition definition : ALL) {
            sourceCounts.merge(definition.source(), 1, Integer::sum);
            sourceKeys.computeIfAbsent(definition.source(), ignored -> new HashSet<>())
                    .add(definition.configKey());
            require(ids.add(definition.id()), "Duplicate setting id: " + definition.id());
            require(snapshotFields.add(definition.snapshotField()),
                    "Duplicate snapshot field: " + definition.snapshotField());
            require(actions.add(definition.action()), "Duplicate action: " + definition.action());
            tabCounts.merge(definition.tabWireName(), 1, Integer::sum);
            validateDefinition(definition);
        }

        require(sourceCounts.getOrDefault(GuiSettingSource.PLAYER_ENGINE, 0) == EXPECTED_PLAYER_ENGINE,
                "PlayerEngine catalog must contain exactly 70 settings");
        require(sourceCounts.getOrDefault(GuiSettingSource.PLAYER2_SERVER, 0) == EXPECTED_PLAYER2_SERVER,
                "Player2 server catalog must contain exactly 36 settings");
        require(EXPECTED_PLAYER_ENGINE_KEYS.equals(
                        sourceKeys.getOrDefault(GuiSettingSource.PLAYER_ENGINE, Set.of())),
                "PlayerEngine catalog keys do not match the approved allowlist");
        require(EXPECTED_PLAYER2_SERVER_KEYS.equals(
                        sourceKeys.getOrDefault(GuiSettingSource.PLAYER2_SERVER, Set.of())),
                "Player2 server catalog keys do not match the approved allowlist");

        for (Map.Entry<String, Integer> tab : tabCounts.entrySet()) {
            int projectedFieldCount = tab.getValue() + COMMON_SNAPSHOT_FIELD_OVERHEAD;
            require(projectedFieldCount <= MAX_SAFE_SNAPSHOT_FIELDS_PER_TAB,
                    "Tab exceeds the safe snapshot field budget: " + tab.getKey());
        }
    }

    private static void validateDefinition(GuiSettingDefinition definition) {
        require(CONFIG_KEY.matcher(definition.configKey()).matches(),
                "Invalid config key: " + definition.configKey());
        require(definition.configKey().length() <= MAX_WIRE_NAME_LENGTH,
                "Config key exceeds wire limit: " + definition.configKey());
        require(ALLOWED_TABS.contains(definition.tabWireName()),
                "Unknown tab wire name: " + definition.tabWireName());
        require(ALLOWED_SECTIONS.contains(definition.section()),
                "Unknown setting section: " + definition.section().key());
        require(SECTION_KEY.matcher(definition.section().key()).matches(),
                "Invalid section key: " + definition.section().key());
        require(definition.section().collapsedByDefault()
                        == definition.section().equals(GuiSettingSections.RAG_DEEPSEARCH_MEMORY_EXPERT),
                "Only the expert section may start collapsed: " + definition.section().key());
        require(definition.snapshotField().length() <= MAX_WIRE_NAME_LENGTH,
                "Snapshot field exceeds wire limit: " + definition.snapshotField());
        require(definition.action().length() <= MAX_WIRE_NAME_LENGTH,
                "Action exceeds wire limit: " + definition.action());
        require(definition.titleKey().startsWith(settingTranslationPrefix(definition)),
                "Invalid title translation key: " + definition.id());
        require(definition.descriptionKey().startsWith(settingTranslationPrefix(definition)),
                "Invalid description translation key: " + definition.id());
        require(definition.unitKey().startsWith(settingTranslationPrefix(definition)),
                "Invalid unit translation key: " + definition.id());
        require(definition.effectKey().equals("screen.player2npc.ui.settings.effect."
                        + definition.effect().name().toLowerCase(java.util.Locale.ROOT)),
                "Invalid effect translation key: " + definition.id());
        require(definition.section().translationKey().startsWith("screen.player2npc.ui.settings.section."),
                "Invalid section translation key: " + definition.section().key());

        if (definition.zeroMeaning() != GuiSettingZeroMeaning.NONE) {
            require(definition.minimum() == 0,
                    "A zero semantic requires a zero minimum: " + definition.id());
        }

        switch (definition.type()) {
            case BOOLEAN -> {
                validateScalarMetadata(definition, 0, 1, 1);
                require(definition.zeroMeaning() == GuiSettingZeroMeaning.NONE,
                        "Boolean setting cannot use numeric zero semantics: " + definition.id());
            }
            case INTEGER -> {
                require(definition.minimum() >= 0, "Negative minimum: " + definition.id());
                require(definition.maximum() >= definition.minimum(), "Inverted range: " + definition.id());
                require(definition.step() >= 1, "Non-positive step: " + definition.id());
                require(!definition.logarithmicHint() || definition.minimum() > 0,
                        "Logarithmic integer range must start above zero: " + definition.id());
                validateUnusedPresentationMetadata(definition);
                require(definition.options().isEmpty(), "Integer setting has enum options: " + definition.id());
            }
            case PERCENT -> {
                validateScalarMetadata(definition, 0, 100, 1);
                require(definition.zeroMeaning() == GuiSettingZeroMeaning.NONE,
                        "Percentage setting has a misleading zero semantic: " + definition.id());
            }
            case PERMILLE -> {
                require(definition.minimum() >= 0 && definition.maximum() <= 1000,
                        "Per-mille range is outside 0..1000: " + definition.id());
                require(definition.maximum() >= definition.minimum() && definition.step() == 1,
                        "Invalid per-mille range: " + definition.id());
                validateUnusedPresentationMetadata(definition);
                require(definition.options().isEmpty(), "Per-mille setting has enum options: " + definition.id());
            }
            case ENUM -> {
                validateNonScalarBounds(definition);
                require(!definition.options().isEmpty(), "Enum setting has no options: " + definition.id());
                Set<String> tokens = new HashSet<>();
                String optionPrefix = "screen.player2npc.ui.settings.option."
                        + definition.source().prefix() + "." + definition.configKey() + ".";
                for (GuiSettingOption option : definition.options()) {
                    require(!option.token().isBlank(), "Blank enum token: " + definition.id());
                    require(tokens.add(option.token()), "Duplicate enum token: " + definition.id());
                    require(option.translationKey().startsWith(optionPrefix),
                            "Invalid enum option translation key: " + definition.id());
                }
            }
            case TEXT -> {
                validateNonScalarBounds(definition);
                require(definition.maxTextLength() > 0, "Text setting has no length cap: " + definition.id());
                require(definition.maxListEntries() == 0 && definition.maxSerializedLength() == 0,
                        "Text setting has list metadata: " + definition.id());
                require(definition.options().isEmpty(), "Text setting has enum options: " + definition.id());
            }
            case ITEM_LIST -> {
                validateNonScalarBounds(definition);
                require(definition.maxTextLength() == 0, "Item list has text metadata: " + definition.id());
                require(definition.maxListEntries() > 0 && definition.maxListEntries() <= 64,
                        "Item list entry cap is outside 1..64: " + definition.id());
                require(definition.maxSerializedLength() > 0 && definition.maxSerializedLength() <= 3500,
                        "Item list serialized cap is outside 1..3500: " + definition.id());
                require(definition.options().isEmpty(), "Item list has enum options: " + definition.id());
            }
            case BLOCK_POS -> {
                validateNonScalarBounds(definition);
                require(definition.source() == GuiSettingSource.PLAYER_ENGINE
                                && definition.configKey().equals("homeBasePosition"),
                        "Only PlayerEngine homeBasePosition may use BLOCK_POS");
                require(definition.action().equals("set-current.pe.homeBasePosition"),
                        "BLOCK_POS must use the set-current action");
                require(definition.options().isEmpty(), "Block position has enum options: " + definition.id());
            }
        }

        if (definition.type() != GuiSettingType.BLOCK_POS) {
            require(definition.action().equals("set." + definition.id()),
                    "Setting must use the ordinary set action: " + definition.id());
        }
    }

    private static void validateScalarMetadata(
            GuiSettingDefinition definition, int minimum, int maximum, int step) {
        require(definition.minimum() == minimum
                        && definition.maximum() == maximum
                        && definition.step() == step,
                "Unexpected scalar range: " + definition.id());
        validateUnusedPresentationMetadata(definition);
        require(definition.options().isEmpty(), "Scalar setting has enum options: " + definition.id());
    }

    private static void validateNonScalarBounds(GuiSettingDefinition definition) {
        require(definition.minimum() == 0 && definition.maximum() == 0 && definition.step() == 0,
                "Non-scalar setting has numeric bounds: " + definition.id());
        require(!definition.logarithmicHint(), "Non-scalar setting has a logarithmic hint: " + definition.id());
        require(definition.zeroMeaning() == GuiSettingZeroMeaning.NONE,
                "Non-scalar setting has a numeric zero semantic: " + definition.id());
    }

    private static void validateUnusedPresentationMetadata(GuiSettingDefinition definition) {
        require(definition.maxTextLength() == 0
                        && definition.maxListEntries() == 0
                        && definition.maxSerializedLength() == 0,
                "Scalar setting has text/list metadata: " + definition.id());
    }

    private static String settingTranslationPrefix(GuiSettingDefinition definition) {
        return "screen.player2npc.ui.settings." + definition.source().prefix() + "."
                + definition.configKey() + ".";
    }

    private static List<GuiSettingDefinition> buildAll() {
        ArrayList<GuiSettingDefinition> definitions = new ArrayList<>(EXPECTED_TOTAL);
        definitions.addAll(PlayerEngineGuiSettings.ALL);
        definitions.addAll(Player2ServerGuiSettings.ALL);
        return List.copyOf(definitions);
    }

    private static Map<String, List<GuiSettingDefinition>> buildByTab() {
        LinkedHashMap<String, List<GuiSettingDefinition>> mutable = new LinkedHashMap<>();
        for (GuiSettingDefinition definition : ALL) {
            mutable.computeIfAbsent(definition.tabWireName(), ignored -> new ArrayList<>()).add(definition);
        }
        mutable.replaceAll((ignored, definitions) -> List.copyOf(definitions));
        return Collections.unmodifiableMap(mutable);
    }

    private static Map<String, GuiSettingDefinition> buildById() {
        LinkedHashMap<String, GuiSettingDefinition> mutable = new LinkedHashMap<>();
        for (GuiSettingDefinition definition : ALL) {
            GuiSettingDefinition previous = mutable.put(definition.id(), definition);
            require(previous == null, "Duplicate setting id: " + definition.id());
        }
        return Collections.unmodifiableMap(mutable);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
