package com.goodbird.player2npc.companion;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Pure checks for the strict offline-authoritative state receipt contract. */
public final class PersistentDataManagerStateValidationSelfTest {
    private PersistentDataManagerStateValidationSelfTest() {
    }

    public static void main(String[] args) {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000501");
        String characterId = "strict-state-character";
        CompoundTag state = validState(owner, characterId);
        require(PersistentDataManager.isFullStateForIdentity(
                        state, owner, characterId),
                "a complete schema-v2 state must be authoritative");

        assertRejectedWithout(state, "StateSchemaVersion", owner, characterId);
        assertRejectedWithout(state, "Inventory", owner, characterId);
        assertRejectedWithout(state, "SelectedItemSlot", owner, characterId);
        assertRejectedWithout(state, "HungerState", owner, characterId);
        assertRejectedWithout(state, "follow_mode", owner, characterId);
        assertRejectedWithout(state, "head_yaw", owner, characterId);
        assertRejectedWithout(state, "owner_uuid", owner, characterId);
        assertRejectedWithout(state, "character", owner, characterId);

        CompoundTag wrongSchema = state.copy();
        wrongSchema.putInt("StateSchemaVersion", 1);
        requireRejected(wrongSchema, owner, characterId, "legacy schema cannot authorize an offline tombstone");
        CompoundTag fractionalSchema = state.copy();
        fractionalSchema.putFloat("StateSchemaVersion", 2.0F);
        requireRejected(fractionalSchema, owner, characterId, "schema must use the exact integer NBT type");
        CompoundTag badSlot = state.copy();
        badSlot.putInt("SelectedItemSlot", 9);
        requireRejected(badSlot, owner, characterId, "selected slot must be in the hotbar");
        CompoundTag wrongSlotType = state.copy();
        wrongSlotType.putByte("SelectedItemSlot", (byte) 0);
        requireRejected(wrongSlotType, owner, characterId, "selected slot must use the exact integer NBT type");
        CompoundTag wrongItemSlotType = state.copy();
        wrongItemSlotType.getList("Inventory", 10).getCompound(0).putInt("Slot", 0);
        requireRejected(wrongItemSlotType, owner, characterId, "item slots must use byte NBT");
        CompoundTag badHunger = state.copy();
        badHunger.getCompound("HungerState").putFloat("foodSaturationLevel", Float.NaN);
        requireRejected(badHunger, owner, characterId, "hunger floats must be finite");
        CompoundTag badFollow = state.copy();
        badFollow.putString("follow_mode", "NOT_A_MODE");
        requireRejected(badFollow, owner, characterId, "follow mode must decode");
        CompoundTag badYaw = state.copy();
        badYaw.putFloat("head_yaw", Float.POSITIVE_INFINITY);
        requireRejected(badYaw, owner, characterId, "head yaw must be finite");
        CompoundTag wrongOwner = state.copy();
        wrongOwner.putUUID(
                "owner_uuid",
                UUID.fromString("00000000-0000-0000-0000-000000000502"));
        requireRejected(wrongOwner, owner, characterId, "owner identity must match exactly");
        CompoundTag wrongCharacter = state.copy();
        wrongCharacter.put("character", characterTag("different-id"));
        requireRejected(wrongCharacter, owner, characterId, "character identity must match exactly");
    }

    private static CompoundTag validState(UUID owner, String characterId) {
        CompoundTag state = new CompoundTag();
        state.putInt("StateSchemaVersion", 2);
        ListTag inventory = new ListTag();
        CompoundTag item = new CompoundTag();
        item.putByte("Slot", (byte) 0);
        item.putString("id", "minecraft:stone");
        item.putByte("Count", (byte) 1);
        inventory.add(item);
        state.put("Inventory", inventory);
        state.putInt("SelectedItemSlot", 0);
        CompoundTag hunger = new CompoundTag();
        hunger.putInt("foodLevel", 20);
        hunger.putInt("foodTickTimer", 0);
        hunger.putFloat("foodSaturationLevel", 5.0F);
        hunger.putFloat("foodExhaustionLevel", 0.0F);
        state.put("HungerState", hunger);
        state.putString("follow_mode", "NORMAL");
        state.putFloat("head_yaw", 0.0F);
        state.putUUID("owner_uuid", owner);
        state.put("character", characterTag(characterId));
        return state;
    }

    private static CompoundTag characterTag(String characterId) {
        CompoundTag character = new CompoundTag();
        character.putString("id", characterId);
        character.putString("name", "Strict State Character");
        character.putString("shortName", "Strict");
        character.putString("greetingInfo", "Strict state greeting");
        character.putString("description", "Strict state description");
        character.putString("skinURL", "");
        character.put("voiceIds", new ListTag());
        return character;
    }

    private static void assertRejectedWithout(
            CompoundTag baseline, String key, UUID owner, String characterId) {
        CompoundTag changed = baseline.copy();
        changed.remove(key);
        requireRejected(changed, owner, characterId, "missing " + key + " must fail closed");
    }

    private static void requireRejected(
            CompoundTag state, UUID owner, String characterId, String message) {
        require(!PersistentDataManager.isFullStateForIdentity(
                        state, owner, characterId),
                message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
