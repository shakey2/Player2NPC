package com.goodbird.player2npc;

import com.goodbird.player2npc.client.gui.CharacterSelectionScreen;
import com.goodbird.player2npc.client.gui.SttConsentScreen;
import com.goodbird.player2npc.client.render.RenderAutomaton;
import com.goodbird.player2npc.client.util.ClientPersistence;
import com.goodbird.player2npc.network.AutomatonEquipmentSyncPacket;
import com.goodbird.player2npc.network.AutomatonSpawnPacket;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.player2.playerengine.PlayerEngineClient;
import com.player2.playerengine.player2api.ChatclefConfigPersistantState;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import com.goodbird.player2npc.companion.AutomatoneEntity;
import com.player2.playerengine.player2api.utils.STTUtils;

public class Player2NPCClient {
    private static KeyMapping openCharacterScreenKeybind;
    private static KeyMapping ttsEnableKeybind;
    private static KeyMapping sttKeybind;
    private static long lastHeartbeatTime = System.nanoTime();
    private static boolean sttWasDown = false;

    public Player2NPCClient() {
    }

    public static void onInitializeClient() {
        EntityRendererRegistry.register(Player2NPC.AUTOMATONE, RenderAutomaton::new);
        // https://www.glfw.org/docs/3.3/group__keys.html
        // 72 => H
        openCharacterScreenKeybind = new KeyMapping("key.player2npc.open_character_screen", Type.KEYSYM, 72,
                "category.player2npc.keys");
        // 79 => O
        ttsEnableKeybind = new KeyMapping("key.player2npc.tts_toggle", Type.KEYSYM, 79,
                "category.player2npc.keys");
        // 86 => V
        sttKeybind = new KeyMapping("key.player2npc.stt_toggle", Type.KEYSYM, 86, "category.player2npc.keys");
        KeyMappingRegistry.register(openCharacterScreenKeybind);
        KeyMappingRegistry.register(ttsEnableKeybind);
        KeyMappingRegistry.register(sttKeybind);
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, Player2NPC.SPAWN_PACKET_ID, (buf, context) -> {
            AutomatonSpawnPacket packet = new AutomatonSpawnPacket(buf);
            context.queue(() -> {
                net.minecraft.client.multiplayer.ClientLevel world = (net.minecraft.client.multiplayer.ClientLevel) context.getPlayer().level();
                AutomatoneEntity entity = new AutomatoneEntity(Player2NPC.AUTOMATONE.get(), world);
                entity.setId(packet.getId());
                entity.setUUID(packet.getUuid());
                entity.syncPacketPositionCodec(packet.getPos().x, packet.getPos().y, packet.getPos().z);
                entity.moveTo(packet.getPos().x, packet.getPos().y, packet.getPos().z);
                entity.setDeltaMovement(packet.getVelocity());
                entity.setXRot(packet.getPitch());
                entity.setYRot(packet.getYaw());
                entity.setCharacter(packet.getCharacter());
                packet.getInventory().player = entity;
                entity.inventory = packet.getInventory();
                world.addEntity(entity);
            });
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, Player2NPC.EQUIP_SYNC_PACKET_ID, (buf, context) -> {
            AutomatonEquipmentSyncPacket packet = new AutomatonEquipmentSyncPacket(buf);
            context.queue(() -> {
                if (!(context.getPlayer().level() instanceof net.minecraft.client.multiplayer.ClientLevel world)) {
                    return;
                }
                if (world.getEntity(packet.getEntityId()) instanceof AutomatoneEntity entity) {
                    packet.applyToClientEntity(entity, world.registryAccess());
                }
            });
        });
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register((player) -> {
            if (!ClientPersistence.getTTStatus()) {
                player.sendSystemMessage(Component.literal("Welcome to Player2NPC!"));
                player.sendSystemMessage(Component.literal(
                        "To spawn a companion, press H. (Make sure you have at least one selected character at player2.game)"));
                player.sendSystemMessage(Component.literal("To toggle text to speech (TTS), press ")
                        .append(Component.keybind("key.player2npc.tts_toggle")));
                player.sendSystemMessage(Component.translatable("screen.player2npc.stt_consent.welcome_hint",
                        Component.keybind("key.player2npc.stt_toggle")));
                ClientPersistence.saveTTSStatus(true);
            }
            PlayerEngineClient.syncTtsPreferenceToServer();
        });
        ClientTickEvent.CLIENT_POST.register((client) -> {
            if (openCharacterScreenKeybind.consumeClick() && client.level != null) {
                client.setScreen(new CharacterSelectionScreen());
            }
            if (ttsEnableKeybind.consumeClick()) {
                PlayerEngineClient.setTtsEnabled(!PlayerEngineClient.isTtsEnabled());
                client.player.sendSystemMessage(
                        Component.literal(PlayerEngineClient.isTtsEnabled() ? "Enabled TTS" : "Disabled TTS"));
            }

            boolean sttIsDown = sttKeybind.isDown();
            if (ChatclefConfigPersistantState.canUseStt()) {
                if (sttIsDown && !sttWasDown) {
                    STTUtils.setIsListening(true, AutomatoneEntity.PLAYER2_GAME_ID);
                } else if (!sttIsDown && sttWasDown) {
                    STTUtils.setIsListening(false, AutomatoneEntity.PLAYER2_GAME_ID);
                }
            } else {
                if (sttIsDown && !sttWasDown && client.screen == null) {
                    client.setScreen(new SttConsentScreen(null));
                }
                if (!sttIsDown && sttWasDown) {
                    STTUtils.setIsListening(false, AutomatoneEntity.PLAYER2_GAME_ID);
                }
            }
            sttWasDown = sttIsDown;
        });
        ClientTickEvent.CLIENT_PRE.register((client) -> {
            STTUtils.update();
        });
    }
}
