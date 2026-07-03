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
import com.player2.playerengine.player2api.AiConversationFeedback;
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
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.Mob;
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

public class AutomatoneEntity extends LivingEntity
        implements IAutomatone, IInventoryProvider, IInteractionManagerProvider, IHungerManagerProvider {
    private static final Logger LOGGER = LogManager.getLogger();
    public LivingEntityInteractionManager manager;
    public LivingEntityInventory inventory;
    public LivingEntityHungerManager hungerManager;
    public PlayerEngineController controller;
    public Character character;
    public ResourceLocation textureLocation;
    protected Vec3 lastVelocity;
    public static final String PLAYER2_GAME_ID = "player2-ai-npc-minecraft";
    /**
     * Owner UUID cached in memory so it is available in {@link #die(DamageSource)} even when the owner
     * is offline ({@code controller.getOwner()} returns null then). Mirrors the {@code owner_uuid} NBT
     * tag; populated in {@link #init(Player)}, {@link #readAdditionalSaveData(CompoundTag)}, and
     * {@link #reattachOwner(Player)}. Used only for the permadeath ban write (Non-negotiable #10);
     * nothing new is persisted ({@code owner_uuid} already round-trips via NBT).
     */
    private UUID ownerUuid;
    /**
     * Persisted follow mode (WS2). Parsed in {@link #readAdditionalSaveData(CompoundTag)} and applied to the
     * controller on whichever path builds it: the read-path construction (cold load) applies it immediately,
     * while the spawn/teleport path applies it at the end of {@link #init(Player, SpawnReason, String)} (the
     * controller does not exist when the NBT is read on that path). Defaults to {@code NORMAL} for fresh
     * spawns and legacy/unknown saves.
     */
    private FollowMode pendingFollowMode = FollowMode.NORMAL;

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
        // this.setMaxUpStep(0.6F);
        this.setSpeed(0.4F);
        this.manager = new LivingEntityInteractionManager(this);
        this.inventory = new LivingEntityInventory(this);
        this.hungerManager = new LivingEntityHungerManager();
        if (!this.level().isClientSide && this.character != null) {
            this.controller = new PlayerEngineController((IBaritone) IBaritone.KEY.get(this), this.character,
                    "player2-ai-npc-minecraft");
            if (companionOwner != null) {
                this.controller.setOwner(companionOwner);
                this.ownerUuid = companionOwner.getUUID();
            }
            // Single greeting decision point (Q5 option (a)): FIRST_MEETING and RETURNING are handled
            // identically here -- both call sendReturnMessage, which greets internally when the
            // per-world history file does not yet exist (true first meeting), else emits "<owner> has
            // respawned you". (No call site passes FIRST_MEETING today; see SpawnReason javadoc.)
            // DEATH_RESPAWN injects the death cause so the revived bot answers truthfully (DESIGN.md §3).
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
            PersistentDataManager.loadInventory(this);
            this.controller.setFollowMode(this.pendingFollowMode);
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

    public AutomatoneEntity(Level world, Character character, Player owner) {
        this(world, character, owner, SpawnReason.RETURNING, null);
    }

    public AutomatoneEntity(Level world, Character character, Player owner, SpawnReason reason, String deathCause) {
        super(Player2NPC.AUTOMATONE.get(), world);
        this.setCharacter(character);
        this.init(owner, reason, deathCause);
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
        this.inventory.readNbt(level().registryAccess(), nbtList);
        this.inventory.selectedSlot = tag.getInt("SelectedItemSlot");
        if (this.character == null && tag.contains("character")) {
            CompoundTag compound = tag.getCompound("character");
            this.character = CharacterUtils.readFromNBT(compound);
            if (this.controller == null) {
                this.controller = new PlayerEngineController((IBaritone) IBaritone.KEY.get(this), this.character,
                        "player2-ai-npc-minecraft");
            }

            // Q2 = SILENT: restart / dimension-change / chunk-reload must emit NOTHING here. This path
            // fires on every owner-far chunk reload and has no owner attached at this point (owner
            // resolution is the block below), so any send would be spurious or a garbage owner name.
            // The genuine "owner respawned you" return message fires only from the owner-online SPAWN
            // call sites (CompanionManager.spawnCompanion -> init RETURNING).
        }

        // Restore controller owner from persisted UUID when possible. Owner may be offline; if so,
        // leave the controller owner unset and CompanionManager.ensureCompanionExists (teleport branch)
        // reattaches when they rejoin. Cache the UUID in memory regardless (even offline) so die() can
        // write a permadeath ban for the correct owner (Non-negotiable #10).
        if (!this.level().isClientSide && tag.hasUUID("owner_uuid")) {
            UUID savedOwnerUuid = tag.getUUID("owner_uuid");
            this.ownerUuid = savedOwnerUuid;
            MinecraftServer srv = this.level().getServer();
            if (this.controller != null && srv != null) {
                ServerPlayer ownerPlayer = srv.getPlayerList().getPlayer(savedOwnerUuid);
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
        tag.put("Inventory", this.inventory.writeNbt(level().registryAccess(), new ListTag()));
        tag.putInt("SelectedItemSlot", this.inventory.selectedSlot);
        if (this.character != null) {
            CompoundTag compound = new CompoundTag();
            CharacterUtils.writeToNBT(compound, this.character);
            tag.put("character", compound);
            if (this.controller != null) {
                tag.putString("follow_mode", this.controller.getFollowMode().name());
            }
        }

        if (this.controller != null && this.controller.getOwner() != null) {
            tag.putUUID("owner_uuid", this.controller.getOwner().getUUID());
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

            for (ItemEntity itemEntity : this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox()
                    .inflate((double) vec3i.getX(), (double) vec3i.getY(), (double) vec3i.getZ()))) {
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

    public boolean doHurtTarget(Entity entity) {
        float f = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource damageSource = this.damageSources().mobAttack(this);
        Level l = this.level();
        if (l instanceof ServerLevel serverLevel) {
            f = EnchantmentHelper.modifyDamage(serverLevel, this.getWeaponItem(), entity, damageSource, f);
        }

        boolean bl = entity.hurt(damageSource, f);
        if (bl) {
            float g = this.getKnockback(entity, damageSource);
            if (g > 0.0F && entity instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) entity;
                livingEntity.knockback((double) (g * 0.5F), (double) Mth.sin(this.getYRot() * ((float) Math.PI / 180F)),
                        (double) (-Mth.cos(this.getYRot() * ((float) Math.PI / 180F))));
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, (double) 1.0F, 0.6));
            }

            Level var7 = this.level();
            if (var7 instanceof ServerLevel) {
                ServerLevel serverLevel2 = (ServerLevel) var7;
                EnchantmentHelper.doPostAttackEffects(serverLevel2, entity, damageSource);
            }

            this.setLastHurtMob(entity);
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
            return (ItemStack) this.inventory.offHand.get(0);
        } else {
            return slot.getType() == Type.HUMANOID_ARMOR ? (ItemStack) this.inventory.armor.get(slot.getIndex())
                    : ItemStack.EMPTY;
        }
    }

    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            this.inventory.setItem(this.inventory.selectedSlot, stack);
        } else if (slot == EquipmentSlot.OFFHAND) {
            this.inventory.offHand.set(0, stack);
        } else if (slot.getType() == Type.HUMANOID_ARMOR) {
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
        return this.lastVelocity.lerp(this.getDeltaMovement(), (double) delta);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return (Packet<ClientGamePacketListener>) AutomatonSpawnPacket.create(level().registryAccess(), this);
    }

    public Component getDisplayName() {
        return (Component) (this.character == null ? super.getDisplayName()
                : Component.literal(this.character.shortName()));
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel serverLevel, DamageSource damageSource) {
        boolean keep = KeepInventoryResolver.effectiveKeep(
                level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY));
        if (keep) {
            // KEEP: return before super (suppresses super's loot-table + XP drops) AND before
            // inventory.dropAll() (the ONLY thing that drops the bot's items/equipment). Inventory persists intact.
            return;
        }
        super.dropAllDeathLoot(serverLevel, damageSource);
        inventory.dropAll();
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (!level().isClientSide()) {
            PersistentDataManager.saveInventory(this);

            // (1) Resolve the owner UUID and server handle FIRST, unconditionally — a kill is a kill
            // even when the owner is offline (controller.getOwner() is null then), so the permadeath
            // ban must not depend on owner presence (Non-negotiable #10).
            UUID resolvedOwnerUuid = this.controller != null && this.controller.getOwner() != null
                    ? this.controller.getOwner().getUUID()
                    : this.ownerUuid;
            MinecraftServer server = this.level().getServer();

            // (2) Permadeath ban write — OUTSIDE the online-owner block so it runs offline too.
            // Load per-player settings + resolve the effective lifecycle config through the single
            // resolver (Non-negotiable #5). Default to ON auto-respawn / no permadeath if we cannot
            // resolve an owner/server (degrade visibly via log, never crash).
            boolean autoRespawn = true;
            boolean bannedThisDeath = false;
            if (server != null && resolvedOwnerUuid != null) {
                OwnerUserSettingsStorage.Snapshot perPlayer =
                        OwnerUserSettingsStorage.load(server, resolvedOwnerUuid);
                BotLifecycleSettings effective = BotLifecycleSettingsResolver.resolve(
                        server, resolvedOwnerUuid, perPlayer.autoRespawn(), perPlayer.botPermadeath());
                autoRespawn = effective.autoRespawn();
                String charId = this.character != null ? this.character.id() : null;
                if (isPermadeathKill(damageSource) && effective.botPermadeath()
                        && charId != null && !charId.isBlank()) {
                    try {
                        PermadeathBanStorage.addBan(server, resolvedOwnerUuid, charId);
                        bannedThisDeath = true;
                    } catch (IOException e) {
                        LOGGER.warn("Failed to write permadeath ban for owner {} character {}",
                                resolvedOwnerUuid, charId, e);
                    }
                }
            } else {
                LOGGER.warn("Bot died with no resolvable owner/server (ownerUuid={}, server={}); "
                        + "skipping ban + respawn", resolvedOwnerUuid, server);
            }

            // Resolve the live owner so an interrupted task can be reported to the player + model
            // BEFORE despwnCompanion wipes the conversation queue.
            Player ownerPlayer = this.controller != null ? this.controller.getOwner() : null;
            boolean banned = bannedThisDeath
                    || (server != null && resolvedOwnerUuid != null && this.character != null
                            && this.character.id() != null && !this.character.id().isBlank()
                            && PermadeathBanStorage.isBanned(server, resolvedOwnerUuid, this.character.id()));

            // (4) Dual-audience model feedback (DESIGN.md §3) — enqueue BEFORE despwnCompanion wipes
            // the queue, so the model does not assume it respawned. Player chat is sent in (3).
            if (this.controller != null) {
                if (banned) {
                    AiConversationFeedback.enqueueInfo(this.controller,
                            "You died permanently (hardcore permadeath) and cannot be summoned again in this world."
                                    + " Do not claim you respawned.");
                } else if (!autoRespawn) {
                    AiConversationFeedback.enqueueInfo(this.controller,
                            "You died and auto-respawn is disabled, so you were not respawned. The owner must summon you."
                                    + " Do not claim you respawned.");
                }
                this.controller.stopWithRespawnNotification(
                        ownerPlayer instanceof ServerPlayer ownerSp0 ? ownerSp0 : null);
                this.controller.unregisterFromGlobalRegistry();
            }
            ConversationManager.despwnCompanion(this.getUUID());

            // (3) Player-facing messaging + respawn — requires an online owner. Choose EXACTLY ONE
            // exclusive branch, banned-first short-circuit.
            if (ownerPlayer instanceof ServerPlayer ownerSp && this.character != null) {
                if (banned) {
                    // (a) banned: died permanently, no respawn, NO "use summon" line (it could never work).
                    ownerSp.sendSystemMessage(Component.translatable(
                            "message.player2npc.companion.died_permadeath", this.character.shortName()));
                } else if (!autoRespawn) {
                    // (b) auto-respawn OFF: no respawn; tell the player to summon it back.
                    ownerSp.sendSystemMessage(Component.translatable(
                            "message.player2npc.companion.died_no_respawn", this.character.shortName()));
                } else {
                    // (c) auto-respawn ON: existing behavior + death context for the model.
                    ownerSp.sendSystemMessage(Component.translatable(
                            "message.player2npc.companion.died", this.character.shortName()));
                    ownerSp.sendSystemMessage(Component.translatable(
                            "message.player2npc.companion.respawned_near"));
                    // Pass DEATH_RESPAWN + vanilla death cause so init() on the new entity injects the
                    // death context into its fresh queue (the dying entity's queue was wiped by
                    // despwnCompanion above). Do not reorder the spawnCompanion internals
                    // (CompanionManager L163-166); the enqueue happens inside init().
                    String deathCause = damageSource.getLocalizedDeathMessage(this).getString();
                    CompanionManager.get(ownerSp).spawnCompanion(this.character, SpawnReason.DEATH_RESPAWN, deathCause);
                }
            }
        }
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
        // On death, die() has already run the full cleanup (saveInventory +
        // stopWithRespawnNotification + unregisterFromGlobalRegistry + despwnCompanion).
        // MC 1.21.1 LivingEntity.die() does not remove immediately; the entity lingers for
        // deathTime ticks, then the tick loop calls remove(RemovalReason.KILLED). Re-running
        // the cleanup here would double-save, double-cancel Baritone on a dead entity, and
        // despwn an already-removed UUID. Skip the cleanup block on the death path.
        if (reason == RemovalReason.KILLED) {
            super.remove(reason);
            return;
        }
        if (!this.level().isClientSide) {
            PersistentDataManager.saveInventory(this);
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
}
