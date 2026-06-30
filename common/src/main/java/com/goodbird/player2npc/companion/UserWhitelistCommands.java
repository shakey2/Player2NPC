package com.goodbird.player2npc.companion;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.player2.playerengine.help.ArgNote;
import com.player2.playerengine.help.HelpEntry;
import com.player2.playerengine.help.HelpRegistry;
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
 * Self-service companion user whitelist under {@code /player2npc userwhitelist} (permission 0).
 */
public final class UserWhitelistCommands {

    private UserWhitelistCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        HelpRegistry.register(new HelpEntry("player2npc", "userwhitelist list",
                "/player2npc userwhitelist list", "help.player2npc.userwhitelist-list.short", null,
                List.of(), 0, null, "lists"));
        HelpRegistry.register(new HelpEntry("player2npc", "userwhitelist add",
                "/player2npc userwhitelist add <username>", "help.player2npc.userwhitelist-add.short", null,
                List.of(new ArgNote("username", "help.player2npc.userwhitelist-add.arg.username")), 0, null, "lists"));
        HelpRegistry.register(new HelpEntry("player2npc", "userwhitelist remove",
                "/player2npc userwhitelist remove <username>", "help.player2npc.userwhitelist-remove.short", null,
                List.of(new ArgNote("username", "help.player2npc.userwhitelist-remove.arg.username")), 0, null, "lists"));
        return Commands.literal("userwhitelist")
                .then(Commands.literal("list").executes(UserWhitelistCommands::listEntries))
                .then(Commands.literal("add")
                        .then(Commands.argument("username", StringArgumentType.string())
                                .executes(UserWhitelistCommands::addUser)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("username", StringArgumentType.string())
                                .executes(UserWhitelistCommands::removeUser)));
    }

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int listEntries(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        List<UserWhitelistStorage.Entry> entries = UserWhitelistStorage.load(server, self.getUUID());
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.userwhitelist.list_empty").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.userwhitelist.list_header", entries.size()).withStyle(ChatFormatting.GOLD), false);
        for (UserWhitelistStorage.Entry e : entries) {
            ctx.getSource().sendSuccess(() -> (e.targetUuid() != null
                    ? Component.translatable("command.player2npc.userwhitelist.entry_with_uuid", e.targetUsername(), e.targetUuid())
                    : Component.translatable("command.player2npc.userwhitelist.entry", e.targetUsername()))
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
        UserWhitelistStorage.Entry entry = new UserWhitelistStorage.Entry(username, targetUuid.map(UUID::toString).orElse(null));
        List<UserWhitelistStorage.Entry> list = new ArrayList<>(UserWhitelistStorage.load(server, self.getUUID()));
        list.removeIf(e -> UserWhitelistStorage.key(e).equals(UserWhitelistStorage.key(entry)));
        list.add(entry);
        try {
            UserWhitelistStorage.save(server, self.getUUID(), list);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.userwhitelist.add_success").withStyle(ChatFormatting.GREEN), false);
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
        UserWhitelistStorage.Entry probe = new UserWhitelistStorage.Entry(username, null);
        List<UserWhitelistStorage.Entry> list = new ArrayList<>(UserWhitelistStorage.load(server, self.getUUID()));
        boolean removed = list.removeIf(e -> UserWhitelistStorage.key(e).equals(UserWhitelistStorage.key(probe)));
        if (!removed) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.userwhitelist.remove_not_found"));
            return 0;
        }
        try {
            UserWhitelistStorage.save(server, self.getUUID(), list);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.userwhitelist.remove_success").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.common.save_failed", e.getMessage()));
            return 0;
        }
    }
}
