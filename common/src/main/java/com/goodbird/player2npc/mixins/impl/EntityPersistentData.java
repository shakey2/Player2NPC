package com.goodbird.player2npc.mixins.impl;

import com.goodbird.player2npc.mixins.IEntityPersistentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityPersistentData implements IEntityPersistentData {
    @Unique
    private CompoundTag CNPC_tag;

    @Unique
    public CompoundTag getPersistentData(){
        if(CNPC_tag==null){
            CNPC_tag = new CompoundTag();
        }
        return CNPC_tag;
    }

    @Inject(method = "saveWithoutId",at=@At("TAIL"))
    public void save(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir){
        if(CNPC_tag!=null && ((Object)this) instanceof LivingEntity) {
            compound.put("Player2NPC_persistantData", CNPC_tag);
        }
    }

    @Inject(method = "load",at=@At("TAIL"))
    public void read(CompoundTag compound, CallbackInfo ci){
        if(compound.contains("Player2NPC_persistantData") && ((Object)this) instanceof LivingEntity) {
            CNPC_tag = compound.getCompound("Player2NPC_persistantData");
        }
    }
}
