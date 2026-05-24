package com.goodbird.player2npc.network;

import com.goodbird.player2npc.Player2NPC;
import com.goodbird.player2npc.companion.AutomatoneEntity;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class AutomatonEquipmentSyncPacket {
    private final int entityId;
    private final ListTag armorNbt;
    private final CompoundTag offhandNbt;
    private final int selectedSlot;
    private final CompoundTag mainHandNbt;

    private AutomatonEquipmentSyncPacket(AutomatoneEntity entity) {
        this.entityId = entity.getId();
        RegistryAccess access = entity.level().registryAccess();
        this.armorNbt = new ListTag();
        for (int i = 0; i < entity.inventory.armor.size(); i++) {
            CompoundTag itemTag = new CompoundTag();
            entity.inventory.armor.get(i).save(access, itemTag);
            this.armorNbt.add(itemTag);
        }
        CompoundTag offTag = new CompoundTag();
        entity.inventory.offHand.get(0).save(access, offTag);
        this.offhandNbt = offTag;
        this.selectedSlot = entity.inventory.selectedSlot;
        this.mainHandNbt = new CompoundTag();
        entity.inventory.getMainHandStack().save(access, this.mainHandNbt);
    }

    public AutomatonEquipmentSyncPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        CompoundTag root = buf.readNbt();
        if (root != null && root.contains("armor", Tag.TAG_LIST)) {
            this.armorNbt = root.getList("armor", Tag.TAG_COMPOUND);
        } else {
            this.armorNbt = new ListTag();
        }
        if (root != null && root.contains("offhand", Tag.TAG_COMPOUND)) {
            this.offhandNbt = root.getCompound("offhand");
        } else {
            this.offhandNbt = new CompoundTag();
        }
        if (root != null && root.contains("selectedSlot", Tag.TAG_INT)) {
            this.selectedSlot = root.getInt("selectedSlot");
        } else {
            this.selectedSlot = 0;
        }
        if (root != null && root.contains("mainHand", Tag.TAG_COMPOUND)) {
            this.mainHandNbt = root.getCompound("mainHand");
        } else {
            this.mainHandNbt = new CompoundTag();
        }
    }

    public static void broadcast(AutomatoneEntity entity) {
        if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(entity) <= 64.0 * 64.0) {
                RegistryFriendlyByteBuf playerBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), serverLevel.registryAccess());
                new AutomatonEquipmentSyncPacket(entity).write(serverLevel.registryAccess(), playerBuf);
                NetworkManager.sendToPlayer(player, Player2NPC.EQUIP_SYNC_PACKET_ID, playerBuf);
            }
        }
    }

    public void write(RegistryAccess access, FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        CompoundTag root = new CompoundTag();
        root.put("armor", this.armorNbt);
        root.put("offhand", this.offhandNbt);
        root.putInt("selectedSlot", this.selectedSlot);
        root.put("mainHand", this.mainHandNbt);
        buf.writeNbt(root);
    }

    public int getEntityId() {
        return entityId;
    }

    public void applyToClientEntity(AutomatoneEntity entity, RegistryAccess access) {
        for (int i = 0; i < this.armorNbt.size() && i < entity.inventory.armor.size(); i++) {
            CompoundTag itemTag = this.armorNbt.getCompound(i);
            entity.inventory.armor.set(i, ItemStack.parseOptional(access, itemTag));
        }
        entity.inventory.offHand.set(0, ItemStack.parseOptional(access, this.offhandNbt));
        if (this.selectedSlot >= 0 && this.selectedSlot < entity.inventory.main.size()) {
            entity.inventory.selectedSlot = this.selectedSlot;
            entity.inventory.main.set(this.selectedSlot, ItemStack.parseOptional(access, this.mainHandNbt));
        }
    }
}
