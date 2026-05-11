package com.goodbird.player2npc.companion;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PersistentDataManager {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void saveInventory(AutomatoneEntity entity) {
        if (entity.character == null || entity.level().isClientSide) return;

        CompletableFuture.runAsync(() -> {
            saveInventoryInternal(entity);
        });
    }

    /**
     * Synchronous save variant for call sites (like dismiss) where we want the inventory file
     * written before the entity is discarded and potentially re-summoned immediately.
     */
    public static void saveInventoryNow(AutomatoneEntity entity) {
        if (entity.character == null || entity.level().isClientSide) return;
        saveInventoryInternal(entity);
    }

    public static void loadInventory(AutomatoneEntity entity) {
        if (entity.character == null || entity.level().isClientSide) return;

        CompletableFuture.runAsync(() -> {
            try {
                String characterId = getCharacterIdOrNull(entity);
                if (characterId == null) return;
                Path inventoryFile = resolveInventoryFileForLoad(entity, characterId);
                if (inventoryFile == null || !Files.exists(inventoryFile)) {
                    LOGGER.info("No per-world inventory file for entityUuid={} characterId={}",
                            entity.getUUID(), characterId);
                    return;
                }

                CompoundTag wrapper;
                try (DataInputStream in = new DataInputStream(Files.newInputStream(inventoryFile))) {
                    wrapper = NbtIo.read(in);
                }
                if (wrapper == null || !wrapper.contains("Inventory", 9)) return;

                ListTag inventoryNbt = wrapper.getList("Inventory", 10);
                MinecraftServer server = Objects.requireNonNull(entity.level().getServer(), "server");
                server.execute(() -> {
                    try {
                        entity.getLivingInventory().readNbt(entity.level().registryAccess(), inventoryNbt);
                        loadConversation(entity);
                        LOGGER.info("Loaded per-world inventory entityUuid={} characterId={} from={}",
                                entity.getUUID(), characterId, inventoryFile);
                    } catch (Exception e) {
                        LOGGER.error("Error applying inventory for characterId: " + characterId, e);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Error loading persistent data for " + entity.character.name(), e);
            }
        });
    }

    private static void saveInventoryInternal(AutomatoneEntity entity) {
        try {
            String characterId = getCharacterIdOrNull(entity);
            if (characterId == null) return;
            Path inventoryFile = getInventoryFileForSave(entity, characterId);

            ListTag inventoryNbt = entity.getLivingInventory().writeNbt(entity.level().registryAccess(), new ListTag());
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("Inventory", inventoryNbt);

            Files.createDirectories(inventoryFile.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(inventoryFile))) {
                NbtIo.write(wrapper, out);
            }
            saveConversationNow(entity);
            LOGGER.info("Saved per-world inventory entityUuid={} characterId={}",
                    entity.getUUID(), characterId);
        } catch (Exception e) {
            LOGGER.error("Error saving persistent data for " + entity.character.name(), e);
        }
    }

    private static String getCharacterIdOrNull(AutomatoneEntity entity) {
        String characterId = entity.character.id();
        if (characterId == null || characterId.isBlank()) {
            LOGGER.warn("Skipping per-world persistence: missing characterId for character='{}'", entity.character.name());
            return null;
        }
        return characterId;
    }

    private static Path getWorldRoot(AutomatoneEntity entity) {
        MinecraftServer server = Objects.requireNonNull(entity.level().getServer(), "server");
        return server.getWorldPath(LevelResource.ROOT);
    }

    private static Path getInventoryFileForSave(AutomatoneEntity entity, String characterId) {
        Path worldRoot = getWorldRoot(entity);
        return worldRoot
                .resolve("player2npc")
                .resolve("persistentdata")
                .resolve(entity.getUUID().toString())
                .resolve(characterId)
                .resolve("inventory.dat");
    }

    private static Path getLegacyInventoryFile(Path worldRoot, String characterId) {
        return worldRoot
                .resolve("player2npc")
                .resolve("persistentdata")
                .resolve(characterId)
                .resolve("inventory.dat");
    }

    private static Path resolveInventoryFileForLoad(AutomatoneEntity entity, String characterId) {
        Path worldRoot = getWorldRoot(entity);
        Path primary = getInventoryFileForSave(entity, characterId);
        if (Files.exists(primary)) {
            return primary;
        }
        Path legacy = getLegacyInventoryFile(worldRoot, characterId);
        if (Files.exists(legacy)) {
            return legacy;
        }
        return null;
    }

    private static void saveConversationNow(AutomatoneEntity entity) {
        try {
            if (entity == null || entity.controller == null) return;
            entity.controller.getAIPersistantData().saveHistoryNow();
        } catch (Exception e) {
            LOGGER.error("Error saving conversation history for " + (entity.character == null ? "UNKNOWN" : entity.character.name()), e);
        }
    }

    private static void loadConversation(AutomatoneEntity entity) {
        try {
            if (entity == null || entity.controller == null) return;
            entity.controller.getAIPersistantData().reloadHistoryFromDisk();
        } catch (Exception e) {
            LOGGER.error("Error loading conversation history for " + (entity.character == null ? "UNKNOWN" : entity.character.name()), e);
        }
    }
}
