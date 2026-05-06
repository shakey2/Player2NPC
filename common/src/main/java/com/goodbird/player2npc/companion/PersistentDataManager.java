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
                Path inventoryFile = getInventoryFile(entity, characterId);
                if (!Files.exists(inventoryFile)) {
                    LOGGER.info("No per-world inventory file found for characterId: {}", characterId);
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

    private static void saveInventoryInternal(AutomatoneEntity entity) {
        try {
            String characterId = getCharacterIdOrNull(entity);
            if (characterId == null) return;
            Path inventoryFile = getInventoryFile(entity, characterId);

            ListTag inventoryNbt = entity.getLivingInventory().writeNbt(entity.level().registryAccess(), new ListTag());
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("Inventory", inventoryNbt);

            Files.createDirectories(inventoryFile.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(inventoryFile))) {
                NbtIo.write(wrapper, out);
            }
            LOGGER.info("Successfully saved per-world inventory for characterId: " + characterId);
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

    private static Path getInventoryFile(AutomatoneEntity entity, String characterId) {
        MinecraftServer server = Objects.requireNonNull(entity.level().getServer(), "server");
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return worldRoot
                .resolve("player2npc")
                .resolve("persistentdata")
                .resolve(characterId)
                .resolve("inventory.dat");
    }
}
