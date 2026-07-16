package com.goodbird.player2npc.companion;

import com.player2.playerengine.player2api.Character;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Synchronous, world-scoped storage for companion deaths that cannot be reconciled while the owner is
 * offline. Each dying entity UUID owns a distinct file; loading never consumes a tombstone.
 */
public final class OfflineDeathTombstoneStorage {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final int SCHEMA_VERSION = 1;
    static final int MAX_LIFECYCLE_MODEL_NOTE_LENGTH = 512;
    static final int MAX_DEATH_CAUSE_LENGTH = 256;

    private static final String TOMBSTONE_DIRECTORY = "death_tombstones";
    private static final String DATA_SUFFIX = ".dat";
    private static final String KEY_SCHEMA_VERSION = "SchemaVersion";
    private static final String KEY_OWNER_UUID = "OwnerUuid";
    private static final String KEY_CHARACTER = "Character";
    private static final String KEY_STABLE_CHARACTER_ID = "StableCharacterId";
    private static final String KEY_STABLE_CHARACTER_NAME = "StableCharacterName";
    private static final String KEY_DYING_UUID = "DyingUuid";
    private static final String KEY_STATE = "State";
    private static final String KEY_AUTO_RESPAWN = "AutoRespawn";
    private static final String KEY_BOT_PERMADEATH = "BotPermadeath";
    private static final String KEY_PERMADEATH_KILL = "PermadeathKill";
    private static final String KEY_LIFECYCLE_MODEL_NOTE = "LifecycleModelNote";
    private static final String KEY_DEATH_CAUSE = "DeathCause";

    private static final String KEY_CHARACTER_ID = "Id";
    private static final String KEY_CHARACTER_NAME = "Name";
    private static final String KEY_CHARACTER_SHORT_NAME = "ShortName";
    private static final String KEY_CHARACTER_GREETING_INFO = "GreetingInfo";
    private static final String KEY_CHARACTER_DESCRIPTION = "Description";
    private static final String KEY_CHARACTER_SKIN_URL = "SkinUrl";
    private static final String KEY_CHARACTER_VOICE_IDS = "VoiceIds";
    private static final String PRESENT_SUFFIX = "Present";
    private static final String KEY_LIST_VALUE = "Value";

    private OfflineDeathTombstoneStorage() {
    }

    /**
     * Immutable death receipt. Mutable NBT and the {@link Character#voiceIds()} array are copied both
     * into and out of the record.
     */
    public record Tombstone(
            UUID ownerUuid,
            Character character,
            String stableCharacterId,
            String stableCharacterName,
            UUID dyingUuid,
            CompoundTag state,
            boolean autoRespawn,
            boolean botPermadeath,
            boolean permadeathKill,
            String lifecycleModelNote,
            String deathCause) {
        public Tombstone {
            ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
            character = copyCharacter(Objects.requireNonNull(character, "character"));
            stableCharacterId = requireStableCharacterId(stableCharacterId);
            stableCharacterName = Objects.requireNonNull(stableCharacterName, "stableCharacterName");
            dyingUuid = Objects.requireNonNull(dyingUuid, "dyingUuid");
            state = Objects.requireNonNull(state, "state").copy();
            if (!stableCharacterId.equals(character.id())) {
                throw new IllegalArgumentException("stableCharacterId must exactly match Character.id");
            }
            if (!stableCharacterName.equals(character.name())) {
                throw new IllegalArgumentException("stableCharacterName must exactly match Character.name");
            }
            lifecycleModelNote = boundedSingleLine(
                    lifecycleModelNote, MAX_LIFECYCLE_MODEL_NOTE_LENGTH);
            deathCause = boundedSingleLine(deathCause, MAX_DEATH_CAUSE_LENGTH);
        }

        @Override
        public Character character() {
            return copyCharacter(character);
        }

        @Override
        public CompoundTag state() {
            return state.copy();
        }
    }

