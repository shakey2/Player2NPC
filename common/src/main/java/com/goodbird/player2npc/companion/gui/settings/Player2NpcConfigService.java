package com.goodbird.player2npc.companion.gui.settings;

import com.goodbird.player2npc.client.gui.Player2NpcTab;
import com.goodbird.player2npc.companion.gui.GuiActionResult;
import com.player2.playerengine.PlayerEngineSettingsAdminService;
import com.player2.playerengine.player2api.config.Player2ServerConfigAdminService;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Permission-enforcing bridge between the declarative GUI catalog and config admin services. */
public final class Player2NpcConfigService {
    private static final String SETTINGS_SCHEMA_VERSION = "2";

    @FunctionalInterface
    interface SettingMutation {
        GuiActionResult apply(GuiSettingDefinition definition);
    }

    private Player2NpcConfigService() {
    }

    public static void appendSnapshot(MinecraftServer server, ServerPlayer player, Player2NpcTab tab,
                                      Map<String, String> fields) {
        if (fields == null) {
            return;
        }
        fields.put("settingsSupported", "true");
        fields.put("settingsSchemaVersion", SETTINGS_SCHEMA_VERSION);
        fields.put("canManageGlobal", String.valueOf(canManageGlobal(server, player)));
        fields.put("dedicatedClientProxy",
                String.valueOf(Player2ServerConfigHolder.get().isDedicatedClientProxy()));

        if (tab == null) {
            return;
        }
        List<GuiSettingDefinition> definitions = GuiSettingsCatalog.forTab(tab.wireName());
        PlayerEngineSettingsAdminService.SnapshotResult playerEngineSnapshot = null;
        Player2ServerConfigAdminService.SnapshotResult player2ServerSnapshot = null;
        for (GuiSettingDefinition definition : definitions) {
            Map<String, String> sourceSnapshot;
            switch (definition.source()) {
                case PLAYER_ENGINE -> {
                    if (playerEngineSnapshot == null) {
                        playerEngineSnapshot = PlayerEngineSettingsAdminService.snapshotResult();
                        fields.put("settingsLoadFailed.pe", String.valueOf(playerEngineSnapshot.loadFailed()));
                        fields.put("settingsLiveState.pe",
                                playerEngineSnapshot.liveState().name().toLowerCase(Locale.ROOT));
                    }
                    sourceSnapshot = playerEngineSnapshot.values();
                }
                case PLAYER2_SERVER -> {
                    if (player2ServerSnapshot == null) {
                        player2ServerSnapshot = Player2ServerConfigAdminService.snapshotResult();
                        fields.put("settingsLoadFailed.server",
                                String.valueOf(player2ServerSnapshot.loadFailed()));
                    }
                    sourceSnapshot = player2ServerSnapshot.values();
                }
                default -> throw new IllegalStateException("Unsupported GUI setting source");
            }
            String value = sourceSnapshot.get(definition.configKey());
            if (value != null) {
                fields.put(definition.snapshotField(), value);
            }
        }
    }

    public static GuiActionResult applyUpdate(MinecraftServer server, ServerPlayer player, Player2NpcTab tab,
                                              String action, String text) {
        if (!canManageGlobal(server, player)) {
            return GuiActionResult.rejected("op_required");
        }
        return resolveAction(tab, action, definition -> switch (definition.source()) {
            case PLAYER_ENGINE -> mapResult(PlayerEngineSettingsAdminService.update(
                    definition.configKey(),
                    definition.type() == GuiSettingType.BLOCK_POS ? "" : text,
                    player.blockPosition()));
            case PLAYER2_SERVER -> mapResult(Player2ServerConfigAdminService.update(
                    server, definition.configKey(), text));
        });
    }

    public static boolean canManageGlobal(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return false;
        }
        return player.createCommandSourceStack().hasPermission(2)
                || server.isSingleplayerOwner(player.getGameProfile());
    }

    public static boolean serverConfigLoadFailed() {
        return Player2ServerConfigHolder.freshLoadResult().loadFailed();
    }

    public static boolean canManageServerConfig(MinecraftServer server, ServerPlayer player) {
        return canManageGlobal(server, player) && !serverConfigLoadFailed();
    }

    static String settingsSchemaVersionForTest() {
        return SETTINGS_SCHEMA_VERSION;
    }

    static GuiActionResult resolveAction(
            Player2NpcTab tab,
            String action,
            SettingMutation mutation) {
        GuiSettingDefinition definition = findByAction(action);
        if (definition == null || tab == null || !definition.tabWireName().equals(tab.wireName())) {
            return GuiActionResult.rejected("unsupported_action");
        }
        return java.util.Objects.requireNonNull(mutation, "mutation").apply(definition);
    }

    private static GuiSettingDefinition findByAction(String action) {
        if (action == null) {
            return null;
        }
        for (GuiSettingDefinition definition : GuiSettingsCatalog.all()) {
            if (definition.action().equals(action)) {
                return definition;
            }
        }
        return null;
    }

    private static GuiActionResult mapResult(PlayerEngineSettingsAdminService.UpdateResult result) {
        return switch (result) {
            case OK -> GuiActionResult.ok();
            case INVALID_VALUE -> GuiActionResult.rejected("invalid_value");
            case UNSUPPORTED_SETTING -> GuiActionResult.rejected("unsupported_action");
            case SAVED_LIVE_PENDING -> GuiActionResult.rejected("saved_live_pending");
            case SAVED_LIVE_FAILED -> GuiActionResult.rejected("saved_live_apply_failed");
            case SAVE_FAILED -> GuiActionResult.rejected("save_failed");
        };
    }

    private static GuiActionResult mapResult(Player2ServerConfigAdminService.UpdateResult result) {
        return switch (result) {
            case OK -> GuiActionResult.ok();
            case INVALID_VALUE -> GuiActionResult.rejected("invalid_value");
            case UNSUPPORTED_SETTING -> GuiActionResult.rejected("unsupported_action");
            case LOAD_FAILED -> GuiActionResult.rejected("load_failed");
            case SAVED_LIVE_APPLY_FAILED -> GuiActionResult.rejected("saved_live_apply_failed");
            case SAVE_FAILED -> GuiActionResult.rejected("save_failed");
        };
    }
}
