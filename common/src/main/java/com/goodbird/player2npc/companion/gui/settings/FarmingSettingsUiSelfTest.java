package com.goodbird.player2npc.companion.gui.settings;

import com.goodbird.player2npc.client.gui.Player2NpcTab;
import com.goodbird.player2npc.companion.gui.GuiActionResult;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic schema/catalog/localization gate for the final farming settings surface. */
public final class FarmingSettingsUiSelfTest {
    private static final String PREFIX = "screen.player2npc.ui.settings.pe.";
    private static final String REPLANT_TITLE = PREFIX + "replantCrops.title";
    private static final String REPLANT_DESCRIPTION = PREFIX + "replantCrops.description";
    private static final String ELLIE_ENABLED_DESCRIPTION =
            PREFIX + "ellieGpsEnabled.description";
    private static final String ELLIE_MODEL_TITLE =
            PREFIX + "ellieGpsUseModelDescription.title";
    private static final String ELLIE_MODEL_DESCRIPTION =
            PREFIX + "ellieGpsUseModelDescription.description";

    private static final String PINNED_ENABLED =
            "Enable inventory and farm waypoint creation, lookup, auditing, and memory.";
    private static final String PINNED_MODEL_TITLE =
            "Model-polished inventory waypoint descriptions";
    private static final String PINNED_MODEL_DESCRIPTION =
            "Use asynchronous summarization to polish inventory and storage waypoint descriptions. "
                    + "Farm descriptions stay deterministic.";

    private static final List<String> LOCALES = List.of(
            "ar_sa", "de_de", "en_us", "es_cl", "es_es", "es_mx", "fr_fr", "hi_in",
            "id_id", "it_it", "ja_jp", "ko_kr", "pt_br", "pt_pt", "ru_ru", "ta_in",
            "tr_tr", "zh_cn", "zh_hk", "zh_tw");
    private static final Set<String> COMPLETE_LOCALES = Set.of("en_us", "it_it", "ko_kr");
    private static final Set<String> ALLOWED_EXISTING_GAPS = Set.of(
            "command.player2npc.cleanup.error_no_server",
            "command.player2npc.cleanup.error_offline",
            "command.player2npc.cleanup.success_all_summary",
            "command.player2npc.cleanup.success_none",
            "command.player2npc.cleanup.success_removed",
            "command.player2npc.cleanup.success_removed_named",
            "help.player2npc.cleanup-clones.long",
            "help.player2npc.cleanup-clones.short",
            "help.player2npc.cleanup-clones-all.long",
            "help.player2npc.cleanup-clones-all.short",
            "help.player2npc.cleanup-clones-player.arg.targets",
            "help.player2npc.cleanup-clones-player.long",
            "help.player2npc.cleanup-clones-player.short",
            "message.player2npc.companion.relocate_failed");

    private FarmingSettingsUiSelfTest() {
    }

    public static void main(String[] args) {
        catalogAndSchemaContract();
        removedActionCannotMutate();
        ellieGpsDefinitions();
        localeContract();
        System.out.println("FarmingSettingsUiSelfTest: all checks passed");
    }

    private static void catalogAndSchemaContract() {
        GuiSettingsCatalog.validateCatalog();
        check(GuiSettingsCatalog.all().size() == 106, "catalog total is 106");
        long playerEngineCount = GuiSettingsCatalog.all().stream()
                .filter(definition -> definition.source() == GuiSettingSource.PLAYER_ENGINE)
                .count();
        long serverCount = GuiSettingsCatalog.all().stream()
                .filter(definition -> definition.source() == GuiSettingSource.PLAYER2_SERVER)
                .count();
        check(playerEngineCount == 70 && serverCount == 36, "source counts are 70/36");
        check(GuiSettingsCatalog.find(GuiSettingSource.PLAYER_ENGINE, "replantCrops").isEmpty(),
                "replant row is absent");
        check(GuiSettingsCatalog.all().stream().noneMatch(definition ->
                        "setting.pe.replantCrops".equals(definition.snapshotField())
                                || "set.pe.replantCrops".equals(definition.action())),
                "replant snapshot field and action are absent");
        check("2".equals(Player2NpcConfigService.settingsSchemaVersionForTest()),
                "settings schema is version 2");
    }

    private static void removedActionCannotMutate() {
        int[] mutations = {0};
        GuiActionResult result = Player2NpcConfigService.resolveAction(
                Player2NpcTab.AUTOMATION_ITEMS,
                "set.pe.replantCrops",
                definition -> {
                    mutations[0]++;
                    return GuiActionResult.ok();
                });
        check(!result.success() && "unsupported_action".equals(result.reasonCode()),
                "removed action is rejected as unsupported");
        check(mutations[0] == 0, "removed action never invokes mutation callback");
    }

