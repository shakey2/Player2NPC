//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.goodbird.player2npc.companion;

import com.player2.playerengine.player2api.Character;
import com.goodbird.player2npc.Player2NPC;
import com.goodbird.player2npc.network.AutomatonEquipmentSyncPacket;
import com.goodbird.player2npc.network.AutomatonSpawnPacket;
import com.player2.playerengine.FollowMode;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.entity.IAutomatone;
import com.player2.playerengine.automaton.api.entity.IHungerManagerProvider;
import com.player2.playerengine.automaton.api.entity.IInteractionManagerProvider;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInteractionManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.multiversion.equip.EquipVer;
import com.player2.playerengine.multiversion.equip.WeaponVer;
import com.player2.playerengine.player2api.BotLifecycleSettings;
import com.player2.playerengine.player2api.BotLifecycleSettingsResolver;
import com.player2.playerengine.player2api.KeepInventoryResolver;
import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;

import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutomatoneEntity extends LivingEntity implements IAutomatone, IInventoryProvider, IInteractionManagerProvider, IHungerManagerProvider {
    private static final Logger LOGGER = LogManager.getLogger();
    public LivingEntityInteractionManager manager;
    public LivingEntityInventory inventory;
    public LivingEntityHungerManager hungerManager;
    public PlayerEngineController controller;
    public Character character;
    public ResourceLocation textureLocation;
    protected Vec3 lastVelocity;
    /**
     * Owner UUID mirrored in memory so it is available at {@link #die(DamageSource)} time even when the
     * owner is offline (then {@code controller.getOwner()} returns null). Populated from init/the spawn
     * constructor, from {@code owner_uuid} NBT in {@link #readAdditionalSaveData(CompoundTag)}, and from
     * {@link #reattachOwner(Player)}. It also supplies stable identity reconciliation while the owner
     * is offline, so every save must retain it even when {@code controller.getOwner()} is null.
     */
    private UUID ownerUuid;
    /** True only while the exact online mapped canonical is inside vanilla death-loot handling. */
    private boolean canonicalDeathDropAuthorized;
    /**
     * Persisted follow mode (WS2). Parsed in {@link #readAdditionalSaveData(CompoundTag)} and applied to the
     * controller on whichever path builds it: the read-path construction (cold load) applies it immediately,
     * while the spawn/teleport path applies it at the end of {@link #init(Player, SpawnReason, String)} (the
     * controller does not exist when the NBT is read on that path). Defaults to {@code NORMAL} for fresh
     * spawns and legacy/unknown saves.
     */
    private FollowMode pendingFollowMode = FollowMode.NORMAL;
    public static final String PLAYER2_GAME_ID = "player2-ai-npc-minecraft";

    public AutomatoneEntity(EntityType<? extends AutomatoneEntity> type, Level world) {
        super(type, world);
        this.init();
    }

    public void init() {
        init(null, SpawnReason.RETURNING, null);
    }

    public void init(Player companionOwner) {
        init(companionOwner, SpawnReason.RETURNING, null);
    }

    /**
     * @param companionOwner when non-null, set on the controller before inventory/history load so owner-scoped paths resolve.
     * @param reason         the spawn scenario; drives the single greeting/return/death dispatch below.
     * @param deathCause     vanilla localized death message, only used for {@link SpawnReason#DEATH_RESPAWN}.
     */
    public void init(Player companionOwner, SpawnReason reason, String deathCause) {
        init(companionOwner, reason, deathCause, false);
    }

    private void init(
            Player companionOwner,
            SpawnReason reason,
            String deathCause,
            boolean inventoryStateManagedExternally) {
        this.setMaxUpStep(0.6F);
        this.setSpeed(0.4F);
        this.manager = new LivingEntityInteractionManager(this);
        this.inventory = new LivingEntityInventory(this);
        this.hungerManager = new LivingEntityHungerManager();
        if (!this.level().isClientSide && this.character != null) {
            this.controller = new PlayerEngineController((IBaritone)IBaritone.KEY.get(this), this.character,
                    "player2-ai-npc-minecraft", companionOwner);
            if (companionOwner != null) {
                this.ownerUuid = companionOwner.getUUID();
            }
            // Single greeting decision point (Q5 option (a)): FIRST_MEETING and RETURNING are handled
            // identically here -- both call sendReturnMessage, which greets internally when the
            // per-world history file does not yet exist (true first meeting), else emits "<owner> has
            // respawned you". (No call site passes FIRST_MEETING today; see SpawnReason javadoc.)
            // DEATH_RESPAWN injects the death cause so the revived bot answers truthfully (DESIGN.md §3).
            if (!inventoryStateManagedExternally) {
                PersistentDataManager.loadInventory(this);
            switch (reason) {
                case FIRST_MEETING:
                case RETURNING:
                    ConversationManager.sendReturnMessage(this.controller, this.character,
                            ownerDisplayName(companionOwner));
                    break;
                case DEATH_RESPAWN:
                    ConversationManager.sendDeathRevival(this.controller, this.character, deathCause);
                    break;
                case DIMENSION_RELOCATE:
                    // Intentionally silent. A cross-dimension relocate (dismiss+respawn because MC
                    // moveTo cannot cross levels) must NOT emit a greeting/return message — otherwise
                    // walking through a portal would spam "<owner> has respawned you" every time.
                    break;
            }
            }
        }

    }

    /**
     * Resolve a model-facing owner display name without ever using {@code controller.getOwnerUsername()},
     * which returns the sentinel "UNKNOWN OWNER" when no owner is attached (Q6/B1). Null owner -> "Your owner".
     */
    private String ownerDisplayName(Player companionOwner) {
        if (companionOwner != null) {
            return companionOwner.getName().getString();
        }
        if (this.controller != null && this.controller.getOwner() instanceof ServerPlayer sp) {
            return sp.getName().getString();
        }
        return "Your owner";
    }

    /** Called by CompanionManager only after synchronous state application and accepted world spawn. */
    void finishManagedSpawnInitialization(
            SpawnReason reason, String deathCause, Player companionOwner) {
        this.dispatchSpawnLifecycleMessage(reason, deathCause, companionOwner);
    }

    private void dispatchSpawnLifecycleMessage(
            SpawnReason reason, String deathCause, Player companionOwner) {
        if (this.controller == null || this.character == null || reason == null) {
            return;
        }
        switch (reason) {
            case FIRST_MEETING:
            case RETURNING:
                ConversationManager.sendReturnMessage(
                        this.controller, this.character, ownerDisplayName(companionOwner));
                break;
            case DEATH_RESPAWN:
                ConversationManager.sendDeathRevival(
                        this.controller, this.character, deathCause);
                break;
            case DIMENSION_RELOCATE:
                break;
        }
    }

    public AutomatoneEntity(Level world, Character character, Player owner) {
        this(world, character, owner, SpawnReason.RETURNING, null);
    }

    public AutomatoneEntity(Level world, Character character, Player owner, SpawnReason reason, String deathCause) {
        this(world, character, owner, reason, deathCause, false);
    }

    AutomatoneEntity(
            Level world,
            Character character,
            Player owner,
            SpawnReason reason,
            String deathCause,
            boolean inventoryStateManagedExternally) {
        super(Player2NPC.AUTOMATONE.get(), world);
        this.setCharacter(character);
        this.init(owner, reason, deathCause, inventoryStateManagedExternally);
    }

    public LivingEntityInventory getLivingInventory() {
        return this.inventory;
    }

    public LivingEntityInteractionManager getInteractionManager() {
        return this.manager;
    }

    public LivingEntityHungerManager getHungerManager() {
        return this.hungerManager;
    }

    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.hungerManager.readNbt(tag);
        if (tag.contains("head_yaw")) {
            this.yHeadRot = tag.getFloat("head_yaw");
        }

        ListTag nbtList = tag.getList("Inventory", 10);
        this.inventory.readNbt(nbtList);
        this.inventory.selectedSlot = tag.getInt("SelectedItemSlot");
        if (this.character == null && tag.contains("character")) {
            CompoundTag compound = tag.getCompound("character");
            this.character = CharacterUtils.readFromNBT(compound);
            if (this.controller == null) {
                this.controller = new PlayerEngineController((IBaritone)IBaritone.KEY.get(this), this.character, "player2-ai-npc-minecraft");
            }

            // Q2 = SILENT: restart / dimension-change / chunk-reload must emit NOTHING here. This path
            // fires on every owner-far chunk reload and has no owner attached at this point (owner
            // resolution is the block below), so any send would be spurious or a garbage owner name.
            // The genuine "owner respawned you" return message fires only from the owner-online SPAWN
            // call sites (CompanionManager.spawnCompanion -> init RETURNING).
        }

        // Restore controller owner from persisted UUID when possible. Owner may be offline; if so,
        // leave unset and CompanionManager.ensureCompanionExists (teleport branch) reattaches when they rejoin.
        if (!this.level().isClientSide && tag.hasUUID("owner_uuid")) {
            UUID ownerUuid = tag.getUUID("owner_uuid");
            // Mirror in memory unconditionally so die() can write a permadeath ban even if the owner is
            // offline and the controller never gets reattached.
            this.ownerUuid = ownerUuid;
            MinecraftServer srv = this.level().getServer();
            if (this.controller != null && srv != null) {
                ServerPlayer ownerPlayer = srv.getPlayerList().getPlayer(ownerUuid);
                if (ownerPlayer != null) {
                    this.controller.setOwner(ownerPlayer);
                }
            }
        }

        // WS2: restore persisted follow mode. Parse always (the only method handed the CompoundTag),
        // guarding legacy/unknown values to NORMAL. Apply immediately if the controller already exists
        // (cold-load read-path construction above); otherwise pendingFollowMode is applied when init(...)
        // builds the controller on the spawn/teleport path.
        FollowMode restored = FollowMode.NORMAL;
        if (tag.contains("follow_mode")) {
            String raw = tag.getString("follow_mode");
            try {
                restored = FollowMode.valueOf(raw);
            } catch (IllegalArgumentException ignored) {
                // legacy/unknown -> NORMAL
            }
        }
        this.pendingFollowMode = restored;
        if (this.controller != null) {
            this.controller.setFollowMode(this.pendingFollowMode);
        }
    }

    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.hungerManager.writeNbt(tag);
        tag.putFloat("head_yaw", this.yHeadRot);
        tag.put("Inventory", this.inventory.writeNbt(new ListTag()));
        tag.putInt("SelectedItemSlot", this.inventory.selectedSlot);
        if (this.character != null) {
            CompoundTag compound = new CompoundTag();
            CharacterUtils.writeToNBT(compound, this.character);
            tag.put("character", compound);
            if (this.controller != null) {
                tag.putString("follow_mode", this.controller.getFollowMode().name());
            }
        }

        UUID attachedOwnerUuid = this.controller != null && this.controller.getOwner() != null
                ? this.controller.getOwner().getUUID()
                : null;
        UUID ownerToPersist = ownerUuidForSave(this.ownerUuid, attachedOwnerUuid);
        if (attachedOwnerUuid != null) {
            this.ownerUuid = attachedOwnerUuid;
        }
        if (ownerToPersist != null) {
            tag.putUUID("owner_uuid", ownerToPersist);
        }
    }

    /**
     * Re-binds the controller's owner. Used by CompanionManager when an existing companion
     * is teleported back to a rejoining player, since teleport doesn't go through the spawn
     * constructor that originally sets the owner.
     */
    public void reattachOwner(Player newOwner) {
        if (newOwner != null && this.controller != null) {
            this.controller.setOwner(newOwner);
            this.ownerUuid = newOwner.getUUID();
        }
    }

    public void tick() {
        this.lastVelocity = this.getDeltaMovement();
        this.manager.update();
        this.inventory.updateItems();
        ++this.attackStrengthTicker;
        if (!this.level().isClientSide && this.controller != null && this.isAlive()) {
            this.controller.serverTick();
            // Throttled, in-memory-only last-known location hint (dimension-generic). Lets
            // CompanionManager positively confirm a merely-unloaded companion before spawning a clone.
            if ((this.tickCount % 100) == 0) {
                CompanionLocationTracker.record(this.getUUID(), this.level().dimension(), this.blockPosition());
            }
        }

        super.tick();
        this.updateSwingTime();
        if (this.controller != null) {
            this.hungerManager.setHungerEnabled(this.controller.getModSettings().isHungerEnabled());
            this.hungerManager.setDeathByHungerMatchesDifficulty(this.controller.getModSettings().isDeathByHungerMatchesDifficulty());
        }
        this.hungerManager.update(this);
    }

    public void aiStep() {
        if (this.isInWater() && this.isShiftKeyDown() && this.isAffectedByFluids()) {
            this.goDownInWater();
        }

        super.aiStep();
        this.yHeadRot = this.getYRot();
        this.pickupItems();
    }

    public void pickupItems() {
        // NOTE: pickup of the owner's drops must NOT be gated on the mobGriefing gamerule.
        // mobGriefing controls mob block/world modification (creepers, endermen); servers commonly
        // set it false, which previously silently disabled the companion's passive item pickup
        // forever. Keep only the server-side / alive / not-dead guards.
        if (!this.level().isClientSide && this.isAlive() && !this.dead) {
            Vec3i vec3i = new Vec3i(3, 3, 3);

            for(ItemEntity itemEntity : this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate((double)vec3i.getX(), (double)vec3i.getY(), (double)vec3i.getZ()))) {
                if (!itemEntity.isRemoved() && !itemEntity.getItem().isEmpty() && !itemEntity.hasPickUpDelay()) {
                    ItemStack itemStack = itemEntity.getItem();
                    ItemStack acquired = itemStack.copy();
                    int i = itemStack.getCount();
                    if (this.getLivingInventory().insertStack(itemStack)) {
                        if (this.controller != null) {
                            if (EquipVer.isBodyArmor(acquired)) {
                                this.controller.getPickupArmorEvalQueue().enqueueFromPickup(acquired);
                            } else if (WeaponVer.isMeleeWeapon(acquired)) {
                                this.controller.getPickupWeaponEvalQueue().enqueueFromPickup(acquired);
                            }
                        }
                        this.take(itemEntity, i);
                        if (itemStack.isEmpty()) {
                            itemEntity.discard();
                            itemStack.setCount(i);
                        }
                    }
                }
            }
        }

    }

    public boolean doHurtTarget(Entity target) {
        this.attackStrengthTicker = 0;
        float f = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float g = (float)this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        if (target instanceof LivingEntity) {
            f += EnchantmentHelper.getDamageBonus(this.getMainHandItem(), ((LivingEntity)target).getMobType());
            g += (float)EnchantmentHelper.getKnockbackBonus(this);
        }

        int i = EnchantmentHelper.getFireAspect(this);
        if (i > 0) {
            target.setSecondsOnFire(i * 4);
        }

        boolean bl = target.hurt(this.damageSources().mobAttack(this), f);
        if (bl) {
            if (g > 0.0F && target instanceof LivingEntity) {
                ((LivingEntity)target).knockback((double)(g * 0.5F), (double)Mth.sin(this.getYRot() * ((float)Math.PI / 180F)), (double)(-Mth.cos(this.getYRot() * ((float)Math.PI / 180F))));
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, (double)1.0F, 0.6));
            }

            this.doEnchantDamageEffects(this, target);
            this.setLastHurtMob(target);
        }

        return bl;
    }

    public void knockback(double strength, double x, double z) {
        if (this.hurtMarked) {
            super.knockback(strength, x, z);
        }

    }

    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    public Iterable<ItemStack> getArmorSlots() {
        return this.getLivingInventory().armor;
    }

    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            return this.inventory.getMainHandStack();
        } else if (slot == EquipmentSlot.OFFHAND) {
            return (ItemStack)this.inventory.offHand.get(0);
        } else {
            return slot.getType() == Type.ARMOR ? (ItemStack)this.inventory.armor.get(slot.getIndex()) : ItemStack.EMPTY;
        }
    }

    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            this.inventory.setItem(this.inventory.selectedSlot, stack);
        } else if (slot == EquipmentSlot.OFFHAND) {
            this.inventory.offHand.set(0, stack);
        } else if (slot.getType() == Type.ARMOR) {
            this.inventory.armor.set(slot.getIndex(), stack);
        }

        AutomatonEquipmentSyncPacket.broadcast(this);
    }

    /**
     * The owner UUID mirrored in memory (see {@link #ownerUuid}). Survives an offline owner (unlike
     * {@code controller.getOwner()}, which is null then). Used by the companion-cleanup command to
     * attribute a loaded entity to a player without depending on an attached controller/owner.
     */
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public Character getCharacter() {
        return this.character;
    }

    public void setCharacter(Character character) {
        this.character = character;
    }

    public Vec3 lerpVelocity(float delta) {
        return this.lastVelocity.lerp(this.getDeltaMovement(), (double)delta);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return (Packet<ClientGamePacketListener>) AutomatonSpawnPacket.create(this);
    }

    public Component getDisplayName() {
        return (Component)(this.character == null ? super.getDisplayName() : Component.literal(this.character.shortName()));
    }

    @Override
    protected void dropAllDeathLoot(DamageSource damageSource) {
        if (!this.canonicalDeathDropAuthorized) {
            // Offline authority is ambiguous and historical duplicates are never allowed to clone-drop.
            return;
        }
        boolean keep = KeepInventoryResolver.effectiveKeep(
                level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY));
        if (keep) {
            // KEEP: return before super (suppresses super's loot-table + XP drops) AND before
            // inventory.dropAll() (the ONLY thing that drops the bot's items/equipment). Inventory persists intact.
            return;
        }
        super.dropAllDeathLoot(damageSource);
        inventory.dropAll();
    }

    @Override
    public void die(DamageSource damageSource) {
        Player ownerPlayer = this.controller != null ? this.controller.getOwner() : null;
        ServerPlayer onlineOwner = ownerPlayer instanceof ServerPlayer
                ? (ServerPlayer) ownerPlayer : null;
        if (onlineOwner != null && (onlineOwner.getServer() == null
                || onlineOwner.getServer().getPlayerList().getPlayer(onlineOwner.getUUID()) != onlineOwner)) {
            onlineOwner = null;
        }
        CompanionManager ownerManager = onlineOwner == null
                ? null : CompanionManager.get(onlineOwner);
        boolean interruptedTask = ownerManager != null
                ? ownerManager.hasActiveWorkForLifecycle(this)
                : this.controller != null && this.controller.hasActiveNonIdleUserTask();
        this.canonicalDeathDropAuthorized = ownerManager != null
                && ownerManager.isMappedDeathAuthority(this);
        try {
            super.die(damageSource);
        } finally {
            this.canonicalDeathDropAuthorized = false;
        }
        if (level().isClientSide()) {
            return;
        }

        UUID resolvedOwnerUuid = onlineOwner != null ? onlineOwner.getUUID() : this.ownerUuid;
        MinecraftServer server = this.level().getServer();
        String deathCause = boundedSingleLine(
                damageSource.getLocalizedDeathMessage(this).getString(), 256);
        if (server == null || resolvedOwnerUuid == null || this.character == null
                || this.character.id() == null || this.character.id().isBlank()) {
            LOGGER.warn("Bot death could not be reconciled (ownerUuid={}, server={}, characterPresent={})",
                    resolvedOwnerUuid, server != null, this.character != null);
            stopDeadController();
            return;
        }

        OwnerUserSettingsStorage.Snapshot perPlayer =
                OwnerUserSettingsStorage.load(server, resolvedOwnerUuid);
        BotLifecycleSettings effective = BotLifecycleSettingsResolver.resolve(
                server, resolvedOwnerUuid, perPlayer.autoRespawn(), perPlayer.botPermadeath());
        boolean lethalDeath = isPermadeathKill(damageSource);
        boolean permadeathKill = lethalDeath && effective.botPermadeath();

        // An offline death cannot safely mutate the owner's persisted companion map. Preserve a
        // per-dying-UUID receipt and let CompanionManager validate it against that map on owner load.
        if (ownerManager == null) {
            PersistentDataManager.InventoryStateReceipt state =
                    PersistentDataManager.captureInventoryStateNow(this);
            if (state.ready() && state.state() != null) {
                String lifecycleNote = deathLifecycleNote(
                        effective.autoRespawn(), permadeathKill, interruptedTask);
                boolean saved = OfflineDeathTombstoneStorage.save(
                        server,
                        new OfflineDeathTombstoneStorage.Tombstone(
                                resolvedOwnerUuid,
                                this.character,
                                this.character.id(),
                                this.character.name(),
                                this.getUUID(),
                                state.state(),
                                effective.autoRespawn(),
                                effective.botPermadeath(),
                                lethalDeath,
                                lifecycleNote,
                                deathCause));
                if (!saved) {
                    LOGGER.warn("Offline death tombstone could not be persisted owner={} characterId={}",
                            resolvedOwnerUuid, this.character.id());
                }
            } else {
                LOGGER.warn("Offline companion death state was unavailable owner={} characterId={}",
                        resolvedOwnerUuid, this.character.id());
            }
            stopDeadController();
            return;
        }

        // Vanilla death has already applied keepInventory/drop semantics. Only the exact UUID still
        // mapped for this stable owner+character identity may spend that post-death receipt.
        CompanionManager.DeathStateReceipt deathState = ownerManager.capturePostDeathState(this);
        if (!deathState.mappedAuthority()) {
            ownerManager.reconcileTerminalDeath(
                    this, this.character, deathState, false, "", deathCause);
            stopDeadController();
            return;
        }

        boolean banned = PermadeathBanStorage.isBanned(
                server, resolvedOwnerUuid, this.character.id());
        PermadeathBanStorage.TerminalBanReceipt banReceipt = null;
        if (permadeathKill) {
            banReceipt = PermadeathBanStorage.recordTerminalBan(
                    server, resolvedOwnerUuid, this.character.id());
            banned = banReceipt.terminalDenied();
        }
        boolean manualDismissPending = ownerManager.hasManualDismissIntent(this.character);
        ManualDismissBarrierPolicy.DeathDecision manualDismissDeath =
                ManualDismissBarrierPolicy.afterDeath(manualDismissPending, banned);

        stopDeadController();
        ServerPlayer ownerSp = onlineOwner;
        if (interruptedTask) {
            ownerSp.sendSystemMessage(Component.translatable(
                    "message.player2npc.companion.despawned_with_task",
                    this.character.shortName()));
        }
        if (banned) {
            ownerManager.reconcileTerminalDeath(
                    this,
                    this.character,
                    deathState,
                    false,
                    deathLifecycleNote(false, true, interruptedTask),
                    deathCause);
            ownerSp.sendSystemMessage(Component.translatable(
                    "message.player2npc.companion.died_permadeath", this.character.shortName()));
            if (banReceipt != null && !banReceipt.persisted()) {
                ownerSp.sendSystemMessage(Component.translatable(
                        "message.player2npc.companion.permadeath_persistence_degraded",
                        this.character.shortName()));
            }
        } else if (manualDismissDeath.requireManualRestore()) {
            ownerManager.reconcileTerminalDeath(
                    this,
                    this.character,
                    deathState,
                    true,
                    pendingDismissDeathLifecycleNote(interruptedTask),
                    deathCause);
            ownerSp.sendSystemMessage(Component.translatable(
                    "message.player2npc.companion.died_dismiss_pending",
                    this.character.shortName()));
        } else if (!effective.autoRespawn()) {
            ownerManager.reconcileTerminalDeath(
                    this,
                    this.character,
                    deathState,
                    true,
                    deathLifecycleNote(false, false, interruptedTask),
                    deathCause);
            ownerSp.sendSystemMessage(Component.translatable(
                    "message.player2npc.companion.died_no_respawn", this.character.shortName()));
        } else {
            ownerSp.sendSystemMessage(Component.translatable(
                    "message.player2npc.companion.died", this.character.shortName()));
            try {
                CompanionManager.DeathRespawnResult result = ownerManager.reconcileAfterDeath(
                        this, this.character, deathCause, deathState, interruptedTask);
                if (result.spawnedReplacement()) {
                    ownerSp.sendSystemMessage(Component.translatable(
                            "message.player2npc.companion.respawned_near"));
                }
            } catch (RuntimeException spawnFailure) {
                ownerSp.sendSystemMessage(Component.translatable(
                        "message.player2npc.companion.relocate_failed", this.character.shortName()));
                LOGGER.warn("Failed to respawn companion after death for owner={} characterId={}",
                        ownerSp.getUUID(), this.character.id(), spawnFailure);
            }
        }
    }

    private void stopDeadController() {
        if (this.controller != null) {
            this.controller.stop();
            this.controller.unregisterFromGlobalRegistry();
        }
        ConversationManager.despwnCompanion(this.getUUID());
    }

    private static String deathLifecycleNote(
            boolean autoRespawn, boolean permadeath, boolean interruptedTask) {
        if (permadeath) {
            return "You died permanently and cannot be summoned again in this world. Do not claim you respawned."
                    + (interruptedTask
                            ? " Your interrupted task was cancelled and did not complete." : "");
        }
        if (!autoRespawn) {
            return "You died while auto-respawn was disabled. You remained despawned until your owner"
                    + " explicitly summoned you."
                    + (interruptedTask
                            ? " Your interrupted task was cancelled and did not complete." : "");
        }
        return "You died while your owner was offline. Your post-death state was preserved for recovery."
                + (interruptedTask
                        ? " Your interrupted task was cancelled and did not complete." : "");
    }

    private static String pendingDismissDeathLifecycleNote(boolean interruptedTask) {
        return "You died while your owner's dismissal request was pending. You remained dismissed until"
                + " explicitly summoned."
                + (interruptedTask
                        ? " Your interrupted task was cancelled and did not complete." : "");
    }

    private static String boundedSingleLine(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= maxLength
                ? singleLine : singleLine.substring(0, maxLength);
    }

    /**
     * Whether a death reaching {@link #die(DamageSource)} counts as a permadeath kill. Per the plan
     * (Non-negotiable #2, user-confirmed): ANY lethal damage that reaches {@code die()} is a kill —
     * delete/despawn paths flow through {@code remove(RemovalReason != KILLED)} and never reach here,
     * so the method boundary alone is the kill-vs-delete discriminator. Do NOT narrow to attacker
     * deaths or use {@code DamageTypeTags.IS_PLAYER_ATTACK} (1.21.1-only; would break 1.20.1 parity).
     */
    private boolean isPermadeathKill(DamageSource damageSource) {
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        // Location-hint lifecycle chokepoint: clear the tracker hint ONLY on a terminal removal
        // (reason.shouldDestroy() — KILLED / DISCARDED). A chunk-unload (UNLOADED_TO_CHUNK) or dimension
        // change must PRESERVE the hint — that is precisely the merely-unloaded state the hint exists to
        // rescue. Clearing it here on terminal removal is what makes "hint present == companion alive but
        // possibly unloaded" a reliable signal for classifySummon / dismiss / resolveViaLocationHint.
        if (reason.shouldDestroy()) {
            CompanionLocationTracker.clear(this.getUUID());
        }
        // On death, die() has already run the full cleanup (canonical-gated post-death state receipt +
        // stopWithRespawnNotification + unregisterFromGlobalRegistry + despwnCompanion).
        // MC 1.20.1 LivingEntity.die() does not remove immediately; the entity lingers for
        // deathTime ticks, then tickDeath() calls remove(RemovalReason.KILLED). Re-running
        // the cleanup here would double-save, double-cancel Baritone on a dead entity, and
        // despwn an already-removed UUID. Skip the cleanup block on the death path.
        if (reason == RemovalReason.KILLED) {
            super.remove(reason);
            return;
        }
        if (!this.level().isClientSide) {
            // Every Player2NPC preservation discard (normal dismiss and cross-dimension relocate)
            // saves synchronously before calling discard(). A generic async save here for DISCARDED
            // makes clone cleanup and character-data deletion write an orphan's inventory back over
            // the owner+character canonical file they are intentionally removing. Keep chunk-unload
            // persistence, but never persist a terminal explicit discard implicitly.
            if (shouldPersistInventoryForRemoval(reason)) {
                PersistentDataManager.saveInventory(this);
            }
            if (this.controller != null) {
                Player ownerPlayer = this.controller.getOwner();
                this.controller.stopWithRespawnNotification(
                        ownerPlayer instanceof ServerPlayer ownerSp ? ownerSp : null);
                this.controller.unregisterFromGlobalRegistry();
            }
            ConversationManager.despwnCompanion(this.getUUID());
        }
        super.remove(reason);
    }

    static boolean shouldPersistInventoryForRemoval(RemovalReason reason) {
        return CompanionIdentityReconciliationPolicy.shouldPersistInventoryForRemoval(
                reason == RemovalReason.KILLED,
                reason == RemovalReason.DISCARDED);
    }

    static UUID ownerUuidForSave(UUID cachedOwnerUuid, UUID attachedOwnerUuid) {
        return CompanionIdentityReconciliationPolicy.ownerUuidForStorage(
                attachedOwnerUuid, cachedOwnerUuid);
    }
}
