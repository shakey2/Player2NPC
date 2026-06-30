package com.goodbird.player2npc.companion;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Self-service companion user blacklist under {@code /player2npc userblacklist} (permission 0).
 * Not registered under {@code /playerengine}: that tree requires OP level 2 for the whole root in PlayerEngine.
 */
public final class UserBlacklistCommands {

    private UserBlacklistCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("userblacklist")
                .then(Commands.literal("list").executes(UserBlacklistCommands::listEntries))
                .then(Commands.literal("add")
                        .then(Commands.argument("username", StringArgumentType.string())
                                .executes(UserBlacklistCommands::addUser)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("username", StringArgumentType.string())
                                .executes(UserBlacklistCommands::removeUser)));
    }

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int listEntries(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        List<UserBlacklistStorage.Entry> entries = UserBlacklistStorage.load(server, self.getUUID());
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.userblacklist.list_empty").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.userblacklist.list_header", entries.size()).withStyle(ChatFormatting.GOLD), false);
        for (UserBlacklistStorage.Entry e : entries) {
            ctx.getSource().sendSuccess(() -> (e.targetUuid() != null
                    ? Component.translatable("command.player2npc.userblacklist.entry_with_uuid", e.targetUsername(), e.targetUuid())
                    : Component.translatable("command.player2npc.userblacklist.entry", e.targetUsername()))
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int addUser(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        String username = StringArgumentType.getString(ctx, "username").trim();
        if (username.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.common.missing_username"));
            return 0;
        }
        Optional<UUID> targetUuid = BotBlacklistStorage.resolveTargetUuid(server, username);
        UserBlacklistStorage.Entry entry = new UserBlacklistStorage.Entry(username, targetUuid.map(UUID::toString).orElse(null));
        List<UserBlacklistStorage.Entry> list = new ArrayList<>(UserBlacklistStorage.load(server, self.getUUID()));
        list.removeIf(e -> UserBlacklistStorage.key(e).equals(UserBlacklistStorage.key(entry)));
        list.add(entry);
        try {
            UserBlacklistStorage.save(server, self.getUUID(), list);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.userblacklist.add_success").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.common.save_failed", e.getMessage()));
            return 0;
        }
    }

    private static int removeUser(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        String username = StringArgumentType.getString(ctx, "username").trim();
        if (username.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.common.missing_username"));
            return 0;
        }
        UserBlacklistStorage.Entry probe = new UserBlacklistStorage.Entry(username, null);
        List<UserBlacklistStorage.Entry> list = new ArrayList<>(UserBlacklistStorage.load(server, self.getUUID()));
        boolean removed = list.removeIf(e -> UserBlacklistStorage.key(e).equals(UserBlacklistStorage.key(probe)));
        if (!removed) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.userblacklist.remove_not_found"));
            return 0;
        }
        try {
            UserBlacklistStorage.save(server, self.getUUID(), list);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.userblacklist.remove_success").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.common.save_failed", e.getMessage()));
            return 0;
        }
    }
}
