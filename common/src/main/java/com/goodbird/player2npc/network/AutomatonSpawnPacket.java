//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.goodbird.player2npc.network;

import com.player2.playerengine.player2api.Character;
import com.goodbird.player2npc.Player2NPC;
import com.goodbird.player2npc.companion.AutomatoneEntity;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.SpawnEntityPacket;
import io.netty.buffer.Unpooled;
import java.util.UUID;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class AutomatonSpawnPacket {
    private final int id;
    private final UUID uuid;
    private final Vec3 pos;
    private final Vec3 velocity;
    private final float pitch;
    private final float yaw;
    private final Character character;
    private final LivingEntityInventory inventory;

    private AutomatonSpawnPacket(AutomatoneEntity entity) {
        this.id = entity.getId();
        this.uuid = entity.getUUID();
        this.pos = entity.position();
        this.velocity = entity.getDeltaMovement();
        this.pitch = entity.getXRot();
        this.yaw = entity.getYRot();
        this.character = entity.getCharacter();
        this.inventory = entity.inventory;
    }

    public AutomatonSpawnPacket(RegistryFriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.uuid = buf.readUUID();
        this.pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.velocity = new Vec3((double)buf.readShort(), (double)buf.readShort(), (double)buf.readShort());
        this.pitch = (float)(buf.readByte() * 360) / 256.0F;
        this.yaw = (float)(buf.readByte() * 360) / 256.0F;
        this.character = CharacterUtils.readFromBuf(buf);
        this.inventory = new LivingEntityInventory((LivingEntity)null);
        this.inventory.readNbt(buf.registryAccess(), buf.readNbt().getList("inv", 10));
    }

    public static Packet<ClientGamePacketListener> create(RegistryAccess access, AutomatoneEntity entity) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        (new AutomatonSpawnPacket(entity)).write(access, buf);
        return (Packet<ClientGamePacketListener>) NetworkManager.toPacket(NetworkManager.Side.S2C, Player2NPC.SPAWN_PACKET_ID, buf);
    }

    public void write(RegistryAccess access, FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeUUID(this.uuid);
        buf.writeDouble(this.pos.x);
        buf.writeDouble(this.pos.y);
        buf.writeDouble(this.pos.z);
        buf.writeShort((int)(Math.min(3.9, this.velocity.x) * (double)8000.0F));
        buf.writeShort((int)(Math.min(3.9, this.velocity.y) * (double)8000.0F));
        buf.writeShort((int)(Math.min(3.9, this.velocity.z) * (double)8000.0F));
        buf.writeByte((byte)((int)(this.pitch * 256.0F / 360.0F)));
        buf.writeByte((byte)((int)(this.yaw * 256.0F / 360.0F)));
        CharacterUtils.writeToBuf(buf, this.character);
        CompoundTag compound = new CompoundTag();
        compound.put("inv", this.inventory.writeNbt(access, new ListTag()));
        buf.writeNbt(compound);
    }

    public static void handle(RegistryFriendlyByteBuf var3, NetworkManager.PacketContext var4) {
        AutomatonSpawnPacket packet = new AutomatonSpawnPacket(var3);
        var4.queue(() -> {
            ClientLevel world = (ClientLevel) var4.getPlayer().level();
            AutomatoneEntity entity = new AutomatoneEntity(Player2NPC.AUTOMATONE.get(), world);
            entity.setId(packet.id);
            entity.setUUID(packet.uuid);
            entity.syncPacketPositionCodec(packet.pos.x, packet.pos.y, packet.pos.z);
            entity.moveTo(packet.pos.x, packet.pos.y, packet.pos.z);
            entity.setDeltaMovement(packet.velocity);
            entity.setXRot(packet.pitch);
            entity.setYRot(packet.yaw);
            entity.setCharacter(packet.character);
            packet.inventory.player = entity;
            entity.inventory = packet.inventory;
            world.addEntity(entity);
        });
    }
}
