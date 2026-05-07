package com.goodbird.player2npc.fabric;

import com.goodbird.player2npc.Player2NPC;
import net.fabricmc.api.ModInitializer;

public final class Player2NPCFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        Player2NPC.onInitialize();
    }
}
