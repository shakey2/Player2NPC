package com.goodbird.player2npc.fabric.client;

import com.goodbird.player2npc.Player2NPCClient;
import com.goodbird.player2npc.fabric.debug.AgentDebugLog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class Player2NPCFabricClient implements ClientModInitializer {
    private static int agentTickSeq;

    @Override
    public void onInitializeClient() {
        // #region agent log
        AgentDebugLog.log("H1", "Player2NPCFabricClient.onInitializeClient", "fabric_client_init", "{}");
        ClientTickEvents.END_CLIENT_TICK.register(Player2NPCFabricClient::agentEndClientTick);
        // #endregion
        Player2NPCClient.onInitializeClient();
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
    }

    private static void agentEndClientTick(Minecraft mc) {
        // #region agent log
        agentTickSeq++;
        if (agentTickSeq % 40 == 1) {
            String screenName = mc.screen == null ? "null" : mc.screen.getClass().getName();
            AgentDebugLog.log("H1", "Player2NPCFabricClient.agentEndClientTick", "fabric_end_tick",
                    String.format("{\"tickSeq\":%d,\"screen\":\"%s\",\"levelNull\":%b,\"playerNull\":%b}",
                            agentTickSeq,
                            screenName.replace("\\", "\\\\").replace("\"", "\\\""),
                            mc.level == null,
                            mc.player == null));
        }
        // #endregion
    }
}
