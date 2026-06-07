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

import net.minecraft.world.entity.EquipmentSlot;

import net.minecraft.world.item.ItemStack;



public final class AutomatonEquipmentSyncPacket {

    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[] {

            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD

    };



    private final int entityId;

    private final ListTag armorNbt;

    private final CompoundTag offhandNbt;

    private final int selectedSlot;

    private final CompoundTag mainHandNbt;



    private AutomatonEquipmentSyncPacket(AutomatoneEntity entity) {

        this.entityId = entity.getId();

        RegistryAccess access = entity.level().registryAccess();

        this.armorNbt = new ListTag();

        for (EquipmentSlot slot : ARMOR_SLOTS) {

            this.armorNbt.add(saveStackForSync(access, entity.getItemBySlot(slot)));

        }

        this.offhandNbt = saveStackForSync(access, entity.getItemBySlot(EquipmentSlot.OFFHAND));

        this.selectedSlot = entity.inventory.selectedSlot;

        this.mainHandNbt = saveStackForSync(access, entity.getItemBySlot(EquipmentSlot.MAINHAND));

    }



    /** MC 1.21+ rejects {@link ItemStack#save} on empty stacks; client uses {@link ItemStack#parseOptional}. */

    private static CompoundTag saveStackForSync(RegistryAccess access, ItemStack stack) {

        CompoundTag tag = new CompoundTag();

        if (stack != null && !stack.isEmpty()) {

            stack.save(access, tag);

        }

        return tag;

    }



    public AutomatonEquipmentSyncPacket(FriendlyByteBuf buf) {

        this.entityId = buf.readVarInt();

        CompoundTag root = buf.readNbt();

        if (root != null && root.contains("armor", Tag.TAG_LIST)) {

            this.armorNbt = root.getList("armor", Tag.TAG_COMPOUND);

        } else {

            this.armorNbt = new ListTag();

        }

        while (this.armorNbt.size() < ARMOR_SLOTS.length) {

            this.armorNbt.add(new CompoundTag());

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

        AutomatonEquipmentSyncPacket packet = new AutomatonEquipmentSyncPacket(entity);

        RegistryAccess access = serverLevel.registryAccess();

        for (ServerPlayer player : serverLevel.players()) {

            if (player.distanceToSqr(entity) <= 64.0 * 64.0) {

                RegistryFriendlyByteBuf playerBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);

                packet.write(access, playerBuf);

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



    /**

     * Applies a full equipment snapshot on the client. Uses {@link AutomatoneEntity#setItemSlot} so armor

     * routing matches the server (same paths {@link net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer} reads).

     */

    public void applyToClientEntity(AutomatoneEntity entity, RegistryAccess access) {

        for (int i = 0; i < ARMOR_SLOTS.length; i++) {

            EquipmentSlot slot = ARMOR_SLOTS[i];

            CompoundTag itemTag = i < this.armorNbt.size() ? this.armorNbt.getCompound(i).copy() : new CompoundTag();

            entity.setItemSlot(slot, parseStack(access, itemTag));

        }

        entity.setItemSlot(EquipmentSlot.OFFHAND, parseStack(access, this.offhandNbt));

        if (this.selectedSlot >= 0 && this.selectedSlot < entity.inventory.main.size()) {

            entity.inventory.selectedSlot = this.selectedSlot;

        }

        entity.setItemSlot(EquipmentSlot.MAINHAND, parseStack(access, this.mainHandNbt));

    }



    private static ItemStack parseStack(RegistryAccess access, CompoundTag tag) {

        if (tag == null || tag.isEmpty()) {

            return ItemStack.EMPTY;

        }

        CompoundTag copy = tag.copy();

        copy.remove("Slot");

        return ItemStack.parseOptional(access, copy);

    }

}


