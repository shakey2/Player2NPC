package com.goodbird.player2npc.network;

import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import com.goodbird.player2npc.companion.CompanionManager;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutomatoneSpawnRequestPacket {
   private static final Logger LOGGER = LogManager.getLogger();
    private final Character character;

    private AutomatoneSpawnRequestPacket(Character character) {
        this.character = character;
    }

    public AutomatoneSpawnRequestPacket(FriendlyByteBuf buf) {
        this.character = CharacterUtils.readFromBuf(buf);
    }

    public static RegistryFriendlyByteBuf create(RegistryAccess access, Character character) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        new AutomatoneSpawnRequestPacket(character).write(buf);
        return buf;
    }

    public void write(FriendlyByteBuf buf) {
        CharacterUtils.writeToBuf(buf, character);
    }

    public static void handle(FriendlyByteBuf var4, NetworkManager.PacketContext var5) {
        AutomatoneSpawnRequestPacket packet = new AutomatoneSpawnRequestPacket(var4);
        LOGGER.info("AutomatoneSpawnReqPacket C2S/ character={}", packet.character);
        if(packet.character != null){
            var5.queue(() -> CompanionManager.get((ServerPlayer) var5.getPlayer()).ensureCompanionExists(packet.character));
        }
    }
}
