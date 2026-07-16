package com.goodbird.player2npc.companion;

import com.player2.playerengine.FollowMode;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Owner/character-scoped companion state persistence.
 *
 * <p>All entity capture and application is synchronous. Disk access is serialized per target path,
 * and writes use a temporary file plus atomic replacement where the filesystem supports it. No
 * background callback retains an entity reference or can overwrite a newer lifecycle receipt.
 */
public final class PersistentDataManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int STATE_SCHEMA_VERSION = 2;
    private static final int MAX_LIFECYCLE_NOTE_CHARS = 240;
    private static final String KEY_SCHEMA = "StateSchemaVersion";
    private static final String KEY_INVENTORY = "Inventory";
    private static final String KEY_SELECTED_SLOT = "SelectedItemSlot";
    private static final String KEY_HUNGER = "HungerState";
    private static final String KEY_FOLLOW_MODE = "follow_mode";
    private static final String KEY_HEAD_YAW = "head_yaw";
    private static final String KEY_CHARACTER = "character";
    private static final String KEY_OWNER_UUID = "owner_uuid";
    private static final String KEY_FOOD_LEVEL = "foodLevel";
    private static final String KEY_FOOD_TICK_TIMER = "foodTickTimer";
    private static final String KEY_FOOD_SATURATION = "foodSaturationLevel";
    private static final String KEY_FOOD_EXHAUSTION = "foodExhaustionLevel";
    static final String KEY_LIFECYCLE_NOTE = "pending_lifecycle_model_note";
    private static final ConcurrentHashMap<Path, Object> PATH_LOCKS = new ConcurrentHashMap<>();

    private PersistentDataManager() {
    }

    /** Synchronous full-state receipt used by transactional spawn/replacement. */
    public record InventoryStateReceipt(boolean ready, CompoundTag state) {
        public InventoryStateReceipt {
            state = state == null ? null : state.copy();
        }

        static InventoryStateReceipt failed() {
            return new InventoryStateReceipt(false, null);
        }

        static InventoryStateReceipt readyEmpty() {
            return new InventoryStateReceipt(true, new CompoundTag());
        }
    }

    /** Compatibility entry point; intentionally synchronous. */
    public static void saveInventory(AutomatoneEntity entity) {
        saveInventoryNowWithReceipt(entity);
    }

    public static void saveInventoryNow(AutomatoneEntity entity) {
        saveInventoryNowWithReceipt(entity);
    }

    public static boolean saveInventoryNowWithReceipt(AutomatoneEntity entity) {
        InventoryStateReceipt captured = captureInventoryStateNow(entity);
        if (!captured.ready() || captured.state() == null) {
            return false;
        }
        return saveStateInternal(entity, captured.state());
    }

    /**
     * Captures every field promised by companion lifecycle transfer: inventory, selected hotbar
     * slot, hunger state, follow mode, head yaw, owner identity, and stable character identity.
     */
    public static InventoryStateReceipt captureInventoryStateNow(AutomatoneEntity entity) {
        if (entity == null || entity.character == null || entity.level().isClientSide) {
            return InventoryStateReceipt.failed();
        }
        try {
            CompoundTag state = new CompoundTag();
            state.putInt(KEY_SCHEMA, STATE_SCHEMA_VERSION);
            state.put(KEY_INVENTORY, entity.getLivingInventory().writeNbt(new ListTag()));
            state.putInt(KEY_SELECTED_SLOT, entity.getLivingInventory().selectedSlot);
            CompoundTag hunger = new CompoundTag();
            entity.getHungerManager().writeNbt(hunger);
            state.put(KEY_HUNGER, hunger);
            state.putFloat(KEY_HEAD_YAW, entity.getYHeadRot());
            FollowMode followMode = entity.controller != null
                    && entity.controller.getFollowMode() != null
                            ? entity.controller.getFollowMode() : FollowMode.NORMAL;
            state.putString(KEY_FOLLOW_MODE, followMode.name());
            UUID owner = OwnerCharacterStoragePaths.ownerUuidOrNull(entity);
            if (owner != null) {
                state.putUUID(KEY_OWNER_UUID, owner);
            }
            CompoundTag character = new CompoundTag();
            CharacterUtils.writeToNBT(character, entity.character);
            state.put(KEY_CHARACTER, character);
            return new InventoryStateReceipt(true, state);
        } catch (RuntimeException captureFailure) {
            LOGGER.error("Error capturing transactional companion state for characterId={}",
                    getCharacterIdOrNull(entity), captureFailure);
            return InventoryStateReceipt.failed();
        }
    }

    /**
     * Synchronously reads a persisted state. A missing file is a successful empty receipt; malformed
     * or unreadable existing data is a failed receipt so a spawn cannot become canonical with stale
     * defaults.
     */
    public static InventoryStateReceipt loadInventoryStateNow(AutomatoneEntity entity) {
        if (entity == null || entity.character == null || entity.level().isClientSide) {
            return InventoryStateReceipt.failed();
        }
        String characterId = getCharacterIdOrNull(entity);
        if (characterId == null) {
            return InventoryStateReceipt.failed();
        }
        Path inventoryFile;
        try {
            inventoryFile = resolveInventoryFileForLoad(entity, characterId);
        } catch (RuntimeException pathFailure) {
            LOGGER.error("Error resolving persisted companion state for characterId={}",
                    characterId, pathFailure);
            return InventoryStateReceipt.failed();
        }
        if (inventoryFile == null || !Files.isRegularFile(inventoryFile)) {
            LOGGER.info("No per-world companion state file for characterId={}", characterId);
            return InventoryStateReceipt.readyEmpty();
        }
        Path lockKey = lockKey(inventoryFile);
        synchronized (PATH_LOCKS.computeIfAbsent(lockKey, ignored -> new Object())) {
            try (DataInputStream in = new DataInputStream(Files.newInputStream(inventoryFile))) {
                CompoundTag state = NbtIo.read(in);
                if (state == null || !state.contains(KEY_INVENTORY, 9)) {
                    LOGGER.warn("Persisted companion state was malformed for characterId={} at={}",
                            characterId, inventoryFile);
                    return InventoryStateReceipt.failed();
                }
                LOGGER.info("Loaded per-world companion state characterId={} from={}",
                        characterId, inventoryFile);
                return new InventoryStateReceipt(true, state);
            } catch (Exception readFailure) {
                LOGGER.error("Error reading persisted companion state for characterId={} at={}",
                        characterId, inventoryFile, readFailure);
                return InventoryStateReceipt.failed();
            }
        }
    }

    /**
     * Applies a complete state receipt synchronously. Identity-bearing states are validated against
     * the destination before any mutable field is applied.
     */
    public static boolean applyInventoryStateNow(AutomatoneEntity entity, CompoundTag state) {
        if (entity == null || state == null) {
            return false;
        }
        try {
            if (state.contains(KEY_SCHEMA)) {
                UUID destinationOwner = OwnerCharacterStoragePaths.ownerUuidOrNull(entity);
                String destinationCharacterId = getCharacterIdOrNull(entity);
                if (!isFullStateForIdentity(
                        state, destinationOwner, destinationCharacterId)) {
                    return false;
                }
            }
            if (!identityMatchesDestination(entity, state)) {
                return false;
            }

            LivingEntityInventory stagedInventory = null;
            if (state.contains(KEY_INVENTORY)) {
                ListTag inventoryNbt = inventoryListOrNull(state);
                if (inventoryNbt == null) {
                    return false;
                }
                if (!isStructurallyValidInventory(inventoryNbt)) {
                    return false;
                }
                stagedInventory = new LivingEntityInventory(entity);
                readInventoryNbt(entity, stagedInventory, inventoryNbt);
                if (writeInventoryNbt(entity, stagedInventory).size() != inventoryNbt.size()) {
                    return false;
                }
            } else if (!state.isEmpty()) {
                return false;
            }

            Integer selectedSlot = null;
            if (state.contains(KEY_SELECTED_SLOT)) {
                if (!state.contains(KEY_SELECTED_SLOT, 3)) {
                    return false;
                }
                selectedSlot = state.getInt(KEY_SELECTED_SLOT);
                if (selectedSlot < 0 || selectedSlot > 8) {
                    return false;
                }
            }

            CompoundTag stagedHunger = null;
            if (state.contains(KEY_HUNGER)) {
                if (!state.contains(KEY_HUNGER, 10)
                        || !isValidHungerState(state.getCompound(KEY_HUNGER))) {
                    return false;
                }
                stagedHunger = canonicalHungerState(state.getCompound(KEY_HUNGER));
            } else if (hasAnyLegacyHungerKey(state)) {
                if (!isValidHungerState(state)) {
                    return false;
                }
                stagedHunger = canonicalHungerState(state);
            }

            Float headYaw = null;
            if (state.contains(KEY_HEAD_YAW)) {
                if (!state.contains(KEY_HEAD_YAW, 5)
                        || !Float.isFinite(state.getFloat(KEY_HEAD_YAW))) {
                    return false;
                }
                headYaw = state.getFloat(KEY_HEAD_YAW);
            }

            FollowMode restoredFollow = FollowMode.NORMAL;
            if (state.contains(KEY_FOLLOW_MODE)) {
                if (!state.contains(KEY_FOLLOW_MODE, 8)) {
                    return false;
                }
                restoredFollow = decodeFollowMode(state.getString(KEY_FOLLOW_MODE));
                if (restoredFollow == null) {
                    return false;
                }
            }

            // Conversation/history is a separate non-world receipt and cannot be rolled back through
            // entity NBT. Resolve it before mutating inventory, hunger, pose, or controller mode.
            if (!loadConversation(entity)) {
                return false;
            }

            LivingEntityInventory liveInventory = entity.getLivingInventory();
            ListTag originalInventory = writeInventoryNbt(entity, liveInventory);
            int originalSelectedSlot = liveInventory.selectedSlot;
            CompoundTag originalHunger = new CompoundTag();
            entity.getHungerManager().writeNbt(originalHunger);
            float originalHeadYaw = entity.getYHeadRot();
            FollowMode originalFollow = entity.controller != null
                    && entity.controller.getFollowMode() != null
                            ? entity.controller.getFollowMode() : FollowMode.NORMAL;
            try {
                if (stagedInventory != null) {
                    copyInventory(stagedInventory, liveInventory);
                }
                if (selectedSlot != null) {
                    liveInventory.selectedSlot = selectedSlot;
                }
                if (stagedHunger != null) {
                    entity.getHungerManager().readNbt(stagedHunger);
                }
                if (headYaw != null) {
                    entity.setYHeadRot(headYaw);
                }
                if (entity.controller != null) {
                    entity.controller.setFollowMode(restoredFollow);
                }
                return true;
            } catch (RuntimeException commitFailure) {
                try {
                    readInventoryNbt(entity, liveInventory, originalInventory);
                    liveInventory.selectedSlot = originalSelectedSlot;
                    entity.getHungerManager().readNbt(originalHunger);
                    entity.setYHeadRot(originalHeadYaw);
                    if (entity.controller != null) {
                        entity.controller.setFollowMode(originalFollow);
                    }
                } catch (RuntimeException rollbackFailure) {
                    commitFailure.addSuppressed(rollbackFailure);
                }
                throw commitFailure;
            }
        } catch (Exception applyFailure) {
            LOGGER.error("Error applying transactional companion state for characterId={}",
                    getCharacterIdOrNull(entity), applyFailure);
            return false;
        }
    }

    private static boolean hasAnyLegacyHungerKey(CompoundTag state) {
        return state.contains(KEY_FOOD_LEVEL)
                || state.contains(KEY_FOOD_TICK_TIMER)
                || state.contains(KEY_FOOD_SATURATION)
                || state.contains(KEY_FOOD_EXHAUSTION);
    }

    private static CompoundTag canonicalHungerState(CompoundTag input) {
        LivingEntityHungerManager staged = new LivingEntityHungerManager();
        staged.readNbt(input);
        CompoundTag canonical = new CompoundTag();
        staged.writeNbt(canonical);
        return canonical;
    }

    private static void copyInventory(
            LivingEntityInventory source, LivingEntityInventory destination) {
        for (int i = 0; i < destination.main.size(); i++) {
            destination.main.set(i, source.main.get(i).copy());
        }
        for (int i = 0; i < destination.armor.size(); i++) {
            destination.armor.set(i, source.armor.get(i).copy());
        }
        for (int i = 0; i < destination.offHand.size(); i++) {
            destination.offHand.set(i, source.offHand.get(i).copy());
        }
    }

    private static ListTag writeInventoryNbt(
            AutomatoneEntity entity, LivingEntityInventory inventory) {
        return inventory.writeNbt(new ListTag());
    }

    private static void readInventoryNbt(
            AutomatoneEntity entity, LivingEntityInventory inventory, ListTag state) {
        inventory.readNbt(state.copy());
    }

    /** Compatibility entry point; intentionally synchronous and fail-closed. */
    public static void loadInventory(AutomatoneEntity entity) {
        InventoryStateReceipt loaded = loadInventoryStateNow(entity);
        if (!loaded.ready() || loaded.state() == null
                || !applyInventoryStateNow(entity, loaded.state())) {
            LOGGER.error("Synchronous companion state load/apply failed for characterId={}",
                    getCharacterIdOrNull(entity));
        }
    }

    static CompoundTag withLifecycleNote(CompoundTag state, String note) {
        CompoundTag copy = state == null ? new CompoundTag() : state.copy();
        String bounded = boundedSingleLine(note);
        if (!bounded.isEmpty()) {
            copy.putString(KEY_LIFECYCLE_NOTE, bounded);
        }
        return copy;
    }

    static String lifecycleNote(CompoundTag state) {
        return state == null ? "" : boundedSingleLine(state.getString(KEY_LIFECYCLE_NOTE));
    }

    static CompoundTag withoutLifecycleNote(CompoundTag state) {
        CompoundTag copy = state == null ? new CompoundTag() : state.copy();
        copy.remove(KEY_LIFECYCLE_NOTE);
        return copy;
    }

    /** Fail-closed validation for an offline receipt before it may mutate the owner map. */
    static boolean isFullStateForIdentity(
            CompoundTag state, UUID expectedOwner, String expectedCharacterId) {
        if (state == null || expectedOwner == null || expectedCharacterId == null
                || expectedCharacterId.isBlank()
                || !state.contains(KEY_SCHEMA, 3)
                || state.getInt(KEY_SCHEMA) != STATE_SCHEMA_VERSION
                || inventoryListOrNull(state) == null
                || !isStructurallyValidInventory(inventoryListOrNull(state))
                || !state.contains(KEY_SELECTED_SLOT, 3)
                || state.getInt(KEY_SELECTED_SLOT) < 0
                || state.getInt(KEY_SELECTED_SLOT) > 8
                || !state.contains(KEY_HUNGER, 10)
                || !isValidHungerState(state.getCompound(KEY_HUNGER))
                || !state.contains(KEY_FOLLOW_MODE, 8)
                || decodeFollowMode(state.getString(KEY_FOLLOW_MODE)) == null
                || !state.contains(KEY_HEAD_YAW, 5)
                || !Float.isFinite(state.getFloat(KEY_HEAD_YAW))
                || !state.hasUUID(KEY_OWNER_UUID)
                || !expectedOwner.equals(state.getUUID(KEY_OWNER_UUID))
                || !state.contains(KEY_CHARACTER, 10)) {
            return false;
        }
        CompoundTag storedCharacter = state.getCompound(KEY_CHARACTER);
        return storedCharacter.contains("id", 8)
                && expectedCharacterId.equals(storedCharacter.getString("id"));
    }

    private static boolean isStructurallyValidInventory(ListTag inventory) {
        Set<Integer> occupiedSlots = new HashSet<>();
        for (int i = 0; i < inventory.size(); i++) {
            CompoundTag item = inventory.getCompound(i);
            if (!item.contains("Slot", 1) || !item.contains("id", 8)
                    || item.getString("id").isBlank()) {
                return false;
            }
            int slot = item.getByte("Slot") & 255;
            boolean supportedSlot = slot < 36
                    || (slot >= 100 && slot < 104)
                    || slot == 150;
            if (!supportedSlot || !occupiedSlots.add(slot)) {
                return false;
            }
        }
        return true;
    }

    private static ListTag inventoryListOrNull(CompoundTag state) {
        if (state == null || !state.contains(KEY_INVENTORY, 9)
                || !(state.get(KEY_INVENTORY) instanceof ListTag inventory)
                || (!inventory.isEmpty() && inventory.getElementType() != 10)) {
            return null;
        }
        return inventory;
    }

    private static boolean isValidHungerState(CompoundTag hunger) {
        if (!hunger.contains(KEY_FOOD_LEVEL, 3)
                || !hunger.contains(KEY_FOOD_TICK_TIMER, 3)
                || !hunger.contains(KEY_FOOD_SATURATION, 5)
                || !hunger.contains(KEY_FOOD_EXHAUSTION, 5)) {
            return false;
        }
        int food = hunger.getInt(KEY_FOOD_LEVEL);
        int timer = hunger.getInt(KEY_FOOD_TICK_TIMER);
        float saturation = hunger.getFloat(KEY_FOOD_SATURATION);
        float exhaustion = hunger.getFloat(KEY_FOOD_EXHAUSTION);
        return food >= 0 && food <= 20
                && timer >= 0
                && Float.isFinite(saturation) && saturation >= 0.0F && saturation <= food
                && Float.isFinite(exhaustion) && exhaustion >= 0.0F && exhaustion <= 40.0F;
    }

    private static FollowMode decodeFollowMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FollowMode.valueOf(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static boolean saveStateInternal(AutomatoneEntity entity, CompoundTag capturedState) {
        String characterId = getCharacterIdOrNull(entity);
        if (characterId == null) {
            return false;
        }
        try {
            Path inventoryFile = getInventoryFileForSave(entity, characterId);
            Path lockKey = lockKey(inventoryFile);
            synchronized (PATH_LOCKS.computeIfAbsent(lockKey, ignored -> new Object())) {
                Files.createDirectories(inventoryFile.getParent());
                writeNbtAtomically(inventoryFile, capturedState);
                OwnerCharacterStoragePaths.writeDisplayJson(
                        inventoryFile.getParent(), entity.character);
                if (!saveConversationNow(entity)) {
                    return false;
                }
                LOGGER.info("Saved per-world companion state ownerPath={} characterId={}",
                        inventoryFile.getParent(), characterId);
                return true;
            }
        } catch (Exception writeFailure) {
            LOGGER.error("Error saving persisted companion state for characterId={}",
                    characterId, writeFailure);
            return false;
        }
    }

    static void writeNbtAtomically(Path target, CompoundTag state) throws Exception {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp))) {
                NbtIo.write(state.copy(), out);
            }
            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean identityMatchesDestination(
            AutomatoneEntity entity, CompoundTag state) {
        if (state.hasUUID(KEY_OWNER_UUID)) {
            UUID destinationOwner = OwnerCharacterStoragePaths.ownerUuidOrNull(entity);
            if (destinationOwner == null
                    || !state.getUUID(KEY_OWNER_UUID).equals(destinationOwner)) {
                return false;
            }
        }
        if (state.contains(KEY_CHARACTER, 10)) {
            Character stored = CharacterUtils.readFromNBT(state.getCompound(KEY_CHARACTER));
            String storedId = stored == null ? null : stored.id();
            String destinationId = entity.character == null ? null : entity.character.id();
            if (storedId == null || storedId.isBlank()
                    || destinationId == null || !storedId.equals(destinationId)) {
                return false;
            }
        }
        return true;
    }

    private static String boundedSingleLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String singleLine = value.strip().replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() <= MAX_LIFECYCLE_NOTE_CHARS
                ? singleLine : singleLine.substring(0, MAX_LIFECYCLE_NOTE_CHARS);
    }

    private static String getCharacterIdOrNull(AutomatoneEntity entity) {
        if (entity == null || entity.character == null) {
            return null;
        }
        String characterId = entity.character.id();
        if (characterId == null || characterId.isBlank()) {
            LOGGER.warn("Skipping per-world persistence: missing characterId for character='{}'",
                    entity.character.name());
            return null;
        }
        return characterId;
    }

    private static Path getWorldRoot(AutomatoneEntity entity) {
        MinecraftServer server = Objects.requireNonNull(entity.level().getServer(), "server");
        return server.getWorldPath(LevelResource.ROOT);
    }

    private static Path getInventoryFileForSave(
            AutomatoneEntity entity, String characterId) {
        Path worldRoot = getWorldRoot(entity);
        UUID owner = OwnerCharacterStoragePaths.ownerUuidOrNull(entity);
        if (owner != null) {
            return OwnerCharacterStoragePaths.inventoryFile(worldRoot, owner, characterId);
        }
        return OwnerCharacterStoragePaths.legacyEntityInventoryFile(
                worldRoot, entity.getUUID(), characterId);
    }

    private static Path resolveInventoryFileForLoad(
            AutomatoneEntity entity, String characterId) {
        Path worldRoot = getWorldRoot(entity);
        UUID owner = OwnerCharacterStoragePaths.ownerUuidOrNull(entity);
        if (owner != null) {
            Path primary = OwnerCharacterStoragePaths.inventoryFile(
                    worldRoot, owner, characterId);
            if (Files.exists(primary)) {
                return primary;
            }
        }
        Path entityScoped = OwnerCharacterStoragePaths.legacyEntityInventoryFile(
                worldRoot, entity.getUUID(), characterId);
        if (Files.exists(entityScoped)) {
            return entityScoped;
        }
        Path legacy = OwnerCharacterStoragePaths.legacyCharacterOnlyInventoryFile(
                worldRoot, characterId);
        if (Files.exists(legacy)) {
            return legacy;
        }
        return owner != null
                ? OwnerCharacterStoragePaths.inventoryFile(worldRoot, owner, characterId)
                : entityScoped;
    }

    private static boolean saveConversationNow(AutomatoneEntity entity) {
        try {
            if (entity == null || entity.controller == null) {
                return true;
            }
            entity.controller.getAIPersistantData().saveHistoryNow();
            entity.controller.getAIPersistantData().saveMoodNow();
            return true;
        } catch (Exception failure) {
            LOGGER.error("Error saving conversation history for characterId={}",
                    getCharacterIdOrNull(entity), failure);
            return false;
        }
    }

    private static boolean loadConversation(AutomatoneEntity entity) {
        try {
            if (entity == null || entity.controller == null) {
                return true;
            }
            entity.controller.getAIPersistantData().reloadHistoryFromDisk();
            entity.controller.getAIPersistantData().reloadMoodFromDisk();
            return true;
        } catch (Exception failure) {
            LOGGER.error("Error loading conversation history for characterId={}",
                    getCharacterIdOrNull(entity), failure);
            return false;
        }
    }

    private static Path lockKey(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
