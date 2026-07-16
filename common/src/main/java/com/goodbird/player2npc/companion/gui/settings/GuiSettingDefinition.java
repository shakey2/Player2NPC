package com.goodbird.player2npc.companion.gui.settings;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Declarative description of one remotely editable setting. Values in this record are wire metadata;
 * presentation is always resolved through the derived translation keys.
 */
public record GuiSettingDefinition(
        GuiSettingSource source,
        String configKey,
        String tabWireName,
        GuiSettingSection section,
        GuiSettingType type,
        int minimum,
        int maximum,
        int step,
        boolean logarithmicHint,
        int maxTextLength,
        int maxListEntries,
        int maxSerializedLength,
        List<GuiSettingOption> options,
        GuiSettingEffect effect,
        GuiSettingZeroMeaning zeroMeaning) {
    private static final String TRANSLATION_PREFIX = "screen.player2npc.ui.settings.";

    public GuiSettingDefinition {
        source = Objects.requireNonNull(source, "source");
        configKey = Objects.requireNonNull(configKey, "configKey");
        tabWireName = Objects.requireNonNull(tabWireName, "tabWireName");
        section = Objects.requireNonNull(section, "section");
        type = Objects.requireNonNull(type, "type");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        effect = Objects.requireNonNull(effect, "effect");
        zeroMeaning = Objects.requireNonNull(zeroMeaning, "zeroMeaning");
    }

    public String id() {
        return source.prefix() + "." + configKey;
    }

    public int min() {
        return minimum;
    }

    public int max() {
        return maximum;
    }

    public boolean logHint() {
        return logarithmicHint;
    }

    public String snapshotField() {
        return "setting." + id();
    }

    public String action() {
        return (type == GuiSettingType.BLOCK_POS ? "set-current." : "set.") + id();
    }

    public String titleKey() {
        return settingTranslationKey("title");
    }

    public String descriptionKey() {
        return settingTranslationKey("description");
    }

    public String unitKey() {
        return settingTranslationKey("unit");
    }

    public String effectKey() {
        return TRANSLATION_PREFIX + "effect." + effect.name().toLowerCase(Locale.ROOT);
    }

    private String settingTranslationKey(String suffix) {
        return TRANSLATION_PREFIX + source.prefix() + "." + configKey + "." + suffix;
    }

    public static GuiSettingDefinition bool(
            GuiSettingSource source,
            String configKey,
            String tabWireName,
            GuiSettingSection section,
            GuiSettingEffect effect) {
        return scalar(source, configKey, tabWireName, section, GuiSettingType.BOOLEAN,
                0, 1, 1, false, List.of(), effect, GuiSettingZeroMeaning.NONE);
    }

    public static GuiSettingDefinition integer(
            GuiSettingSource source,
            String configKey,
            String tabWireName,
            GuiSettingSection section,
            int minimum,
            int maximum,
            int step,
            boolean logarithmicHint,
            GuiSettingEffect effect,
            GuiSettingZeroMeaning zeroMeaning) {
        return scalar(source, configKey, tabWireName, section, GuiSettingType.INTEGER,
                minimum, maximum, step, logarithmicHint, List.of(), effect, zeroMeaning);
    }

    public static GuiSettingDefinition percent(
            GuiSettingSource source,
            String configKey,
            String tabWireName,
            GuiSettingSection section,
            GuiSettingEffect effect) {
        return scalar(source, configKey, tabWireName, section, GuiSettingType.PERCENT,
                0, 100, 1, false, List.of(), effect, GuiSettingZeroMeaning.NONE);
    }

    public static GuiSettingDefinition permille(
            GuiSettingSource source,
            String configKey,
            String tabWireName,
            GuiSettingSection section,
            int minimum,
            int maximum,
            GuiSettingEffect effect) {
        return scalar(source, configKey, tabWireName, section, GuiSettingType.PERMILLE,
                minimum, maximum, 1, false, List.of(), effect, GuiSettingZeroMeaning.NONE);
    }

    public static GuiSettingDefinition text(
            GuiSettingSource source,
            String configKey,
            String tabWireName,
            GuiSettingSection section,
            int maxTextLength,
            GuiSettingEffect effect) {
        return new GuiSettingDefinition(source, configKey, tabWireName, section, GuiSettingType.TEXT,
                0, 0, 0, false, maxTextLength, 0, 0, List.of(), effect, GuiSettingZeroMeaning.NONE);
    }

    public static GuiSettingDefinition enumerated(
            GuiSettingSource source,
            String configKey,
            String tabWireName,
            GuiSettingSection section,
            List<GuiSettingOption> options,
            GuiSettingEffect effect) {
        return scalar(source, configKey, tabWireName, section, GuiSettingType.ENUM,
                0, 0, 0, false, options, effect, GuiSettingZeroMeaning.NONE);
    }

    public static GuiSettingDefinition itemList(
            GuiSettingSource source,
            String configKey,
            String tabWireName,
            GuiSettingSection section,
            int maxListEntries,
            int maxSerializedLength,
            GuiSettingEffect effect) {
        return new GuiSettingDefinition(source, configKey, tabWireName, section, GuiSettingType.ITEM_LIST,
                0, 0, 0, false, 0, maxListEntries, maxSerializedLength, List.of(), effect,
                GuiSettingZeroMeaning.NONE);
    }

    public static GuiSettingDefinition blockPos(
            GuiSettingSource source,
            String configKey,
            String tabWireName,
            GuiSettingSection section,
            GuiSettingEffect effect) {
        return scalar(source, configKey, tabWireName, section, GuiSettingType.BLOCK_POS,
                0, 0, 0, false, List.of(), effect, GuiSettingZeroMeaning.NONE);
    }

    private static GuiSettingDefinition scalar(
            GuiSettingSource source,
            String configKey,
            String tabWireName,
            GuiSettingSection section,
            GuiSettingType type,
            int minimum,
            int maximum,
            int step,
            boolean logarithmicHint,
            List<GuiSettingOption> options,
            GuiSettingEffect effect,
            GuiSettingZeroMeaning zeroMeaning) {
        return new GuiSettingDefinition(source, configKey, tabWireName, section, type,
                minimum, maximum, step, logarithmicHint, 0, 0, 0, options, effect, zeroMeaning);
    }
}
