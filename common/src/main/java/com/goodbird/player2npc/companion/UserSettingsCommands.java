package com.goodbird.player2npc.companion;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.player2.playerengine.player2api.BotLifecycleSettings;
import com.player2.playerengine.player2api.BotLifecycleSettingsResolver;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import java.io.IOException;
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

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        OwnerUserSettingsStorage.Snapshot s = OwnerUserSettingsStorage.load(server, self.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("userListMode: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(s.userListMode().toJson()).withStyle(ChatFormatting.YELLOW)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("botListMode: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(s.botListMode().toJson()).withStyle(ChatFormatting.YELLOW)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("autoEquipArmor (armor + weapon auto-equip): ").withStyle(ChatFormatting.GOLD)
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
        String permadeathDetail = chosenPermadeath == null
                ? (hardcore ? " (hardcore default)" : " (default — unset)")
                : "";

        String source;
        if (usesServer) {
            source = dedicated
                    ? "server config (server override ON — your personal setting is not in effect)"
                    : "server config (singleplayer / LAN)";
        } else {
            source = "your per-player config";
        }
        ctx.getSource().sendSuccess(() -> Component.literal("auto-respawn (effective): ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(effective.autoRespawn() ? "ON" : "OFF").withStyle(ChatFormatting.YELLOW)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("bot-permadeath (effective): ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal((effective.botPermadeath() ? "ON" : "OFF") + permadeathDetail).withStyle(ChatFormatting.YELLOW)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("source: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(source).withStyle(ChatFormatting.AQUA)), false);
        if (dedicated && overrideOn) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Note: server override is ON; your personal auto-respawn / bot-permadeath settings are currently not in effect.")
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
            ctx.getSource().sendSuccess(() -> Component.literal("autoEquipArmor set to " + enabled + " (armor and main-hand weapon pickup auto-equip)").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Save failed: " + e.getMessage()));
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
            ctx.getSource().sendSuccess(() -> Component.literal("userListMode set to " + mode.toJson()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Save failed: " + e.getMessage()));
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
            ctx.getSource().sendSuccess(() -> Component.literal("botListMode set to " + mode.toJson()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Save failed: " + e.getMessage()));
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
                ctx.getSource().sendFailure(Component.literal(
                        "Editing server-level lifecycle settings on a dedicated server requires operator permission (level 2)."));
                return 0;
            }
            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            cfg.setServerAutoRespawn(enabled);
            Player2ServerConfigHolder.save();
            boolean overridden = server != null && server.isDedicatedServer();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "auto-respawn set to " + (enabled ? "ON" : "OFF") + " (server config"
                            + (overridden ? ", in effect via server override)" : ")"))
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        OwnerUserSettingsStorage.Snapshot cur = OwnerUserSettingsStorage.load(server, self.getUUID());
        OwnerUserSettingsStorage.Snapshot next = new OwnerUserSettingsStorage.Snapshot(
                cur.userListMode(), cur.botListMode(), cur.autoEquipArmor(), enabled, cur.botPermadeath());
        try {
            OwnerUserSettingsStorage.saveSnapshot(server, self.getUUID(), next);
            boolean overrideOn = Player2ServerConfigHolder.get().isServerOverridesPlayerConfig();
            ctx.getSource().sendSuccess(() -> Component.literal("auto-respawn set to " + (enabled ? "ON" : "OFF")
                    + " (your per-player config)" + (overrideOn ? " — but server override is ON, so it is not currently in effect." : ""))
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Save failed: " + e.getMessage()));
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
                ctx.getSource().sendFailure(Component.literal(
                        "Editing server-level lifecycle settings on a dedicated server requires operator permission (level 2)."));
                return 0;
            }
            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            cfg.setServerBotPermadeath(value);
            Player2ServerConfigHolder.save();
            boolean overridden = server != null && server.isDedicatedServer();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "bot-permadeath set to " + (enabled ? "ON" : "OFF") + " (server config"
                            + (overridden ? ", in effect via server override)" : ")"))
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        OwnerUserSettingsStorage.Snapshot cur = OwnerUserSettingsStorage.load(server, self.getUUID());
        OwnerUserSettingsStorage.Snapshot next = new OwnerUserSettingsStorage.Snapshot(
                cur.userListMode(), cur.botListMode(), cur.autoEquipArmor(), cur.autoRespawn(), value);
        try {
            OwnerUserSettingsStorage.saveSnapshot(server, self.getUUID(), next);
            boolean overrideOn = Player2ServerConfigHolder.get().isServerOverridesPlayerConfig();
            ctx.getSource().sendSuccess(() -> Component.literal("bot-permadeath set to " + (enabled ? "ON" : "OFF")
                    + " (your per-player config)" + (overrideOn ? " — but server override is ON, so it is not currently in effect." : ""))
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Save failed: " + e.getMessage()));
            return 0;
        }
    }

    /** OP-gated (level 2): toggles whether the server config overrides every player's per-player config. */
    private static int setServerOverride(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
        cfg.setServerOverridesPlayerConfig(enabled);
        Player2ServerConfigHolder.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "server-override set to " + (enabled ? "ON (server config wins)" : "OFF (per-player config applies on dedicated servers)"))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