    private static void ellieGpsDefinitions() {
        assertEllieDefinition("ellieGpsEnabled", GuiSettingType.BOOLEAN);
        assertEllieDefinition("ellieGpsSnapshotSlotThreshold", GuiSettingType.INTEGER);
        assertEllieDefinition("ellieGpsUseModelDescription", GuiSettingType.BOOLEAN);
    }

    private static void assertEllieDefinition(String key, GuiSettingType type) {
        GuiSettingDefinition definition = GuiSettingsCatalog
                .find(GuiSettingSource.PLAYER_ENGINE, key)
                .orElseThrow(() -> new AssertionError("missing EllieGPS setting " + key));
        check("automation_items".equals(definition.tabWireName()), key + " tab");
        check(GuiSettingSections.ELLIE_GPS.equals(definition.section()), key + " section");
        check(definition.type() == type, key + " type");
        check(definition.effect() == GuiSettingEffect.LIVE, key + " live effect");
    }

    private static void localeContract() {
        JsonObject english = loadLocale("en_us");
        check(PINNED_ENABLED.equals(value(english, ELLIE_ENABLED_DESCRIPTION)),
                "pinned English EllieGPS master description");
        check(PINNED_MODEL_TITLE.equals(value(english, ELLIE_MODEL_TITLE)),
                "pinned English model-polish title");
        check(PINNED_MODEL_DESCRIPTION.equals(value(english, ELLIE_MODEL_DESCRIPTION)),
                "pinned English model-polish description");
        check(!english.has(REPLANT_TITLE) && !english.has(REPLANT_DESCRIPTION),
                "removed English replant keys are absent");

        for (GuiSettingDefinition definition : GuiSettingsCatalog.all()) {
            requireNonblank(english, definition.titleKey(), "English title");
            requireNonblank(english, definition.descriptionKey(), "English description");
            if (isNumeric(definition.type())) {
                requireNonblank(english, definition.unitKey(), "English numeric unit");
            }
        }

        Set<String> englishKeys = english.keySet();
        for (String locale : LOCALES) {
            JsonObject translated = loadLocale(locale);
            check(!translated.has(REPLANT_TITLE) && !translated.has(REPLANT_DESCRIPTION),
                    locale + " removed replant keys");
            requireNonblank(translated, ELLIE_ENABLED_DESCRIPTION, locale);
            requireNonblank(translated, ELLIE_MODEL_TITLE, locale);
            requireNonblank(translated, ELLIE_MODEL_DESCRIPTION, locale);
            if (!"en_us".equals(locale)) {
                check(!PINNED_ENABLED.equals(value(translated, ELLIE_ENABLED_DESCRIPTION)),
                        locale + " master description is translated");
                check(!PINNED_MODEL_TITLE.equals(value(translated, ELLIE_MODEL_TITLE)),
                        locale + " model title is translated");
                check(!PINNED_MODEL_DESCRIPTION.equals(value(translated, ELLIE_MODEL_DESCRIPTION)),
                        locale + " model description is translated");
            }

            HashSet<String> missing = new HashSet<>(englishKeys);
            missing.removeAll(translated.keySet());
            if (COMPLETE_LOCALES.contains(locale)) {
                check(missing.isEmpty(), locale + " remains complete: " + missing);
            } else {
                check(ALLOWED_EXISTING_GAPS.containsAll(missing),
                        locale + " added localization gaps: " + missing);
            }
        }
    }

    private static JsonObject loadLocale(String locale) {
        String resource = "assets/player2npc/lang/" + locale + ".json";
        InputStream stream = FarmingSettingsUiSelfTest.class.getClassLoader()
                .getResourceAsStream(resource);
        if (stream == null) {
            throw new AssertionError("missing locale resource " + resource);
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new AssertionError("locale is not a JSON object: " + locale);
            }
            return parsed.getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new AssertionError("failed reading locale " + locale, error);
        }
    }

    private static void requireNonblank(JsonObject locale, String key, String context) {
        check(locale.has(key) && !value(locale, key).isBlank(), context + " missing " + key);
    }

    private static String value(JsonObject locale, String key) {
        JsonElement value = locale.get(key);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static boolean isNumeric(GuiSettingType type) {
        return type == GuiSettingType.INTEGER
                || type == GuiSettingType.PERCENT
                || type == GuiSettingType.PERMILLE;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Farming settings UI self-test failed: " + message);
        }
    }
}
