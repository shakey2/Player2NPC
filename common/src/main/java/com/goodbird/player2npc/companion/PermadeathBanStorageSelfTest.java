package com.goodbird.player2npc.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/** Server-free filesystem regression checks for fail-closed permadeath-ban merging. */
public final class PermadeathBanStorageSelfTest {
    private PermadeathBanStorageSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path testRoot = Files.createTempDirectory("player2npc-permadeath-bans-");
        try {
            missingFileIsCreated(testRoot);
            validFileIsMerged(testRoot);
            corruptFileIsPreserved(testRoot);
            fractionalSchemaFileIsPreserved(testRoot);
        } finally {
            deleteTree(testRoot);
        }
    }

    private static void missingFileIsCreated(Path testRoot) throws IOException {
        Path path = testRoot.resolve("missing.json");
        PermadeathBanStorage.DiskLoadReceipt before =
                PermadeathBanStorage.loadDisk(path);
        require(before.status() == PermadeathBanStorage.DiskLoadStatus.MISSING,
                "a genuinely absent ban file must have a typed missing receipt");

        PermadeathBanStorage.persistMerged(path, "new-character");

        PermadeathBanStorage.DiskLoadReceipt after =
                PermadeathBanStorage.loadDisk(path);
        require(after.status() == PermadeathBanStorage.DiskLoadStatus.VALID
                        && after.characterIds().equals(Set.of("new-character")),
                "a missing ban file must be created with the new terminal identity");
    }

    private static void validFileIsMerged(Path testRoot) throws IOException {
        Path path = testRoot.resolve("valid.json");
        PermadeathBanStorage.writeDiskAtomically(
                path, new LinkedHashSet<>(Set.of("existing-character")));

        PermadeathBanStorage.persistMerged(path, "new-character");

        PermadeathBanStorage.DiskLoadReceipt receipt =
                PermadeathBanStorage.loadDisk(path);
        require(receipt.status() == PermadeathBanStorage.DiskLoadStatus.VALID
                        && receipt.characterIds().equals(
                        Set.of("existing-character", "new-character")),
                "a valid ban file must preserve existing identities while adding the new one");
    }

    private static void corruptFileIsPreserved(Path testRoot) throws IOException {
        Path path = testRoot.resolve("corrupt.json");
        Files.writeString(path, "{ definitely-not-valid-json", StandardCharsets.UTF_8);
        byte[] originalBytes = Files.readAllBytes(path);
        PermadeathBanStorage.DiskLoadReceipt receipt =
                PermadeathBanStorage.loadDisk(path);
        require(receipt.status() == PermadeathBanStorage.DiskLoadStatus.INVALID,
                "an existing corrupt ban file must have a typed invalid receipt");

        boolean refused = false;
        try {
            PermadeathBanStorage.persistMerged(path, "must-remain-volatile");
        } catch (IOException expected) {
            refused = true;
        }

        require(refused, "merging must refuse to overwrite an invalid existing ban file");
        require(Arrays.equals(originalBytes, Files.readAllBytes(path)),
                "a refused merge must preserve the corrupt file byte-for-byte");
    }

    private static void fractionalSchemaFileIsPreserved(Path testRoot) throws IOException {
        Path path = testRoot.resolve("fractional-schema.json");
        Files.writeString(
                path,
                "{\"schemaVersion\":1.5,\"bannedCharacterIds\":[\"existing-character\"]}",
                StandardCharsets.UTF_8);
        byte[] originalBytes = Files.readAllBytes(path);
        PermadeathBanStorage.DiskLoadReceipt receipt =
                PermadeathBanStorage.loadDisk(path);
        require(receipt.status() == PermadeathBanStorage.DiskLoadStatus.INVALID,
                "a fractional schema must not be truncated into the supported integer schema");

        boolean refused = false;
        try {
            PermadeathBanStorage.persistMerged(path, "must-remain-volatile");
        } catch (IOException expected) {
            refused = true;
        }

        require(refused, "merging must refuse a fractional schema version");
        require(Arrays.equals(originalBytes, Files.readAllBytes(path)),
                "a refused fractional-schema merge must preserve the file byte-for-byte");
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
