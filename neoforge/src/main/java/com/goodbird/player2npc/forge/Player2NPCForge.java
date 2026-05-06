package com.goodbird.player2npc.forge;

import com.goodbird.player2npc.Player2NPCClient;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;

import com.goodbird.player2npc.Player2NPC;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Player2NPC.MOD_ID)
public final class Player2NPCForge {
    public Player2NPCForge(IEventBus modEventBus, ModContainer modContainer) {
        // Submit our event bus to let Architectury API register our content on the right time.
        //FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        //FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        Player2NPC.onInitialize();
        if(Platform.getEnvironment()== Env.CLIENT){
            Player2NPCClient.onInitializeClient();
        }
    }

//    private void setup(final FMLCommonSetupEvent event) {
//
//    }

//    private void clientSetup(final FMLClientSetupEvent event) {
//
//    }
}
