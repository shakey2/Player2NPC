package com.goodbird.player2npc.companion.gui.settings;

/** Shared UX sections. Expert tuning starts collapsed; ordinary sections start expanded. */
public final class GuiSettingSections {
    public static final GuiSettingSection GENERAL_LIMITS = new GuiSettingSection("general_limits", false);
    public static final GuiSettingSection SURVIVAL_COMBAT_TRAVEL =
            new GuiSettingSection("survival_combat_travel", false);
    public static final GuiSettingSection BILLING_ROUTING = new GuiSettingSection("billing_routing", false);
    public static final GuiSettingSection RAG_DEEPSEARCH_MEMORY_EXPERT =
            new GuiSettingSection("rag_deepsearch_memory_expert", true);
    public static final GuiSettingSection ELLIE_GPS = new GuiSettingSection("ellie_gps", false);
    public static final GuiSettingSection MOD_INTELLIGENCE = new GuiSettingSection("mod_intelligence", false);
    public static final GuiSettingSection TASK_PLANNING_GATHERING =
            new GuiSettingSection("task_planning_gathering", false);
    public static final GuiSettingSection CRAFTING_SMELTING_MINING =
            new GuiSettingSection("crafting_smelting_mining", false);
    public static final GuiSettingSection STORAGE_DEPOSIT_LABELS =
            new GuiSettingSection("storage_deposit_labels", false);
    public static final GuiSettingSection INVENTORY_FUEL = new GuiSettingSection("inventory_fuel", false);

    private GuiSettingSections() {
    }
}
