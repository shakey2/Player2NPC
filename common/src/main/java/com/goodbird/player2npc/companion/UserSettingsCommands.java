package com.goodbird.player2npc.companion;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.player2.playerengine.help.HelpEntry;
import com.player2.playerengine.help.HelpRegistry;
import com.player2.playerengine.player2api.BotLifecycleSettings;
import com.player2.playerengine.player2api.BotLifecycleSettingsResolver;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import java.io.IOException;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class UserSettingsCommands {

    private UserSettingsCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        registerHelpEntries();
        return Commands.literal("user-settings")
                .then(Commands.literal("show").executes(UserSettingsCommands::show))
                .then(Commands.literal("auto-equip")
                        .then(Commands.literal("on").executes(ctx -> setAutoEquip(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setAutoEquip(ctx, false))))
                .then(Commands.literal("userlistmode")
                        .then(Commands.literal("blacklist").executes(ctx -> setUserMode(ctx, OwnerUserSettingsStorage.ListMode.BLACKLIST)))
                        .then(Commands.literal("whitelist").executes(ctx -> setUserMode(ctx, OwnerUserSettingsStorage.ListMode.WHITELIST))))
                .then(Commands.literal("botlistmode")
                        .then(Commands.literal("blacklist").executes(ctx -> setBotMode(ctx, OwnerUserSettingsStorage.ListMode.BLACKLIST)))
                        .then(Commands.literal("whitelist").executes(ctx -> setBotMode(ctx, OwnerUserSettingsStorage.ListMode.WHITELIST))))
                .then(Commands.literal("auto-respawn")
                        .then(Commands.literal("on").executes(ctx -> setAutoRespawn(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setAutoRespawn(ctx, false))))
                .then(Commands.literal("bot-permadeath")
                        .then(Commands.literal("on").executes(ctx -> setBotPermadeath(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setBotPermadeath(ctx, false))))
                // OP-gated (level 2): server-wide override toggle. When ON, the server config wins over
                // every player's per-player toggle (Non-negotiable #5). Open question #5: routed here.
                .then(Commands.literal("server-override")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("on").executes(ctx -> setServerOverride(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setServerOverride(ctx, false))));
    }

    /**
     * Contributes a {@link HelpEntry} for every {@code /player2npc user-settings} leaf. The
     * {@code auto-respawn}/{@code bot-permadeath} on/off leaves stay {@code permLevel=0} with a
     * {@code permNoteKey} describing the dedicated + server-override caveat (Non-negotiable #7);
     * {@code server-override} on/off is {@code permLevel=2}.
     */
    private static void registerHelpEntries() {
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings show",
                "/player2npc user-settings show", "help.player2npc.user-settings-show.short", null,
                List.of(), 0, null, "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings auto-equip on",
                "/player2npc user-settings auto-equip on", "help.player2npc.user-settings-auto-equip-on.short", null,
                List.of(), 0, null, "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings auto-equip off",
                "/player2npc user-settings auto-equip off", "help.player2npc.user-settings-auto-equip-off.short", null,
                List.of(), 0, null, "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings userlistmode blacklist",
                "/player2npc user-settings userlistmode blacklist", "help.player2npc.user-settings-userlistmode-blacklist.short", null,
                List.of(), 0, null, "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings userlistmode whitelist",
                "/player2npc user-settings userlistmode whitelist", "help.player2npc.user-settings-userlistmode-whitelist.short", null,
                List.of(), 0, null, "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings botlistmode blacklist",
                "/player2npc user-settings botlistmode blacklist", "help.player2npc.user-settings-botlistmode-blacklist.short", null,
                List.of(), 0, null, "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings botlistmode whitelist",
                "/player2npc user-settings botlistmode whitelist", "help.player2npc.user-settings-botlistmode-whitelist.short", null,
                List.of(), 0, null, "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings auto-respawn on",
                "/player2npc user-settings auto-respawn on", "help.player2npc.user-settings-auto-respawn-on.short", null,
                List.of(), 0, "help.player2npc.user-settings-auto-respawn-on.perm", "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings auto-respawn off",
                "/player2npc user-settings auto-respawn off", "help.player2npc.user-settings-auto-respawn-off.short", null,
                List.of(), 0, "help.player2npc.user-settings-auto-respawn-off.perm", "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings bot-permadeath on",
                "/player2npc user-settings bot-permadeath on", "help.player2npc.user-settings-bot-permadeath-on.short", null,
                List.of(), 0, "help.player2npc.user-settings-bot-permadeath-on.perm", "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings bot-permadeath off",
                "/player2npc user-settings bot-permadeath off", "help.player2npc.user-settings-bot-permadeath-off.short", null,
                List.of(), 0, "help.player2npc.user-settings-bot-permadeath-off.perm", "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings server-override on",
                "/player2npc user-settings server-override on", "help.player2npc.user-settings-server-override-on.short", null,
                List.of(), 2, null, "settings"));
        HelpRegistry.register(new HelpEntry("player2npc", "user-settings server-override off",
                "/player2npc user-settings server-override off", "help.player2npc.user-settings-server-override-off.short", null,
                List.of(), 2, null, "settings"));
    }

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        OwnerUserSettingsStorage.Snapshot s = OwnerUserSettingsStorage.load(server, self.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.user_settings.show.user_list_mode_label").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(s.userListMode().toJson()).withStyle(ChatFormatting.YELLOW)), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.user_settings.show.bot_list_mode_label").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(s.botListMode().toJson()).withStyle(ChatFormatting.YELLOW)), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.user_settings.show.auto_equip_label").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(String.valueOf(s.autoEquipArmor())).withStyle(ChatFormatting.YELLOW)), false);

        // Bot lifecycle: print RESOLVED effective values (through the single resolver, so show can
        // never disagree with enforcement) plus the routing source.
        boolean usesServer = BotLifecycleSettingsResolver.usesServerConfig(server);
        boolean dedicated = server != null && server.isDedicatedServer();
        boolean overrideOn = Player2ServerConfigHolder.get().isServerOverridesPlayerConfig();
        BotLifecycleSettings effective = BotLifecycleSettingsResolver.resolve(
                server, self.getUUID(), s.autoRespawn(), s.botPermadeath());
        boolean hardcore = server != null && server.isHardcore();
        // Whether the effective permadeath came from an UNSET value falling back to the world hardcore
        // flag (Model A). The "chosen" value depends on routing: server config field vs per-player snapshot.
        Boolean chosenPermadeath = usesServer
                ? Player2ServerConfigHolder.get().getServerBotPermadeath()
                : s.botPermadeath();
        Component permadeathDetailComp = chosenPermadeath == null
                ? (hardcore
                        ? Component.translatable("command.player2npc.user_settings.show.permadeath_detail_hardcore").withStyle(ChatFormatting.YELLOW)
                        : Component.translatable("command.player2npc.user_settings.show.permadeath_detail_unset").withStyle(ChatFormatting.YELLOW))
                : Component.empty();

        Component sourceComp = (usesServer
                ? (dedicated
                        ? Component.translatable("command.player2npc.user_settings.show.source_server_override")
                        : Component.translatable("command.player2npc.user_settings.show.source_server_sp_lan"))
                : Component.translatable("command.player2npc.user_settings.show.source_per_player"))
                .withStyle(ChatFormatting.AQUA);

        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.user_settings.show.auto_respawn_label").withStyle(ChatFormatting.GOLD)
                .append((effective.autoRespawn()
                        ? Component.translatable("command.player2npc.user_settings.on")
                        : Component.translatable("command.player2npc.user_settings.off"))
                        .withStyle(ChatFormatting.YELLOW)), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.user_settings.show.bot_permadeath_label").withStyle(ChatFormatting.GOLD)
                .append((effective.botPermadeath()
                        ? Component.translatable("command.player2npc.user_settings.on")
                        : Component.translatable("command.player2npc.user_settings.off"))
                        .withStyle(ChatFormatting.YELLOW)
                        .append(permadeathDetailComp)), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.user_settings.show.source_label").withStyle(ChatFormatting.GOLD)
                .append(sourceComp), false);
        if (dedicated && overrideOn) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.player2npc.user_settings.show.server_override_note")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int setAutoEquip(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        OwnerUserSettingsStorage.Snapshot cur = OwnerUserSettingsStorage.load(server, self.getUUID());
        OwnerUserSettingsStorage.Snapshot next = new OwnerUserSettingsStorage.Snapshot(
                cur.userListMode(), cur.botListMode(), enabled, cur.autoRespawn(), cur.botPermadeath());
        try {
            OwnerUserSettingsStorage.saveSnapshot(server, self.getUUID(), next);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.user_settings.auto_equip_set", enabled).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.common.save_failed", e.getMessage()));
            return 0;
        }
    }

    private static int setUserMode(CommandContext<CommandSourceStack> ctx, OwnerUserSettingsStorage.ListMode mode) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        OwnerUserSettingsStorage.Snapshot cur = OwnerUserSettingsStorage.load(server, self.getUUID());
        OwnerUserSettingsStorage.Snapshot next = new OwnerUserSettingsStorage.Snapshot(mode, cur.botListMode(), cur.autoEquipArmor(), cur.autoRespawn(), cur.botPermadeath());
        try {
            OwnerUserSettingsStorage.saveSnapshot(server, self.getUUID(), next);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.user_settings.user_list_mode_set", mode.toJson()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.common.save_failed", e.getMessage()));
            return 0;
        }
    }

    private static int setBotMode(CommandContext<CommandSourceStack> ctx, OwnerUserSettingsStorage.ListMode mode) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        OwnerUserSettingsStorage.Snapshot cur = OwnerUserSettingsStorage.load(server, self.getUUID());
        OwnerUserSettingsStorage.Snapshot next = new OwnerUserSettingsStorage.Snapshot(cur.userListMode(), mode, cur.autoEquipArmor(), cur.autoRespawn(), cur.botPermadeath());
        try {
            OwnerUserSettingsStorage.saveSnapshot(server, self.getUUID(), next);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.user_settings.bot_list_mode_set", mode.toJson()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.common.save_failed", e.getMessage()));
            return 0;
        }
    }

    private static int setAutoRespawn(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        // Route via the single resolver (NOT BudgetCommands.useServerConfig, which is private):
        // SP / LAN / dedicated+override-ON -> server config; dedicated+override-OFF -> per-player.
        if (BotLifecycleSettingsResolver.usesServerConfig(server)) {
            // Writing the server-level value on a DEDICATED server (override ON) edits shared, all-player
            // config — that is OP-only (level 2) per Non-negotiable #5 / WS7. Singleplayer / LAN (non-dedicated)
            // continues to allow level-0 writes, since there the server config IS the local player's config.
            if (server != null && server.isDedicatedServer() && !ctx.getSource().hasPermission(2)) {
                ctx.getSource().sendFailure(Component.translatable("command.player2npc.user_settings.server_lifecycle_op_required"));
                return 0;
            }
            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            cfg.setServerAutoRespawn(enabled);
            Player2ServerConfigHolder.save();
            boolean overridden = server != null && server.isDedicatedServer();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    overridden ? "command.player2npc.user_settings.auto_respawn_set_server_override"
                               : "command.player2npc.user_settings.auto_respawn_set_server",
                    Component.translatable(enabled ? "command.player2npc.user_settings.on"
                                                   : "command.player2npc.user_settings.off"))
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        OwnerUserSettingsStorage.Snapshot cur = OwnerUserSettingsStorage.load(server, self.getUUID());
        OwnerUserSettingsStorage.Snapshot next = new OwnerUserSettingsStorage.Snapshot(
                cur.userListMode(), cur.botListMode(), cur.autoEquipArmor(), enabled, cur.botPermadeath());
        try {
            OwnerUserSettingsStorage.saveSnapshot(server, self.getUUID(), next);
            boolean overrideOn = Player2ServerConfigHolder.get().isServerOverridesPlayerConfig();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    overrideOn ? "command.player2npc.user_settings.auto_respawn_set_player_override_on"
                               : "command.player2npc.user_settings.auto_respawn_set_player",
                    Component.translatable(enabled ? "command.player2npc.user_settings.on"
                                                   : "command.player2npc.user_settings.off"))
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.common.save_failed", e.getMessage()));
            return 0;
        }
    }

    private static int setBotPermadeath(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        // The player explicitly chose, so write an explicit Boolean (never null) — this makes OFF
        // honorable even in hardcore (Model A, Non-negotiable #6).
        Boolean value = enabled ? Boolean.TRUE : Boolean.FALSE;
        if (BotLifecycleSettingsResolver.usesServerConfig(server)) {
            // Writing the server-level value on a DEDICATED server (override ON) edits shared, all-player
            // config — that is OP-only (level 2) per Non-negotiable #5 / WS7. Singleplayer / LAN (non-dedicated)
            // continues to allow level-0 writes, since there the server config IS the local player's config.
            if (server != null && server.isDedicatedServer() && !ctx.getSource().hasPermission(2)) {
                ctx.getSource().sendFailure(Component.translatable("command.player2npc.user_settings.server_lifecycle_op_required"));
                return 0;
            }
            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            cfg.setServerBotPermadeath(value);
            Player2ServerConfigHolder.save();
            boolean overridden = server != null && server.isDedicatedServer();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    overridden ? "command.player2npc.user_settings.bot_permadeath_set_server_override"
                               : "command.player2npc.user_settings.bot_permadeath_set_server",
                    Component.translatable(enabled ? "command.player2npc.user_settings.on"
                                                   : "command.player2npc.user_settings.off"))
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        OwnerUserSettingsStorage.Snapshot cur = OwnerUserSettingsStorage.load(server, self.getUUID());
        OwnerUserSettingsStorage.Snapshot next = new OwnerUserSettingsStorage.Snapshot(
                cur.userListMode(), cur.botListMode(), cur.autoEquipArmor(), cur.autoRespawn(), value);
        try {
            OwnerUserSettingsStorage.saveSnapshot(server, self.getUUID(), next);
            boolean overrideOn = Player2ServerConfigHolder.get().isServerOverridesPlayerConfig();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    overrideOn ? "command.player2npc.user_settings.bot_permadeath_set_player_override_on"
                               : "command.player2npc.user_settings.bot_permadeath_set_player",
                    Component.translatable(enabled ? "command.player2npc.user_settings.on"
                                                   : "command.player2npc.user_settings.off"))
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.common.save_failed", e.getMessage()));
            return 0;
        }
    }

    /** OP-gated (level 2): toggles whether the server config overrides every player's per-player config. */
    private static int setServerOverride(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
        cfg.setServerOverridesPlayerConfig(enabled);
        Player2ServerConfigHolder.save();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "command.player2npc.user_settings.server_override_set_on"
                        : "command.player2npc.user_settings.server_override_set_off")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
