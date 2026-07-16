package com.goodbird.player2npc.companion;

import com.player2.playerengine.player2api.Character;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** Pure, server-free tombstone codec, identity, path, and defensive-copy checks. */
public final class OfflineDeathTombstoneStorageSelfTest {
    private OfflineDeathTombstoneStorageSelfTest() {
    }

    public static void main(String[] args) {
        runAll();
    }

    public static void runAll() {
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID dyingUuid = UUID.fromString("00000000-0000-0000-0000-000000000402");
        UUID otherDyingUuid = UUID.fromString("00000000-0000-0000-0000-000000000403");
        String[] sourceVoices = {"voice-a", null, "voice-c"};
        Character sourceCharacter = new Character(
                "stable-character-id",
                "Stable Character Name",
                null,
                "hello",
                "description",
                null,
                sourceVoices);
        CompoundTag sourceState = new CompoundTag();
        sourceState.putInt("HealthReceipt", 17);
        CompoundTag nestedState = new CompoundTag();
        nestedState.putString("InventoryReceipt", "exact");
        sourceState.put("Nested", nestedState);

        String longNote = "  death\nreceipt\t" + "x".repeat(
                OfflineDeathTombstoneStorage.MAX_LIFECYCLE_MODEL_NOTE_LENGTH + 20);
        String longCause = " fell\r\nthrough\u0000world " + "y".repeat(
                OfflineDeathTombstoneStorage.MAX_DEATH_CAUSE_LENGTH + 20);
        OfflineDeathTombstoneStorage.Tombstone tombstone =
                new OfflineDeathTombstoneStorage.Tombstone(
                        ownerUuid,
                        sourceCharacter,
                        sourceCharacter.id(),
                        sourceCharacter.name(),
                        dyingUuid,
                        sourceState,
                        true,
                        false,
                        false,
                        longNote,
                        longCause);

        sourceVoices[0] = "mutated-source";
        sourceState.putInt("HealthReceipt", 1);
        require("voice-a".equals(tombstone.character().voiceIds()[0]),
                "construction must copy the mutable Character voice array");
        require(tombstone.state().getInt("HealthReceipt") == 17,
                "construction must copy mutable state NBT");

        Character firstCharacterRead = tombstone.character();
        firstCharacterRead.voiceIds()[0] = "mutated-accessor";
        CompoundTag firstStateRead = tombstone.state();
        firstStateRead.putInt("HealthReceipt", 2);
        require("voice-a".equals(tombstone.character().voiceIds()[0]),
                "the Character accessor must return a defensive copy");
        require(tombstone.state().getInt("HealthReceipt") == 17,
                "the state accessor must return a defensive copy");
        require(isBoundedSingleLine(
                        tombstone.lifecycleModelNote(),
                        OfflineDeathTombstoneStorage.MAX_LIFECYCLE_MODEL_NOTE_LENGTH),
                "lifecycle model notes must be hard-capped and single-line");
        require(isBoundedSingleLine(
                        tombstone.deathCause(),
                        OfflineDeathTombstoneStorage.MAX_DEATH_CAUSE_LENGTH),
                "death causes must be hard-capped and single-line");

        CompoundTag encoded = OfflineDeathTombstoneStorage.encode(tombstone);
        OfflineDeathTombstoneStorage.Tombstone decoded = OfflineDeathTombstoneStorage.decode(
                encoded, ownerUuid, sourceCharacter.id(), dyingUuid);
        require(decoded != null, "an exact encoded tombstone must decode");
        require(ownerUuid.equals(decoded.ownerUuid())
                        && dyingUuid.equals(decoded.dyingUuid())
                        && sourceCharacter.id().equals(decoded.stableCharacterId())
                        && sourceCharacter.name().equals(decoded.stableCharacterName()),
                "all stable identity fields must round-trip exactly");
        require(charactersEqual(tombstone.character(), decoded.character()),
                "all nullable Character fields and voice entries must round-trip exactly");
        require(tombstone.state().equals(decoded.state()),
                "the complete nested state compound must round-trip exactly");
        require(decoded.autoRespawn()
                        && !decoded.botPermadeath()
                        && !decoded.permadeathKill()
                        && tombstone.lifecycleModelNote().equals(decoded.lifecycleModelNote())
                        && tombstone.deathCause().equals(decoded.deathCause()),
                "effective policy and bounded lifecycle fields must round-trip exactly");

        encoded.getCompound("State").putInt("HealthReceipt", 3);
        encoded.getCompound("Character").putString("Name", "mutated-encoded");
        require(decoded.state().getInt("HealthReceipt") == 17,
                "decoding must detach the state from encoded input");
        require("Stable Character Name".equals(decoded.character().name()),
                "decoding must detach Character data from encoded input");

        Path worldRoot = Path.of("deterministic-world-root");
        Path expectedPath = OwnerCharacterStoragePaths.ownerCharacterDir(
                        worldRoot, ownerUuid, sourceCharacter.id())
                .resolve("death_tombstones")
                .resolve(dyingUuid + ".dat");
        Path actualPath = OfflineDeathTombstoneStorage.tombstonePath(worldRoot, tombstone);
        require(expectedPath.equals(actualPath),
                "the path must be world/owner/stable-character-id/dying-uuid scoped");
        require(!actualPath.equals(OfflineDeathTombstoneStorage.tombstonePath(
                        worldRoot, ownerUuid, sourceCharacter.id(), otherDyingUuid)),
                "different dying UUIDs must never overwrite each other's tombstones");

        CompoundTag baseline = OfflineDeathTombstoneStorage.encode(tombstone);
        assertRejectedWithStringChange(
                baseline, "OwnerUuid", otherDyingUuid.toString(), ownerUuid, sourceCharacter.id(), dyingUuid,
                "stored owner identity must match the owner directory");
        assertRejectedWithStringChange(
                baseline, "StableCharacterId", "different-character", ownerUuid, sourceCharacter.id(), dyingUuid,
                "stored stable id must match the character directory");
        assertRejectedWithStringChange(
                baseline, "DyingUuid", otherDyingUuid.toString(), ownerUuid, sourceCharacter.id(), dyingUuid,
                "stored dying UUID must match the filename");
        assertRejectedWithStringChange(
                baseline, "StableCharacterName", "different-name", ownerUuid, sourceCharacter.id(), dyingUuid,
                "stable name must exactly match the stored Character");

        CompoundTag wrongStateType = baseline.copy();
        wrongStateType.putString("State", "not-a-compound");
        require(OfflineDeathTombstoneStorage.decode(
                        wrongStateType, ownerUuid, sourceCharacter.id(), dyingUuid) == null,
                "state must be a full compound rather than a defaulted value");
        CompoundTag incompleteCharacter = baseline.copy();
        incompleteCharacter.getCompound("Character").remove("DescriptionPresent");
        require(OfflineDeathTombstoneStorage.decode(
                        incompleteCharacter, ownerUuid, sourceCharacter.id(), dyingUuid) == null,
                "missing exact Character presence metadata must fail closed");
        CompoundTag wrongVoiceEntryType = baseline.copy();
        ListTag invalidVoices = new ListTag();
        invalidVoices.add(StringTag.valueOf("not-a-structured-nullable-entry"));
        wrongVoiceEntryType.getCompound("Character").put("VoiceIds", invalidVoices);
        require(OfflineDeathTombstoneStorage.decode(
                        wrongVoiceEntryType, ownerUuid, sourceCharacter.id(), dyingUuid) == null,
                "voice entries must preserve exact nullable Character structure");
        CompoundTag unboundedModelNote = baseline.copy();
        unboundedModelNote.putString("LifecycleModelNote", "line one\nline two");
        require(OfflineDeathTombstoneStorage.decode(
                        unboundedModelNote, ownerUuid, sourceCharacter.id(), dyingUuid) == null,
                "non-canonical model-facing strings must be rejected on load");
        CompoundTag invalidBoolean = baseline.copy();
        invalidBoolean.putByte("AutoRespawn", (byte) 2);
        require(OfflineDeathTombstoneStorage.decode(
                        invalidBoolean, ownerUuid, sourceCharacter.id(), dyingUuid) == null,
                "policy booleans must use an exact zero-or-one NBT receipt");

        boolean traversalRejected = false;
        try {
            OfflineDeathTombstoneStorage.tombstonePath(
                    worldRoot, ownerUuid, "../display-name-is-not-identity", dyingUuid);
        } catch (IllegalArgumentException expected) {
            traversalRejected = true;
        }
        require(traversalRejected,
                "stable identity paths must reject traversal and never fall back to a display name");
    }

