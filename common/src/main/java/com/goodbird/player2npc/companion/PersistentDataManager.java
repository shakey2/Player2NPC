package com.goodbird.player2npc.companion;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import java.util.concurrent.CompletableFuture;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PersistentDataManager {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Serializes the entity's inventory to NBT and saves it to a per-world file keyed by character ID.
     */
    public static void saveInventory(AutomatoneEntity entity) {
        if (entity.character == null || entity.level().isClientSide) return;

        CompletableFuture.runAsync(() -> {
            try {
                String characterId = getCharacterIdOrNull(entity);
                if (characterId == null) return;
                Path inventoryFile = getInventoryFile(entity, characterId);
                
                ListTag inventoryNbt = entity.getLivingInventory().writeNbt(new ListTag());
                CompoundTag wrapper = new CompoundTag();
                wrapper.put("Inventory", inventoryNbt);
                
                Files.createDirectories(inventoryFile.getParent());
                try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(inventoryFile))) {
                    NbtIo.write(wrapper, out);
                }
                saveConversationNow(entity);
                LOGGER.info("Successfully saved per-world inventory for characterId: " + characterId);
            } catch (Exception e) {
                LOGGER.error("Error saving persistent data for " + entity.character.name(), e);
            }
        });
    }

    /**
     * Loads the serialized inventory NBT from a per-world file keyed by character ID and applies it.
     */
    public static void loadInventory(AutomatoneEntity entity) {
        if (entity.character == null || entity.level().isClientSide) return;

        CompletableFuture.runAsync(() -> {
            try {
                String characterId = getCharacterIdOrNull(entity);
                if (characterId == null) return;
                Path inventoryFile = getInventoryFile(entity, characterId);
                if (!Files.exists(inventoryFile)) return;

                CompoundTag wrapper;
                try (DataInputStream in = new DataInputStream(Files.newInputStream(inventoryFile))) {
                    wrapper = NbtIo.read(in);
                }
                if (wrapper == null || !wrapper.contains("Inventory", 9)) return;

                ListTag inventoryNbt = wrapper.getList("Inventory", 10);
                MinecraftServer server = Objects.requireNonNull(entity.level().getServer(), "server");
                server.execute(() -> {
                    try {
                        entity.getLivingInventory().readNbt(inventoryNbt);
                        loadConversation(entity);
                        LOGGER.info("Successfully loaded per-world inventory for characterId: " + characterId);
                    } catch (Exception e) {
                        LOGGER.error("Error applying inventory for characterId: " + characterId, e);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Error loading persistent data for " + entity.character.name(), e);
            }
        });
    }

    private static String getCharacterIdOrNull(AutomatoneEntity entity) {
        String characterId = entity.character.id();
        if (characterId == null || characterId.isBlank()) {
            LOGGER.warn("Skipping per-world persistence: missing characterId for character='{}'", entity.character.name());
            return null;
        }
        return characterId;
    }

    private static Path getInventoryFile(AutomatoneEntity entity, String characterId) {
        MinecraftServer server = Objects.requireNonNull(entity.level().getServer(), "server");
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return worldRoot
                .resolve("player2npc")
                .resolve("persistentdata")
                .resolve(characterId)
                .resolve("inventory.dat");
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
