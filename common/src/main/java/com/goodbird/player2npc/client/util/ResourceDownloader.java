//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.goodbird.player2npc.client.util;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;

public class ResourceDownloader {
    private static final Set<ResourceLocation> active = Collections.synchronizedSet(new HashSet());
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public ResourceDownloader() {
    }

    public static void load(ImageDownloadAlt resource) {
        if (!active.contains(resource.location)) {
            active.add(resource.location);
            executor.execute(() -> {
                resource.loadTextureFromServer();
                Minecraft.getInstance().submit(() -> {
                    Minecraft.getInstance().getTextureManager().register(resource.location, resource);
                    active.remove(resource.location);
                });

                try {
                    Thread.sleep(400L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            });
        }

    }

    public static ResourceLocation getUrlResourceLocation(String url, boolean fixSkin) {
        String var10002 = "cnpcaicompanion";
        int var10003 = (url + fixSkin).hashCode();
        return new ResourceLocation(var10002, "skins/" + var10003 + (fixSkin ? "" : "32"));
    }

    public static File getUrlFile(String url, boolean fixSkin) {
        File var10002;
        try {
            var10002 = (File)SkinManager.class.getField("skinCacheDir").get(Minecraft.getInstance().getSkinManager());
        } catch (Exception var4) {
            var10002 = new File(Minecraft.getInstance().gameDirectory, "cache");
        }

        String var10003 = url + fixSkin;
        return new File(var10002, "" + var10003.hashCode());
    }

    public static boolean contains(ResourceLocation location) {
        return active.contains(location);
    }
}
