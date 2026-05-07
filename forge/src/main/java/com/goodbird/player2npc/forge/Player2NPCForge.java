package com.goodbird.player2npc.forge;

import com.goodbird.player2npc.Player2NPCClient;
import dev.architectury.platform.Platform;
import dev.architectury.platform.forge.EventBuses;
import dev.architectury.utils.Env;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.goodbird.player2npc.Player2NPC;

@Mod(Player2NPC.MOD_ID)
public final class Player2NPCForge {
    public Player2NPCForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(Player2NPC.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
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
