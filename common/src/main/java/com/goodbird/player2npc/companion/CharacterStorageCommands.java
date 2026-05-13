package com.goodbird.player2npc.companion;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("player2npc").requires(s -> s.hasPermission(0))
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
            ctx.getSource().sendFailure(Component.literal("Missing character id."));
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
            ctx.getSource().sendSuccess(() -> Component.literal("Deleted all stored character data for you.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Delete failed: " + e.getMessage()));
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
            ctx.getSource().sendSuccess(() -> Component.literal("Deleted all stored character data for " + owner + ".").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Delete failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int deleteByNameOrShort(CommandSourceStack source, MinecraftServer server, UUID ownerUuid, String query, boolean fullName) {
        List<String> matches = fullName
                ? CharacterStorageOperations.findCharacterIdsByFullName(server, ownerUuid, query, false)
                : CharacterStorageOperations.findCharacterIdsByShortName(server, ownerUuid, query, false);
        if (matches.isEmpty()) {
            source.sendFailure(Component.literal("No stored character matched."));
            return 0;
        }
        if (matches.size() > 1) {
            MutableComponent msg = Component.literal("Ambiguous match; character ids: ").withStyle(ChatFormatting.RED);
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
            source.sendFailure(Component.literal("No server."));
            return 0;
        }
        boolean hadDir = java.nio.file.Files.isDirectory(CharacterStorageOperations.ownerDir(server, ownerUuid).resolve(characterId));
        CharacterStorageOperations.dismissLiveByCharacterId(server, ownerUuid, characterId);
        try {
            if (hadDir) {
                CharacterStorageOperations.deleteCharacterData(server, ownerUuid, characterId);
            }
            if (hadDir) {
                source.sendSuccess(() -> Component.literal("Deleted storage for character id: " + characterId).withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.literal("Removed live companion if present; no data folder for that id.").withStyle(ChatFormatting.YELLOW), false);
            }
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Delete failed: " + e.getMessage()));
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
                    ctx.getSource().sendFailure(Component.literal("Invalid UUID."));
                    return 0;
                });
    }

    private static int sendList(CommandSourceStack source, MinecraftServer server, UUID ownerUuid) {
        List<CharacterStorageOperations.StoredCharacterRow> rows = CharacterStorageOperations.listStoredCharacters(server, ownerUuid);
        long totalBytes = CharacterStorageOperations.directorySizeBytes(CharacterStorageOperations.ownerDir(server, ownerUuid));
        MutableComponent header = Component.literal("Stored characters: ").withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD);
        header.append(Component.literal(String.valueOf(rows.size())).withStyle(ChatFormatting.AQUA));
        header.append(Component.literal("  Total disk (owners/" + ownerUuid + "): ").withStyle(ChatFormatting.GRAY));
        header.append(Component.literal(formatBytes(totalBytes)).withStyle(ChatFormatting.AQUA));
        source.sendSuccess(() -> header, false);
        for (CharacterStorageOperations.StoredCharacterRow row : rows) {
            String line = String.format(Locale.ROOT, "%s | %s | %s | %s",
                    row.characterId(), row.fullName(), row.shortName(), formatBytes(row.bytesOnDisk()));
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
        }
        if (rows.isEmpty()) {
            source.sendSuccess(() -> Component.literal("(no character folders)").withStyle(ChatFormatting.DARK_GRAY), false);
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