    /** Writes or atomically replaces only this dying UUID's tombstone file. */
    public static boolean save(MinecraftServer server, Tombstone tombstone) {
        if (server == null || tombstone == null) {
            return false;
        }
        Path target = null;
        Path temporary = null;
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            target = tombstonePath(worldRoot, tombstone);
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(
                    target.getParent(), "." + tombstone.dyingUuid() + ".", ".tmp");
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temporary))) {
                NbtIo.write(encode(tombstone), output);
            }
            moveReplacing(temporary, target);
            temporary = null;
            return true;
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not save offline death tombstone at {}", target, failure);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    LOGGER.debug("Could not remove incomplete tombstone temp file at {}", temporary,
                            cleanupFailure);
                }
            }
        }
    }

    /**
     * Loads every valid tombstone structurally owned by {@code ownerUuid}. Invalid files are ignored,
     * and no file is deleted or otherwise consumed by this operation.
     */
    public static List<Tombstone> loadForOwner(MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) {
            return List.of();
        }
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path ownerDirectory = OwnerCharacterStoragePaths.ownersRoot(worldRoot)
                .resolve(ownerUuid.toString());
        if (!Files.isDirectory(ownerDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }

        List<Tombstone> loaded = new ArrayList<>();
        try {
            for (Path characterDirectory : sortedChildren(ownerDirectory)) {
                if (!Files.isDirectory(characterDirectory, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String characterId = characterDirectory.getFileName().toString();
                if (!isSafeStableCharacterId(characterId)) {
                    continue;
                }
                Path tombstoneDirectory = characterDirectory.resolve(TOMBSTONE_DIRECTORY);
                if (!Files.isDirectory(tombstoneDirectory, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                for (Path file : sortedChildren(tombstoneDirectory)) {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    UUID dyingUuid = dyingUuidFromFile(file);
                    if (dyingUuid == null) {
                        continue;
                    }
                    Tombstone tombstone = readAndDecode(file, ownerUuid, characterId, dyingUuid);
                    if (tombstone != null) {
                        loaded.add(tombstone);
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not enumerate offline death tombstones for owner {} at {}",
                    ownerUuid, ownerDirectory, failure);
        }
        loaded.sort(Comparator
                .comparing(Tombstone::stableCharacterId)
                .thenComparing(tombstone -> tombstone.dyingUuid().toString()));
        return List.copyOf(loaded);
    }

    /** Deletes exactly this dying UUID's tombstone; a missing file returns {@code false}. */
    public static boolean delete(MinecraftServer server, Tombstone tombstone) {
        if (server == null || tombstone == null) {
            return false;
        }
        return deleteExact(
                server,
                tombstone.ownerUuid(),
                tombstone.stableCharacterId(),
                tombstone.dyingUuid());
    }

    /**
     * Deletes one exact structural key without loading the tombstone first. No sibling dying UUID is
     * inspected or removed.
     */
    public static boolean deleteExact(
            MinecraftServer server,
            UUID ownerUuid,
            String stableCharacterId,
            UUID dyingUuid) {
        if (server == null || ownerUuid == null || dyingUuid == null) {
            return false;
        }
        Path target = null;
        try {
            target = tombstonePath(
                    server.getWorldPath(LevelResource.ROOT),
                    ownerUuid,
                    stableCharacterId,
                    dyingUuid);
            return Files.deleteIfExists(target);
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not delete offline death tombstone at {}", target, failure);
            return false;
        }
    }

    static Path tombstonePath(Path worldRoot, Tombstone tombstone) {
        Objects.requireNonNull(tombstone, "tombstone");
        return tombstonePath(
                worldRoot,
                tombstone.ownerUuid(),
                tombstone.stableCharacterId(),
                tombstone.dyingUuid());
    }

    static Path tombstonePath(
            Path worldRoot, UUID ownerUuid, String stableCharacterId, UUID dyingUuid) {
        Objects.requireNonNull(worldRoot, "worldRoot");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(dyingUuid, "dyingUuid");
        String validatedId = requireStableCharacterId(stableCharacterId);
        return OwnerCharacterStoragePaths.ownerCharacterDir(worldRoot, ownerUuid, validatedId)
                .resolve(TOMBSTONE_DIRECTORY)
                .resolve(dyingUuid + DATA_SUFFIX);
    }

    static CompoundTag encode(Tombstone tombstone) {
        Objects.requireNonNull(tombstone, "tombstone");
        CompoundTag root = new CompoundTag();
        root.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        root.putString(KEY_OWNER_UUID, tombstone.ownerUuid().toString());
        root.put(KEY_CHARACTER, encodeCharacter(tombstone.character()));
        root.putString(KEY_STABLE_CHARACTER_ID, tombstone.stableCharacterId());
        root.putString(KEY_STABLE_CHARACTER_NAME, tombstone.stableCharacterName());
        root.putString(KEY_DYING_UUID, tombstone.dyingUuid().toString());
        root.put(KEY_STATE, tombstone.state());
        root.putBoolean(KEY_AUTO_RESPAWN, tombstone.autoRespawn());
        root.putBoolean(KEY_BOT_PERMADEATH, tombstone.botPermadeath());
        root.putBoolean(KEY_PERMADEATH_KILL, tombstone.permadeathKill());
        root.putString(KEY_LIFECYCLE_MODEL_NOTE, tombstone.lifecycleModelNote());
        root.putString(KEY_DEATH_CAUSE, tombstone.deathCause());
        return root;
    }

    /** Returns {@code null} unless stored identity exactly matches all three structural expectations. */
    static Tombstone decode(
            CompoundTag root,
            UUID expectedOwnerUuid,
            String expectedCharacterId,
            UUID expectedDyingUuid) {
        if (root == null
                || expectedOwnerUuid == null
                || expectedDyingUuid == null
                || !isSafeStableCharacterId(expectedCharacterId)
                || !root.contains(KEY_SCHEMA_VERSION, Tag.TAG_INT)
                || root.getInt(KEY_SCHEMA_VERSION) != SCHEMA_VERSION
                || !hasString(root, KEY_OWNER_UUID)
                || !root.contains(KEY_CHARACTER, Tag.TAG_COMPOUND)
                || !hasString(root, KEY_STABLE_CHARACTER_ID)
                || !hasString(root, KEY_STABLE_CHARACTER_NAME)
                || !hasString(root, KEY_DYING_UUID)
                || !root.contains(KEY_STATE, Tag.TAG_COMPOUND)
                || !hasString(root, KEY_LIFECYCLE_MODEL_NOTE)
                || !hasString(root, KEY_DEATH_CAUSE)) {
            return null;
        }

        UUID ownerUuid = parseCanonicalUuid(root.getString(KEY_OWNER_UUID));
        UUID dyingUuid = parseCanonicalUuid(root.getString(KEY_DYING_UUID));
        String stableCharacterId = root.getString(KEY_STABLE_CHARACTER_ID);
        String stableCharacterName = root.getString(KEY_STABLE_CHARACTER_NAME);
        Character character = decodeCharacter(root.getCompound(KEY_CHARACTER));
        Byte autoRespawn = strictBoolean(root, KEY_AUTO_RESPAWN);
        Byte botPermadeath = strictBoolean(root, KEY_BOT_PERMADEATH);
        Byte permadeathKill = strictBoolean(root, KEY_PERMADEATH_KILL);
        String lifecycleModelNote = root.getString(KEY_LIFECYCLE_MODEL_NOTE);
        String deathCause = root.getString(KEY_DEATH_CAUSE);

        if (!expectedOwnerUuid.equals(ownerUuid)
                || !expectedDyingUuid.equals(dyingUuid)
                || !expectedCharacterId.equals(stableCharacterId)
                || character == null
                || !stableCharacterId.equals(character.id())
                || !stableCharacterName.equals(character.name())
                || autoRespawn == null
                || botPermadeath == null
                || permadeathKill == null
                || !lifecycleModelNote.equals(boundedSingleLine(
                        lifecycleModelNote, MAX_LIFECYCLE_MODEL_NOTE_LENGTH))
                || !deathCause.equals(boundedSingleLine(deathCause, MAX_DEATH_CAUSE_LENGTH))) {
            return null;
        }

        try {
            return new Tombstone(
                    ownerUuid,
                    character,
                    stableCharacterId,
                    stableCharacterName,
                    dyingUuid,
                    root.getCompound(KEY_STATE),
                    autoRespawn != 0,
                    botPermadeath != 0,
                    permadeathKill != 0,
                    lifecycleModelNote,
                    deathCause);
        } catch (IllegalArgumentException | NullPointerException malformed) {
            return null;
        }
    }

    static String boundedSingleLine(String value, int maximumLength) {
        if (value == null || value.isEmpty() || maximumLength <= 0) {
            return "";
        }
        StringBuilder bounded = new StringBuilder(Math.min(value.length(), maximumLength));
        boolean pendingSpace = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += java.lang.Character.charCount(codePoint);
            if (java.lang.Character.isWhitespace(codePoint)
                    || java.lang.Character.isISOControl(codePoint)) {
                pendingSpace = bounded.length() > 0;
                continue;
            }
            int needed = java.lang.Character.charCount(codePoint) + (pendingSpace ? 1 : 0);
            if (bounded.length() + needed > maximumLength) {
                break;
            }
            if (pendingSpace) {
                bounded.append(' ');
                pendingSpace = false;
            }
            bounded.appendCodePoint(codePoint);
        }
        return bounded.toString();
    }

    private static Tombstone readAndDecode(
            Path file, UUID ownerUuid, String characterId, UUID dyingUuid) {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            return decode(NbtIo.read(input), ownerUuid, characterId, dyingUuid);
        } catch (IOException | RuntimeException malformed) {
            LOGGER.warn("Ignoring malformed offline death tombstone at {}", file, malformed);
            return null;
        }
    }

    private static List<Path> sortedChildren(Path directory) throws IOException {
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                children.add(child);
            }
        }
        children.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return children;
    }

    private static UUID dyingUuidFromFile(Path file) {
        String fileName = file.getFileName().toString();
        if (!fileName.endsWith(DATA_SUFFIX) || fileName.length() <= DATA_SUFFIX.length()) {
            return null;
        }
        return parseCanonicalUuid(fileName.substring(0, fileName.length() - DATA_SUFFIX.length()));
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static CompoundTag encodeCharacter(Character character) {
        CompoundTag encoded = new CompoundTag();
        putNullableString(encoded, KEY_CHARACTER_ID, character.id());
        putNullableString(encoded, KEY_CHARACTER_NAME, character.name());
        putNullableString(encoded, KEY_CHARACTER_SHORT_NAME, character.shortName());
        putNullableString(encoded, KEY_CHARACTER_GREETING_INFO, character.greetingInfo());
        putNullableString(encoded, KEY_CHARACTER_DESCRIPTION, character.description());
        putNullableString(encoded, KEY_CHARACTER_SKIN_URL, character.skinURL());

        String[] voiceIds = character.voiceIds();
        encoded.putBoolean(KEY_CHARACTER_VOICE_IDS + PRESENT_SUFFIX, voiceIds != null);
        if (voiceIds != null) {
            ListTag voices = new ListTag();
            for (String voiceId : voiceIds) {
                CompoundTag entry = new CompoundTag();
                putNullableString(entry, KEY_LIST_VALUE, voiceId);
                voices.add(entry);
            }
            encoded.put(KEY_CHARACTER_VOICE_IDS, voices);
        }
        return encoded;
    }

    private static Character decodeCharacter(CompoundTag encoded) {
        NullableString id = readNullableString(encoded, KEY_CHARACTER_ID);
        NullableString name = readNullableString(encoded, KEY_CHARACTER_NAME);
        NullableString shortName = readNullableString(encoded, KEY_CHARACTER_SHORT_NAME);
        NullableString greetingInfo = readNullableString(encoded, KEY_CHARACTER_GREETING_INFO);
        NullableString description = readNullableString(encoded, KEY_CHARACTER_DESCRIPTION);
        NullableString skinUrl = readNullableString(encoded, KEY_CHARACTER_SKIN_URL);
        Byte voicesPresent = strictBoolean(encoded, KEY_CHARACTER_VOICE_IDS + PRESENT_SUFFIX);
        if (!id.valid()
                || !name.valid()
                || !shortName.valid()
                || !greetingInfo.valid()
                || !description.valid()
                || !skinUrl.valid()
                || voicesPresent == null) {
            return null;
        }

        String[] voiceIds = null;
        if (voicesPresent != 0) {
            Tag voicesTag = encoded.get(KEY_CHARACTER_VOICE_IDS);
            if (!(voicesTag instanceof ListTag)) {
                return null;
            }
            ListTag voices = (ListTag) voicesTag;
            voiceIds = new String[voices.size()];
            for (int index = 0; index < voices.size(); index++) {
                Tag entryTag = voices.get(index);
                if (!(entryTag instanceof CompoundTag)) {
                    return null;
                }
                NullableString voiceId = readNullableString((CompoundTag) entryTag, KEY_LIST_VALUE);
                if (!voiceId.valid()) {
                    return null;
                }
                voiceIds[index] = voiceId.value();
            }
        } else if (encoded.contains(KEY_CHARACTER_VOICE_IDS)) {
            return null;
        }
        return new Character(
                id.value(),
                name.value(),
                shortName.value(),
                greetingInfo.value(),
                description.value(),
                skinUrl.value(),
                voiceIds);
    }

    private static void putNullableString(CompoundTag target, String key, String value) {
        target.putBoolean(key + PRESENT_SUFFIX, value != null);
        if (value != null) {
            target.putString(key, value);
        }
    }

    private static NullableString readNullableString(CompoundTag source, String key) {
        Byte present = strictBoolean(source, key + PRESENT_SUFFIX);
        if (present == null) {
            return NullableString.invalid();
        }
        if (present != 0) {
            return hasString(source, key)
                    ? new NullableString(true, source.getString(key))
                    : NullableString.invalid();
        }
        return source.contains(key) ? NullableString.invalid() : new NullableString(true, null);
    }

    private static Byte strictBoolean(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_BYTE)) {
            return null;
        }
        byte value = source.getByte(key);
        return value == 0 || value == 1 ? value : null;
    }

    private static boolean hasString(CompoundTag source, String key) {
        return source.contains(key, Tag.TAG_STRING);
    }

    private static UUID parseCanonicalUuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equals(value) ? parsed : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static String requireStableCharacterId(String characterId) {
        if (!isSafeStableCharacterId(characterId)) {
            throw new IllegalArgumentException("stableCharacterId must be a non-blank path segment");
        }
        return characterId;
    }

    private static boolean isSafeStableCharacterId(String characterId) {
        return characterId != null
                && !characterId.isBlank()
                && !characterId.equals(".")
                && !characterId.equals("..")
                && characterId.indexOf('/') < 0
                && characterId.indexOf('\\') < 0
                && characterId.indexOf('\0') < 0;
    }

    private static Character copyCharacter(Character character) {
        String[] voiceIds = character.voiceIds();
        return new Character(
                character.id(),
                character.name(),
                character.shortName(),
                character.greetingInfo(),
                character.description(),
                character.skinURL(),
                voiceIds == null ? null : Arrays.copyOf(voiceIds, voiceIds.length));
    }

    private record NullableString(boolean valid, String value) {
        static NullableString invalid() {
            return new NullableString(false, null);
        }
    }
}
