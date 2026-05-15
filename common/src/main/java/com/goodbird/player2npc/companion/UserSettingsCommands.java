package com.goodbird.player2npc.companion;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
                .then(Commands.literal("userlistmode")
                        .then(Commands.literal("blacklist").executes(ctx -> setUserMode(ctx, OwnerUserSettingsStorage.ListMode.BLACKLIST)))
                        .then(Commands.literal("whitelist").executes(ctx -> setUserMode(ctx, OwnerUserSettingsStorage.ListMode.WHITELIST))))
                .then(Commands.literal("botlistmode")
                        .then(Commands.literal("blacklist").executes(ctx -> setBotMode(ctx, OwnerUserSettingsStorage.ListMode.BLACKLIST)))
                        .then(Commands.literal("whitelist").executes(ctx -> setBotMode(ctx, OwnerUserSettingsStorage.ListMode.WHITELIST))));
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
        return 1;
    }

    private static int setUserMode(CommandContext<CommandSourceStack> ctx, OwnerUserSettingsStorage.ListMode mode) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        OwnerUserSettingsStorage.Snapshot cur = OwnerUserSettingsStorage.load(server, self.getUUID());
        OwnerUserSettingsStorage.Snapshot next = new OwnerUserSettingsStorage.Snapshot(mode, cur.botListMode());
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
        OwnerUserSettingsStorage.Snapshot next = new OwnerUserSettingsStorage.Snapshot(cur.userListMode(), mode);
        try {
            OwnerUserSettingsStorage.saveSnapshot(server, self.getUUID(), next);
            ctx.getSource().sendSuccess(() -> Component.literal("botListMode set to " + mode.toJson()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Save failed: " + e.getMessage()));
            return 0;
        }
    }
}
