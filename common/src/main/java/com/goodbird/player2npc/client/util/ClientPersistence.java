package com.goodbird.player2npc.client.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import dev.architectury.platform.Platform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

public class ClientPersistence {
    private static final Path FILE = Platform.getConfigFolder()
            .resolve("player2NPC-client.dat");
    private static final Object LOCK = new Object();
    private static volatile boolean ttsHint;
    private static volatile boolean loaded;
    private static volatile boolean loadStarted;

    public static void preloadTTSStatus() {
        if (loaded || loadStarted) {
            return;
        }
        synchronized (LOCK) {
            if (loaded || loadStarted) {
                return;
            }
            loadStarted = true;
        }
        CompletableFuture.runAsync(ClientPersistence::loadTTSStatusFromDisk);
    }

    public static void saveTTSStatus(boolean flag) {
        synchronized (LOCK) {
            ttsHint = flag;
            loaded = true;
            loadStarted = true;
        }
        CompletableFuture.runAsync(() -> writeTTSStatus(flag));
    }

    public static boolean getTTStatus() {
        preloadTTSStatus();
        return loaded && ttsHint;
    }

    private static void writeTTSStatus(boolean flag) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("ttsHint", flag);
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(FILE))) {
            NbtIo.write(tag, out);
        } catch (Exception e) {
        }
    }

    private static void loadTTSStatusFromDisk() {
        boolean value = false;
        if (!Files.exists(FILE)) {
            completeLoad(value);
            return;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(FILE))) {
            CompoundTag tag = NbtIo.read(in);
            value = tag.getBoolean("ttsHint");
        } catch (Exception e) {
        }
        completeLoad(value);
    }

    private static void completeLoad(boolean value) {
        synchronized (LOCK) {
            if (!loaded) {
                ttsHint = value;
                loaded = true;
            }
        }
    }
}
