package com.goodbird.player2npc.companion.gui.settings;

import java.util.List;
import java.util.Locale;

/** PlayerEngine-owned settings approved for the mod settings UI. */
final class PlayerEngineGuiSettings {
    private static final GuiSettingSource SOURCE = GuiSettingSource.PLAYER_ENGINE;
    private static final String BEHAVIOR = "behavior";
    private static final String AUTOMATION_ITEMS = "automation_items";
    private static final GuiSettingEffect LIVE = GuiSettingEffect.LIVE;
    private static final GuiSettingEffect NEXT_TASK = GuiSettingEffect.NEXT_TASK;

    static final List<GuiSettingDefinition> ALL = List.of(
            text("commandPrefix", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS, 8, LIVE),
            integer("resourcePickupDropRange", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS,
                    0, 128, false, LIVE, GuiSettingZeroMeaning.OFF),
            integer("minimumFoodAllowed", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL,
                    0, 64, false, LIVE, GuiSettingZeroMeaning.NONE),
            integer("foodUnitsToCollect", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL,
                    0, 64, false, LIVE, GuiSettingZeroMeaning.OFF),
            integer("resourceChestLocateRange", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS,
                    0, 4096, false, LIVE, GuiSettingZeroMeaning.OFF),
            integer("resourceMineRange", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS,
                    0, 4096, false, LIVE, GuiSettingZeroMeaning.OFF),
            bool("avoidSearchingDungeonChests", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL, LIVE),
            bool("avoidOceanBlocks", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL, LIVE),
            integer("entityReachRange", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL,
                    1, 6, false, LIVE, GuiSettingZeroMeaning.NONE),
            bool("collectPickaxeFirst", AUTOMATION_ITEMS, GuiSettingSections.TASK_PLANNING_GATHERING, NEXT_TASK),
            bool("mobDefense", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL, LIVE),
            enumerated("forceFieldStrategy", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL,
                    LIVE, "OFF", "FASTEST", "DELAY", "SMART"),
            bool("dodgeProjectiles", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL, LIVE),
            bool("killOrAvoidAnnoyingHostiles", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL, LIVE),
            bool("avoidDrowning", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL, LIVE),
            bool("extinguishSelfWithWater", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL, LIVE),
            bool("autoEat", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL, LIVE),
            bool("hungerEnabled", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL, LIVE),
            bool("autoMLGBucket", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL, LIVE),
            enumerated("overworldToNetherBehaviour", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL,
                    LIVE, "BUILD_PORTAL_VANILLA", "GO_TO_HOME_BASE"),
            integer("netherFastTravelWalkingRange", BEHAVIOR, GuiSettingSections.SURVIVAL_COMBAT_TRAVEL,
                    0, 100000, false, LIVE, GuiSettingZeroMeaning.NONE),
            text("idleCommand", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS, 128, LIVE),
            bool("fastCraftMacrosEnabled", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING,
                    NEXT_TASK),
            integer("craftDelaySeconds", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING,
                    0, 5000, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("craftTableLookHoldSeconds", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING,
                    0, 3000, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            bool("preferLocalCraftingTable", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING,
                    NEXT_TASK),
            integer("craftingTableReuseRadius", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING,
                    4, 128, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            bool("enableAgenticPlanner", AUTOMATION_ITEMS, GuiSettingSections.TASK_PLANNING_GATHERING, NEXT_TASK),
            bool("enableLookAtOwnerIdle", BEHAVIOR, GuiSettingSections.GENERAL_LIMITS, LIVE),
            integer("agenticPlannerRagTopK", AUTOMATION_ITEMS, GuiSettingSections.TASK_PLANNING_GATHERING,
                    1, 20, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("agenticPlannerMaxSteps", AUTOMATION_ITEMS, GuiSettingSections.TASK_PLANNING_GATHERING,
                    1, 4, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            bool("agenticPlannerFallbackGather", AUTOMATION_ITEMS,
                    GuiSettingSections.TASK_PLANNING_GATHERING, NEXT_TASK),
            integer("gatherLooseItemsRadius", AUTOMATION_ITEMS, GuiSettingSections.TASK_PLANNING_GATHERING,
                    2, 64, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("gatherLooseItemsMaxItems", AUTOMATION_ITEMS, GuiSettingSections.TASK_PLANNING_GATHERING,
                    1, 1024, true, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("gatherLooseItemsTimeoutSeconds", AUTOMATION_ITEMS,
                    GuiSettingSections.TASK_PLANNING_GATHERING,
                    5, 300, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("gatherLooseItemsSettleSeconds", AUTOMATION_ITEMS,
                    GuiSettingSections.TASK_PLANNING_GATHERING,
                    500, 20000, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("agenticStorageSearchRadius", AUTOMATION_ITEMS, GuiSettingSections.STORAGE_DEPOSIT_LABELS,
                    4, 64, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("agenticStoragePlacementRadius", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS,
                    2, 8, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("agenticMaxTravelRadius", AUTOMATION_ITEMS, GuiSettingSections.TASK_PLANNING_GATHERING,
                    16, 160, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            bool("agenticStorageAllowPlacement", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS, NEXT_TASK),
            bool("agenticStoragePreferExisting", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS, NEXT_TASK),
            bool("agenticStorageAvoidLootChests", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS, NEXT_TASK),
            integer("agenticStorageResolveTimeoutSeconds", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS,
                    10, 300, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("agenticDepositTimeoutSeconds", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS,
                    10, 300, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            bool("agenticDepositKeepTools", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS, NEXT_TASK),
            bool("agenticEnableLabelChest", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS, NEXT_TASK),
            integer("agenticLabelTimeoutSeconds", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS,
                    10, 180, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            bool("agenticLabelUseModelText", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS, NEXT_TASK),
            bool("enableDeferredSmelt", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING, NEXT_TASK),
            integer("deferredSmeltMaxBatch", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING,
                    1, 512, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            bool("enableSmithing", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING, NEXT_TASK),
            integer("smithMaxBatch", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING,
                    1, 512, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("deferredSmeltStallPolls", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING,
                    20, 2000, true, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("deferredSmeltTimeoutSeconds", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING,
                    60, 3600, true, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("aggregateCountDropRadius", AUTOMATION_ITEMS, GuiSettingSections.TASK_PLANNING_GATHERING,
                    0, 64, false, NEXT_TASK, GuiSettingZeroMeaning.OFF),
            integer("aggregateLocalSourceBlockRadius", AUTOMATION_ITEMS,
                    GuiSettingSections.TASK_PLANNING_GATHERING,
                    0, 128, false, NEXT_TASK, GuiSettingZeroMeaning.OFF),
            integer("mineCollectSettleSeconds", AUTOMATION_ITEMS, GuiSettingSections.CRAFTING_SMELTING_MINING,
                    0, 5000, false, NEXT_TASK, GuiSettingZeroMeaning.NONE),
            integer("wanderBoundDefaultSeconds", AUTOMATION_ITEMS, GuiSettingSections.TASK_PLANNING_GATHERING,
                    0, 600, false, NEXT_TASK, GuiSettingZeroMeaning.OFF),
            integer("wanderNoImprovementSeconds", AUTOMATION_ITEMS, GuiSettingSections.TASK_PLANNING_GATHERING,
                    0, 300, false, NEXT_TASK, GuiSettingZeroMeaning.OFF),
            bool("ellieGpsEnabled", AUTOMATION_ITEMS, GuiSettingSections.ELLIE_GPS, LIVE),
            integer("ellieGpsSnapshotSlotThreshold", AUTOMATION_ITEMS, GuiSettingSections.ELLIE_GPS,
                    1, 54, false, LIVE, GuiSettingZeroMeaning.NONE),
            bool("ellieGpsUseModelDescription", AUTOMATION_ITEMS, GuiSettingSections.ELLIE_GPS, LIVE),
            itemList("throwawayItems", AUTOMATION_ITEMS, GuiSettingSections.INVENTORY_FUEL, NEXT_TASK),
            bool("dontThrowAwayCustomNameItems", AUTOMATION_ITEMS, GuiSettingSections.INVENTORY_FUEL, NEXT_TASK),
            bool("dontThrowAwayEnchantedItems", AUTOMATION_ITEMS, GuiSettingSections.INVENTORY_FUEL, NEXT_TASK),
            bool("throwAwayUnusedItems", AUTOMATION_ITEMS, GuiSettingSections.INVENTORY_FUEL, NEXT_TASK),
            itemList("importantItems", AUTOMATION_ITEMS, GuiSettingSections.INVENTORY_FUEL, NEXT_TASK),
            bool("limitFuelsToSupportedFuels", AUTOMATION_ITEMS, GuiSettingSections.INVENTORY_FUEL, NEXT_TASK),
            itemList("supportedFuels", AUTOMATION_ITEMS, GuiSettingSections.INVENTORY_FUEL, NEXT_TASK),
            GuiSettingDefinition.blockPos(SOURCE, "homeBasePosition", AUTOMATION_ITEMS,
                    GuiSettingSections.STORAGE_DEPOSIT_LABELS, NEXT_TASK));

    private PlayerEngineGuiSettings() {
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
            GuiSettingEffect effect,
            GuiSettingZeroMeaning zeroMeaning) {
        return GuiSettingDefinition.integer(SOURCE, key, tab, section, minimum, maximum, 1,
                logarithmicHint, effect, zeroMeaning);
    }

    private static GuiSettingDefinition text(
            String key, String tab, GuiSettingSection section, int maxLength, GuiSettingEffect effect) {
        return GuiSettingDefinition.text(SOURCE, key, tab, section, maxLength, effect);
    }

    private static GuiSettingDefinition enumerated(
            String key,
            String tab,
            GuiSettingSection section,
            GuiSettingEffect effect,
            String... tokens) {
        List<GuiSettingOption> options = java.util.Arrays.stream(tokens)
                .map(token -> new GuiSettingOption(token, optionKey(key, token)))
                .toList();
        return GuiSettingDefinition.enumerated(SOURCE, key, tab, section, options, effect);
    }

    private static GuiSettingDefinition itemList(
            String key, String tab, GuiSettingSection section, GuiSettingEffect effect) {
        return GuiSettingDefinition.itemList(SOURCE, key, tab, section, 64, 3500, effect);
    }

    private static String optionKey(String key, String token) {
        return "screen.player2npc.ui.settings.option.pe." + key + "." + token.toLowerCase(Locale.ROOT);
    }
}
