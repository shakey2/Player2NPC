//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.goodbird.player2npc.network;

import com.player2.playerengine.player2api.Character;
import com.goodbird.player2npc.companion.CompanionManager;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class AutomatoneDespawnRequestPacket {
    private final Character character;

    private AutomatoneDespawnRequestPacket(Character character) {
        this.character = character;
    }

    public AutomatoneDespawnRequestPacket(FriendlyByteBuf buf) {
        this.character = CharacterUtils.readFromBuf(buf);
    }

    public static RegistryFriendlyByteBuf create(RegistryAccess access, Character character) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        (new AutomatoneDespawnRequestPacket(character)).write(buf);
        return buf;
    }

    public void write(FriendlyByteBuf buf) {
        CharacterUtils.writeToBuf(buf, this.character);
    }

    public static void handle(FriendlyByteBuf var4, NetworkManager.PacketContext var5) {
        AutomatoneDespawnRequestPacket packet = new AutomatoneDespawnRequestPacket(var4);
        var5.queue(() -> (CompanionManager.get((ServerPlayer) var5.getPlayer())).dismissCompanion(packet.character.name()));
    }
}
