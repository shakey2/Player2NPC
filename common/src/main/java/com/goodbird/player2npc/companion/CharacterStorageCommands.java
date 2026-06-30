package com.goodbird.player2npc.companion;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.player2.playerengine.help.ArgNote;
import com.player2.playerengine.help.HelpEntry;
import com.player2.playerengine.help.HelpRegistry;
import com.player2.playerengine.help.HelpRenderer;
import com.player2.playerengine.help.HelpfulCommand;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CharacterStorageCommands {

    private CharacterStorageCommands() {
    }

    /**
     * Registers {@code /player2npc} (level 0). Bot and user blacklists live here, not under {@code /playerengine}
     * (PlayerEngine's {@code playerengine} root requires level 2).
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerStorageHelpEntries();
        dispatcher.register(Commands.literal("player2npc").requires(s -> s.hasPermission(0))
                .then(helpLeaf())
                .then(BotBlacklistCommands.branch())
                .then(BotWhitelistCommands.branch())
                .then(UserBlacklistCommands.branch())
                .then(UserWhitelistCommands.branch())
                .then(UserSettingsCommands.branch())
                .then(BudgetCommands.branch())
                .then(Commands.literal("storage")
                        .then(Commands.literal("delete")
                                .then(Commands.literal("id").then(Commands.argument("id", StringArgumentType.greedyString())
                                        .executes(CharacterStorageCommands::deleteSelfById)))
                                .then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(CharacterStorageCommands::deleteSelfByName)))
                                .then(Commands.literal("short").then(Commands.argument("short", StringArgumentType.greedyString())
                                        .executes(CharacterStorageCommands::deleteSelfByShort)))
                                .then(Commands.literal("all").executes(CharacterStorageCommands::deleteSelfAll)))
                        .then(Commands.literal("op").requires(s -> s.hasPermission(2))
                                .then(Commands.literal("delete")
                                        .then(Commands.literal("id")
                                                .then(Commands.argument("id", StringArgumentType.string())
                                                        .then(Commands.literal("player").then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                                                .executes(CharacterStorageCommands::deleteOpById)))))
                                        .then(Commands.literal("name")
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .then(Commands.literal("player").then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                                                .executes(CharacterStorageCommands::deleteOpByName)))))
                                        .then(Commands.literal("short")
                                                .then(Commands.argument("short", StringArgumentType.greedyString())
                                                        .then(Commands.literal("player").then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                                                .executes(CharacterStorageCommands::deleteOpByShort)))))
                                        .then(Commands.literal("all")
                                                .then(Commands.literal("player").then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                                        .executes(CharacterStorageCommands::deleteOpAll)))))
                                .then(Commands.literal("list")
                                        .then(Commands.literal("player").then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                                .executes(CharacterStorageCommands::listOp)))
                                        .then(Commands.literal("uuid").then(Commands.argument("uuid", StringArgumentType.string())
                                                .executes(CharacterStorageCommands::listOpUuid)))))));
    }

    /**
     * The self-documenting {@code /player2npc help} leaf. {@code help [page]} renders the index;
     * {@code help <command> [page]} renders detail for one command. {@code <command>} is an English
     * {@link StringArgumentType#greedyString()} — never translated, never routed to a model surface.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> helpLeaf() {
        return HelpfulCommand.leaf("player2npc", "help", "/player2npc help [command] [page]",
                "help.player2npc.help.short", null,
                List.of(new ArgNote("command", "help.player2npc.help.arg.command"),
                        new ArgNote("page", "help.player2npc.help.arg.page")), 0, null, "general")
                .executes(ctx -> {
                    HelpRenderer.index("player2npc", 1, ctx.getSource());
                    return 1;
                })
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            HelpRenderer.index("player2npc", IntegerArgumentType.getInteger(ctx, "page"), ctx.getSource());
                            return 1;
                        }))
                .then(Commands.argument("command", StringArgumentType.greedyString())
                        .executes(CharacterStorageCommands::detailHelp));
    }

    /**
     * Detail handler for {@code help <command> [page]}. Splits an optional trailing integer page off
     * the greedy command string, then defers all rendering (and the bounded "unknown subcommand"
     * reply) to {@link HelpRenderer}, which never echoes the raw input into a model-facing surface.
     */
    private static int detailHelp(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "command").trim();
        int page = 1;
        int lastSpace = raw.lastIndexOf(' ');
        if (lastSpace > 0) {
            String tail = raw.substring(lastSpace + 1);
            try {
                int parsed = Integer.parseInt(tail);
                if (parsed >= 1) {
                    page = parsed;
                    raw = raw.substring(0, lastSpace).trim();
                }
            } catch (NumberFormatException ignored) {
                // No trailing page; treat the whole string as the command path.
            }
        }
        HelpRenderer.detail("player2npc", raw, page, ctx.getSource());
        return 1;
    }

    /**
     * Contributes a {@link HelpEntry} for every {@code /player2npc storage} leaf. The canonical paths
     * are the literal-only chains the {@code HelpCoverageVerifier} walks — the OP delete/list leaves
     * are the {@code player}/{@code uuid} literals nested below the {@code <id>}/{@code <name>}/
     * {@code <short>} argument nodes, so their paths end in those literals.
     */
    private static void registerStorageHelpEntries() {
        // Self-service storage (permission 0).
        HelpRegistry.register(new HelpEntry("player2npc", "storage delete id",
                "/player2npc storage delete id <id>", "help.player2npc.storage-delete-id.short", null,
                List.of(new ArgNote("id", "help.player2npc.storage-delete-id.arg.id")), 0, null, "storage"));
        HelpRegistry.register(new HelpEntry("player2npc", "storage delete name",
                "/player2npc storage delete name <name>", "help.player2npc.storage-delete-name.short", null,
                List.of(new ArgNote("name", "help.player2npc.storage-delete-name.arg.name")), 0, null, "storage"));
        HelpRegistry.register(new HelpEntry("player2npc", "storage delete short",
                "/player2npc storage delete short <short>", "help.player2npc.storage-delete-short.short", null,
                List.of(new ArgNote("short", "help.player2npc.storage-delete-short.arg.short")), 0, null, "storage"));
        HelpRegistry.register(new HelpEntry("player2npc", "storage delete all",
                "/player2npc storage delete all", "help.player2npc.storage-delete-all.short", null,
                List.of(), 0, null, "storage"));

        // Operator storage (permission 2).
        HelpRegistry.register(new HelpEntry("player2npc", "storage op delete id player",
                "/player2npc storage op delete id <id> player <targets>", "help.player2npc.storage-op-delete-id-player.short", null,
                List.of(new ArgNote("id", "help.player2npc.storage-op-delete-id-player.arg.id"),
                        new ArgNote("targets", "help.player2npc.storage-op-delete-id-player.arg.targets")), 2, null, "storage"));
        HelpRegistry.register(new HelpEntry("player2npc", "storage op delete name player",
                "/player2npc storage op delete name <name> player <targets>", "help.player2npc.storage-op-delete-name-player.short", null,
                List.of(new ArgNote("name", "help.player2npc.storage-op-delete-name-player.arg.name"),
                        new ArgNote("targets", "help.player2npc.storage-op-delete-name-player.arg.targets")), 2, null, "storage"));
        HelpRegistry.register(new HelpEntry("player2npc", "storage op delete short player",
                "/player2npc storage op delete short <short> player <targets>", "help.player2npc.storage-op-delete-short-player.short", null,
                List.of(new ArgNote("short", "help.player2npc.storage-op-delete-short-player.arg.short"),
                        new ArgNote("targets", "help.player2npc.storage-op-delete-short-player.arg.targets")), 2, null, "storage"));
        HelpRegistry.register(new HelpEntry("player2npc", "storage op delete all player",
                "/player2npc storage op delete all player <targets>", "help.player2npc.storage-op-delete-all-player.short", null,
                List.of(new ArgNote("targets", "help.player2npc.storage-op-delete-all-player.arg.targets")), 2, null, "storage"));
        HelpRegistry.register(new HelpEntry("player2npc", "storage op list player",
                "/player2npc storage op list player <targets>", "help.player2npc.storage-op-list-player.short", null,
                List.of(new ArgNote("targets", "help.player2npc.storage-op-list-player.arg.targets")), 2, null, "storage"));
        HelpRegistry.register(new HelpEntry("player2npc", "storage op list uuid",
                "/player2npc storage op list uuid <uuid>", "help.player2npc.storage-op-list-uuid.short", null,
                List.of(new ArgNote("uuid", "help.player2npc.storage-op-list-uuid.arg.uuid")), 2, null, "storage"));
    }

    private static ServerPlayer requireExecutorPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static UUID singleProfileUuid(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, arg);
        if (profiles.isEmpty()) {
            throw GameProfileArgument.ERROR_UNKNOWN_PLAYER.create();
        }
        return profiles.iterator().next().getId();
    }

    private static int deleteSelfById(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requireExecutorPlayer(ctx);
        String id = StringArgumentType.getString(ctx, "id").trim();
        if (id.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.storage.error.missing_id"));
            return 0;
        }
        return deleteForOwner(ctx.getSource(), player.getServer(), player.getUUID(), id);
    }

    private static int deleteSelfByName(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requireExecutorPlayer(ctx);
        String name = StringArgumentType.getString(ctx, "name").trim();
        return deleteByNameOrShort(ctx.getSource(), player.getServer(), player.getUUID(), name, true);
    }

    private static int deleteSelfByShort(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requireExecutorPlayer(ctx);
        String shortN = StringArgumentType.getString(ctx, "short").trim();
        return deleteByNameOrShort(ctx.getSource(), player.getServer(), player.getUUID(), shortN, false);
    }

    private static int deleteSelfAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requireExecutorPlayer(ctx);
        try {
            CharacterStorageOperations.dismissAllAutomatonsForOwner(player);
            CharacterStorageOperations.deleteAllCharacterData(player.getServer(), player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.storage.delete_self.success_all").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.storage.delete.error_io", e.getMessage()));
            return 0;
        }
    }

    private static int deleteOpById(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "id").trim();
        UUID owner = singleProfileUuid(ctx, "targets");
        return deleteForOwner(ctx.getSource(), ctx.getSource().getServer(), owner, id);
    }

    private static int deleteOpByName(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name").trim();
        UUID owner = singleProfileUuid(ctx, "targets");
        return deleteByNameOrShort(ctx.getSource(), ctx.getSource().getServer(), owner, name, true);
    }

    private static int deleteOpByShort(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String shortN = StringArgumentType.getString(ctx, "short").trim();
        UUID owner = singleProfileUuid(ctx, "targets");
        return deleteByNameOrShort(ctx.getSource(), ctx.getSource().getServer(), owner, shortN, false);
    }

    private static int deleteOpAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        UUID owner = singleProfileUuid(ctx, "targets");
        MinecraftServer server = ctx.getSource().getServer();
        try {
            ServerPlayer online = server.getPlayerList().getPlayer(owner);
            if (online != null) {
                CharacterStorageOperations.dismissAllAutomatonsForOwner(online);
            }
            CharacterStorageOperations.deleteAllCharacterData(server, owner);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.storage.delete_op.success_all", owner.toString()).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("command.player2npc.storage.delete.error_io", e.getMessage()));
            return 0;
        }
    }

    private static int deleteByNameOrShort(CommandSourceStack source, MinecraftServer server, UUID ownerUuid, String query, boolean fullName) {
        List<String> matches = fullName
                ? CharacterStorageOperations.findCharacterIdsByFullName(server, ownerUuid, query, false)
                : CharacterStorageOperations.findCharacterIdsByShortName(server, ownerUuid, query, false);
        if (matches.isEmpty()) {
            source.sendFailure(Component.translatable("command.player2npc.storage.delete.error_no_match"));
            return 0;
        }
        if (matches.size() > 1) {
            MutableComponent msg = Component.translatable("command.player2npc.storage.delete.error_ambiguous").withStyle(ChatFormatting.RED);
            for (String m : matches) {
                msg.append(Component.literal(m + " ").withStyle(ChatFormatting.YELLOW));
            }
            source.sendFailure(msg);
            return 0;
        }
        return deleteForOwner(source, server, ownerUuid, matches.get(0));
    }

    private static int deleteForOwner(CommandSourceStack source, MinecraftServer server, UUID ownerUuid, String characterId) {
        if (server == null) {
            source.sendFailure(Component.translatable("command.player2npc.storage.delete.error_no_server"));
            return 0;
        }
        boolean hadDir = java.nio.file.Files.isDirectory(CharacterStorageOperations.ownerDir(server, ownerUuid).resolve(characterId));
        CharacterStorageOperations.dismissLiveByCharacterId(server, ownerUuid, characterId);
        try {
            if (hadDir) {
                CharacterStorageOperations.deleteCharacterData(server, ownerUuid, characterId);
            }
            if (hadDir) {
                source.sendSuccess(() -> Component.translatable("command.player2npc.storage.delete.success_by_id", characterId).withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.translatable("command.player2npc.storage.delete.success_no_data").withStyle(ChatFormatting.YELLOW), false);
            }
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.translatable("command.player2npc.storage.delete.error_io", e.getMessage()));
            return 0;
        }
    }

    private static int listOp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        UUID owner = singleProfileUuid(ctx, "targets");
        return sendList(ctx.getSource(), ctx.getSource().getServer(), owner);
    }

    private static int listOpUuid(CommandContext<CommandSourceStack> ctx) {
        String u = StringArgumentType.getString(ctx, "uuid").trim();
        return CharacterStorageOperations.parseUuidLenient(u)
                .map(uuid -> sendList(ctx.getSource(), ctx.getSource().getServer(), uuid))
                .orElseGet(() -> {
                    ctx.getSource().sendFailure(Component.translatable("command.player2npc.storage.list.error_invalid_uuid"));
                    return 0;
                });
    }

    private static int sendList(CommandSourceStack source, MinecraftServer server, UUID ownerUuid) {
        List<CharacterStorageOperations.StoredCharacterRow> rows = CharacterStorageOperations.listStoredCharacters(server, ownerUuid);
        long totalBytes = CharacterStorageOperations.directorySizeBytes(CharacterStorageOperations.ownerDir(server, ownerUuid));
        MutableComponent header = Component.translatable("command.player2npc.storage.list.header").withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD);
        header.append(Component.literal(String.valueOf(rows.size())).withStyle(ChatFormatting.AQUA));
        header.append(Component.translatable("command.player2npc.storage.list.header_disk", ownerUuid.toString()).withStyle(ChatFormatting.GRAY));
        header.append(Component.literal(formatBytes(totalBytes)).withStyle(ChatFormatting.AQUA));
        source.sendSuccess(() -> header, false);
        for (CharacterStorageOperations.StoredCharacterRow row : rows) {
            source.sendSuccess(() -> Component.translatable("command.player2npc.storage.list.row",
                    row.characterId(), row.fullName(), row.shortName(), formatBytes(row.bytesOnDisk()))
                    .withStyle(ChatFormatting.WHITE), false);
        }
        if (rows.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.player2npc.storage.list.empty").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        exp = Math.min(Math.max(exp, 1), 6);
        char pre = "KMGTPE".charAt(exp - 1);
        double val = bytes / Math.pow(1024, exp);
        return String.format(Locale.ROOT, "%.1f %ciB", val, pre);
    }
}
