package com.goodbird.player2npc.companion;

import com.player2.playerengine.player2api.Character;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Canonical on-disk layout: {@code player2npc/persistentdata/owners/<ownerPlayerUuid>/<characterId>/}.
 */
public final class OwnerCharacterStoragePaths {
    private static final Logger LOGGER = LogManager.getLogger();

    private OwnerCharacterStoragePaths() {
    }

    public static Path persistentDataRoot(Path worldRoot) {
        return worldRoot.resolve("player2npc").resolve("persistentdata");
    }

    public static Path ownersRoot(Path worldRoot) {
        return persistentDataRoot(worldRoot).resolve("owners");
    }

    public static Path ownerCharacterDir(Path worldRoot, UUID ownerUuid, String characterId) {
        return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve(characterId);
    }

    public static Path inventoryFile(Path worldRoot, UUID ownerUuid, String characterId) {
        return ownerCharacterDir(worldRoot, ownerUuid, characterId).resolve("inventory.dat");
    }

    /** Entity-scoped path used before owner-based layout. */
    public static Path legacyEntityInventoryFile(Path worldRoot, UUID entityUuid, String characterId) {
        return persistentDataRoot(worldRoot).resolve(entityUuid.toString()).resolve(characterId).resolve("inventory.dat");
    }

    public static Path legacyCharacterOnlyInventoryFile(Path worldRoot, String characterId) {
        return persistentDataRoot(worldRoot).resolve(characterId).resolve("inventory.dat");
    }

    public static UUID ownerUuidOrNull(AutomatoneEntity entity) {
        if (entity.controller == null) {
            return null;
        }
        Player owner = entity.controller.getOwner();
        return owner == null ? null : owner.getUUID();
    }

    public static Path worldRootFromEntity(AutomatoneEntity entity) {
        return entity.level().getServer().getWorldPath(LevelResource.ROOT);
    }

    public static void writeDisplayJson(Path characterDir, Character character) {
        if (character == null || characterDir == null) {
            return;
        }
        try {
            Files.createDirectories(characterDir);
            String id = character.id() == null ? "" : escapeJson(character.id());
            String name = character.name() == null ? "" : escapeJson(character.name());
            String shortName = character.shortName() == null ? "" : escapeJson(character.shortName());
            String json = "{\"characterId\":\"" + id + "\",\"fullName\":\"" + name + "\",\"shortName\":\"" + shortName + "\"}";
            Files.writeString(characterDir.resolve("display.json"), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write display.json under {}", characterDir, e);
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
