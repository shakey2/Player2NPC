package com.goodbird.player2npc.companion;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class BotBlacklistCommands {

    private BotBlacklistCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("botblacklist")
                .then(Commands.literal("list").executes(BotBlacklistCommands::listEntries))
                .then(Commands.literal("add")
                        .then(Commands.argument("username", StringArgumentType.string())
                                .executes(BotBlacklistCommands::addAllBots)
                                .then(Commands.argument("character", StringArgumentType.greedyString())
                                        .executes(BotBlacklistCommands::addCharacter))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("username", StringArgumentType.string())
                                .executes(BotBlacklistCommands::removeAllBots)
                                .then(Commands.argument("character", StringArgumentType.greedyString())
                                        .executes(BotBlacklistCommands::removeCharacter))));
    }

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int listEntries(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        List<BotBlacklistStorage.Entry> entries = BotBlacklistStorage.load(server, self.getUUID());
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Your bot blacklist is empty.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Your bot blacklist (" + entries.size() + " entries):").withStyle(ChatFormatting.GOLD), false);
        for (BotBlacklistStorage.Entry e : entries) {
            StringBuilder sb = new StringBuilder();
            sb.append("- ").append(e.targetUsername());
            if (e.targetUuid() != null) {
                sb.append(" [").append(e.targetUuid()).append("]");
            }
            if (e.allBots()) {
                sb.append(" (all bots)");
            } else {
                sb.append(" (character: ");
                if (e.characterId() != null) {
                    sb.append("id=").append(e.characterId());
                }
                if (e.characterName() != null) {
                    if (e.characterId() != null) {
                        sb.append(", ");
                    }
                    sb.append("name=").append(e.characterName());
                }
                sb.append(")");
            }
            String line = sb.toString();
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int addAllBots(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        String username = StringArgumentType.getString(ctx, "username").trim();
        if (username.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Missing username."));
            return 0;
        }
        Optional<UUID> targetUuid = BotBlacklistStorage.resolveTargetUuid(ctx.getSource().getServer(), username);
        BotBlacklistStorage.Entry entry = new BotBlacklistStorage.Entry(username, targetUuid.map(UUID::toString).orElse(null), true, null, null);
        return addEntry(ctx, self, entry);
    }

    private static int addCharacter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        String username = StringArgumentType.getString(ctx, "username").trim();
        String characterRaw = StringArgumentType.getString(ctx, "character").trim();
        if (username.isEmpty() || characterRaw.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Missing username or character."));
            return 0;
        }
        Optional<UUID> targetOwnerUuid = BotBlacklistStorage.resolveTargetUuid(server, username);
        if (targetOwnerUuid.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Could not resolve player UUID for '" + username + "'. They must be online, in the profile cache, or have joined this world before (server_username_uuid_cache.json)."));
            return 0;
        }
        Optional<UUID> charUuid = parseUuidLenient(characterRaw);
        BotBlacklistStorage.Entry entry;
        if (charUuid.isPresent()) {
            String id = charUuid.get().toString();
            entry = new BotBlacklistStorage.Entry(username, targetOwnerUuid.map(UUID::toString).orElse(null), false, id, null);
        } else {
            List<String> byFull = CharacterStorageOperations.findCharacterIdsByFullName(server, targetOwnerUuid.get(), characterRaw, false);
            List<String> byShort = CharacterStorageOperations.findCharacterIdsByShortName(server, targetOwnerUuid.get(), characterRaw, false);
            List<String> matches = new ArrayList<>();
            matches.addAll(byFull);
            for (String s : byShort) {
                if (!matches.contains(s)) {
                    matches.add(s);
                }
            }
            if (matches.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("No stored character matched that name for " + username + "."));
                return 0;
            }
            if (matches.size() > 1) {
                MutableComponent msg = Component.literal("Ambiguous character name; ids: ").withStyle(ChatFormatting.RED);
                for (String m : matches) {
                    msg.append(Component.literal(m + " ").withStyle(ChatFormatting.YELLOW));
                }
                ctx.getSource().sendFailure(msg);
                return 0;
            }
            entry = new BotBlacklistStorage.Entry(username, targetOwnerUuid.map(UUID::toString).orElse(null), false, matches.get(0), characterRaw);
        }
        return addEntry(ctx, self, entry);
    }

    private static Optional<UUID> parseUuidLenient(String s) {
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(s.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static int addEntry(CommandContext<CommandSourceStack> ctx, ServerPlayer self, BotBlacklistStorage.Entry entry) {
        MinecraftServer server = ctx.getSource().getServer();
        List<BotBlacklistStorage.Entry> list = new ArrayList<>(BotBlacklistStorage.load(server, self.getUUID()));
        list.removeIf(e -> BotBlacklistStorage.key(e).equals(BotBlacklistStorage.key(entry)));
        list.add(entry);
        try {
            BotBlacklistStorage.save(server, self.getUUID(), list);
            ctx.getSource().sendSuccess(() -> Component.literal("Added blacklist entry.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Save failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeAllBots(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        String username = StringArgumentType.getString(ctx, "username").trim();
        if (username.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Missing username."));
            return 0;
        }
        BotBlacklistStorage.Entry probe = new BotBlacklistStorage.Entry(username, null, true, null, null);
        return removeByKey(ctx, self, BotBlacklistStorage.key(probe));
    }

    private static int removeCharacter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        String username = StringArgumentType.getString(ctx, "username").trim();
        String characterRaw = StringArgumentType.getString(ctx, "character").trim();
        if (username.isEmpty() || characterRaw.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Missing username or character."));
            return 0;
        }
        Optional<UUID> targetOwnerUuid = BotBlacklistStorage.resolveTargetUuid(server, username);
        if (targetOwnerUuid.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Could not resolve player UUID for '" + username + "'."));
            return 0;
        }
        Optional<UUID> charUuid = parseUuidLenient(characterRaw);
        BotBlacklistStorage.Entry probe;
        if (charUuid.isPresent()) {
            probe = new BotBlacklistStorage.Entry(username, targetOwnerUuid.map(UUID::toString).orElse(null), false, charUuid.get().toString(), null);
        } else {
            List<String> byFull = CharacterStorageOperations.findCharacterIdsByFullName(server, targetOwnerUuid.get(), characterRaw, false);
            List<String> byShort = CharacterStorageOperations.findCharacterIdsByShortName(server, targetOwnerUuid.get(), characterRaw, false);
            List<String> matches = new ArrayList<>();
            matches.addAll(byFull);
            for (String s : byShort) {
                if (!matches.contains(s)) {
                    matches.add(s);
                }
            }
            if (matches.size() != 1) {
                ctx.getSource().sendFailure(Component.literal("Character name must match exactly one stored character to remove."));
                return 0;
            }
            probe = new BotBlacklistStorage.Entry(username, targetOwnerUuid.map(UUID::toString).orElse(null), false, matches.get(0), characterRaw);
        }
        return removeByKey(ctx, self, BotBlacklistStorage.key(probe));
    }

    private static int removeByKey(CommandContext<CommandSourceStack> ctx, ServerPlayer self, String key) {
        MinecraftServer server = ctx.getSource().getServer();
        List<BotBlacklistStorage.Entry> list = new ArrayList<>(BotBlacklistStorage.load(server, self.getUUID()));
        boolean removed = list.removeIf(e -> BotBlacklistStorage.key(e).equals(key));
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("No matching blacklist entry."));
            return 0;
        }
        try {
            BotBlacklistStorage.save(server, self.getUUID(), list);
            ctx.getSource().sendSuccess(() -> Component.literal("Removed blacklist entry.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Save failed: " + e.getMessage()));
            return 0;
        }
    }
}