    private static void assertRejectedWithStringChange(
            CompoundTag baseline,
            String key,
            String value,
            UUID expectedOwner,
            String expectedCharacterId,
            UUID expectedDying,
            String message) {
        CompoundTag changed = baseline.copy();
        changed.putString(key, value);
        require(OfflineDeathTombstoneStorage.decode(
                        changed, expectedOwner, expectedCharacterId, expectedDying) == null,
                message);
    }

    private static boolean charactersEqual(Character left, Character right) {
        return java.util.Objects.equals(left.id(), right.id())
                && java.util.Objects.equals(left.name(), right.name())
                && java.util.Objects.equals(left.shortName(), right.shortName())
                && java.util.Objects.equals(left.greetingInfo(), right.greetingInfo())
                && java.util.Objects.equals(left.description(), right.description())
                && java.util.Objects.equals(left.skinURL(), right.skinURL())
                && Arrays.equals(left.voiceIds(), right.voiceIds());
    }

    private static boolean isBoundedSingleLine(String value, int maximumLength) {
        if (value.length() > maximumLength) {
            return false;
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (java.lang.Character.isISOControl(codePoint)
                    || codePoint == '\r'
                    || codePoint == '\n') {
                return false;
            }
            offset += java.lang.Character.charCount(codePoint);
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
