package com.goodbird.player2npc.fabric.client;

import com.goodbird.player2npc.Player2NPCClient;
import net.fabricmc.api.ClientModInitializer;

public final class Player2NPCFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Player2NPCClient.onInitializeClient();
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
    }
}
