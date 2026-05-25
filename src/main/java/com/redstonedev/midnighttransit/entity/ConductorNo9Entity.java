package com.redstonedev.midnighttransit.entity;

import com.redstonedev.midnighttransit.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib3.core.AnimationState;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType.EDefaultLoopTypes;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

public class ConductorNo9Entity extends Monster implements IAnimatable {

    /** Behavior modes for this entity. Encoded as an int in synced data. */
    public enum Mode { NORMAL, STALKING_IDLE, STALKING_POSE, WINDOW, LOOKING }

    private static final EntityDataAccessor<Boolean> DATA_AGGRESSIVE =
            SynchedEntityData.defineId(ConductorNo9Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
            SynchedEntityData.defineId(ConductorNo9Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(ConductorNo9Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GRAB_TARGET =
            SynchedEntityData.defineId(ConductorNo9Entity.class, EntityDataSerializers.INT);

    /** Total length of the grab attack in server ticks (~2 seconds matches the attack animation). */
    private static final int GRAB_DURATION_TICKS = 40;

    // No walking speed - he only RUNS or stands still per spec.
    private static final double SPEED_CHASE = 0.36D;
    private static final double SPEED_CLIMB = 0.36D;

    private final AnimationFactory factory = GeckoLibUtil.createFactory(this);

    private int aliveTicks = 0;
    private int blockBreakCooldown = 30;
    private int ambientSoundCooldown;
    private int helloCooldown;
    private int helpCooldown;
    private int iseeyouCooldown;
    private int stareSinceLooked = 0; // for "LOOKING" mode - stare 3s then despawn

    private boolean clientChaseSoundStarted = false;

    public ConductorNo9Entity(EntityType<? extends ConductorNo9Entity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
        this.maxUpStep = 1.0F;
        // Randomized initial cooldowns - first ambient noise isn't tied to spawn time.
        this.ambientSoundCooldown = 600 + this.random.nextInt(1200);
        this.helloCooldown        = 800 + this.random.nextInt(1600);
        this.helpCooldown         = 1200 + this.random.nextInt(2400);
        this.iseeyouCooldown      = 1600 + this.random.nextInt(2400);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 700000.0D)        // per spec
                .add(Attributes.ATTACK_DAMAGE, 1000000.0D)    // per spec
                .add(Attributes.MOVEMENT_SPEED, SPEED_CHASE)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_AGGRESSIVE, false);
        this.entityData.define(DATA_CLIMBING, false);
        this.entityData.define(DATA_MODE, Mode.NORMAL.ordinal());
        this.entityData.define(DATA_GRAB_TARGET, -1);
    }

    private int grabTicks = 0;

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BreakDoorGoal(this, d -> true));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 96.0F, 1.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1,
                new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // === State accessors ======================================================

    public boolean isConductorAggressive() { return this.entityData.get(DATA_AGGRESSIVE); }
    public boolean isClimbing()            { return this.entityData.get(DATA_CLIMBING); }

    public Mode getMode() {
        int idx = this.entityData.get(DATA_MODE);
        Mode[] vs = Mode.values();
        if (idx < 0) idx = 0;
        if (idx >= vs.length) idx = vs.length - 1;
        return vs[idx];
    }

    public boolean isStalking() {
        Mode m = getMode();
        return m == Mode.STALKING_IDLE || m == Mode.STALKING_POSE;
    }

    public boolean isWindow()  { return getMode() == Mode.WINDOW; }
    public boolean isLooking() { return getMode() == Mode.LOOKING; }

    public void setConductorAggressive(boolean aggressive) {
        boolean was = this.entityData.get(DATA_AGGRESSIVE);
        this.entityData.set(DATA_AGGRESSIVE, aggressive);
        if (aggressive) {
            this.entityData.set(DATA_MODE, Mode.NORMAL.ordinal());
            this.setNoAi(false);
            if (!was && !this.level.isClientSide) {
                this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.ISEEYOU.get(), SoundSource.HOSTILE, 1.4F, 1.0F);
            }
        }
    }

    public void setClimbing(boolean climbing) { this.entityData.set(DATA_CLIMBING, climbing); }

    public void setMode(Mode mode) {
        this.entityData.set(DATA_MODE, mode.ordinal());
        // Stalking and special-spawn modes lock AI; aggressive/normal lets AI run.
        if (mode == Mode.NORMAL) {
            this.setNoAi(false);
        } else {
            this.setNoAi(true);
        }
    }

    // === Climbing =============================================================

    @Override
    public boolean onClimbable() { return this.isClimbing(); }

    // === Tick =================================================================

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide) {
            boolean blocked = this.horizontalCollision;
            LivingEntity target = this.getTarget();
            boolean targetAbove = target != null && target.getY() > this.getY() + 1.5;
            if (blocked && targetAbove) {
                Vec3 d = this.getDeltaMovement();
                this.setDeltaMovement(d.x, Math.max(0.22D, d.y), d.z);
            }
            this.setClimbing(blocked);
        } else {
            if (!clientChaseSoundStarted && isConductorAggressive() && getMode() == Mode.NORMAL) {
                clientChaseSoundStarted = true;
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                        net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.redstonedev.midnighttransit.client.sound.ClientChaseSoundStarter.start(this));
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level.isClientSide) return;

        // Grab attack drives everything when it's active.
        if (grabTicks > 0) {
            tickGrab();
            aliveTicks++;
            return;
        }

        aliveTicks++;
        // 1 minute lifetime cap per spec.
        if (aliveTicks >= 1200) {
            this.discard();
            return;
        }

        if (blockBreakCooldown > 0) blockBreakCooldown--;
        if (ambientSoundCooldown > 0) ambientSoundCooldown--;
        if (helloCooldown > 0) helloCooldown--;
        if (helpCooldown > 0) helpCooldown--;
        if (iseeyouCooldown > 0) iseeyouCooldown--;

        // Adjust movement speed based on mode.
        double speed = SPEED_CHASE;
        if (isClimbing()) speed = SPEED_CLIMB;
        AttributeInstance attr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && Math.abs(attr.getBaseValue() - speed) > 1e-6) {
            attr.setBaseValue(speed);
        }

        Player nearest = this.level.getNearestPlayer(this, 96.0D);

        // STALKING modes - both react identically: stare at player, disappear when looked at.
        if (isStalking()) {
            if (nearest != null) {
                lockYawTo(nearest);
                if (isPlayerStaringAt(nearest)) {
                    this.discard();
                    return;
                }
            }
            tickAmbientSounds(nearest);
            return;
        }

        // WINDOW mode - same disappear-on-look behavior; renderer shows the window animation.
        if (isWindow()) {
            if (nearest != null) {
                lockYawTo(nearest);
                if (isPlayerStaringAt(nearest)) {
                    // Per spec: play "window_seen" animation and then despawn. The animation
                    // controller switches to window_seen automatically; we delay despawn 30
                    // ticks (~1.5s) so the seen animation has time to play.
                    setMode(Mode.NORMAL);
                    this.entityData.set(DATA_MODE, 99); // sentinel: "seen" - handled in predicate
                    // Use stareSinceLooked as a despawn countdown for the seen animation.
                    stareSinceLooked = 30;
                }
            }
            tickAmbientSounds(nearest);
            // If we set the sentinel above, count down to despawn.
            if (this.entityData.get(DATA_MODE) == 99 && stareSinceLooked-- <= 0) {
                this.discard();
            }
            return;
        }

        // LOOKING mode - spawns behind a wall/tree. When player looks: 50% looking_seen +
        // quick despawn; 50% stare for 3 seconds then despawn.
        if (isLooking()) {
            if (nearest != null) {
                lockYawTo(nearest);
                if (isPlayerStaringAt(nearest)) {
                    if (this.random.nextBoolean()) {
                        // looking_seen variant
                        this.entityData.set(DATA_MODE, 98);
                        stareSinceLooked = 30;
                    } else {
                        // stare-3s variant
                        this.entityData.set(DATA_MODE, 97);
                        stareSinceLooked = 60; // 3 seconds
                    }
                }
            }
            tickAmbientSounds(nearest);
            int rawMode = this.entityData.get(DATA_MODE);
            if ((rawMode == 98 || rawMode == 97) && stareSinceLooked-- <= 0) {
                this.discard();
            }
            return;
        }

        // NORMAL / AGGRESSIVE mode:
        if (nearest != null) {
            if (!isConductorAggressive() && isPlayerStaringAt(nearest)) {
                setConductorAggressive(true);
            }
            if (!isConductorAggressive() && this.distanceTo(nearest) < 2.0D) {
                setConductorAggressive(true);
            }
        }

        // Aggressive Conductor breaks blocks while chasing.
        if (isConductorAggressive() && blockBreakCooldown <= 0 && !isClimbing()) {
            tryBreakBlocks();
        }

        tickAmbientSounds(nearest);
    }

    private void lockYawTo(Player p) {
        double dx = p.getX() - this.getX();
        double dz = p.getZ() - this.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
    }

    private void tickAmbientSounds(Player nearest) {
        if (ambientSoundCooldown <= 0 && nearest != null) {
            // Pick one of three vocal sounds randomly.
            int r = this.random.nextInt(3);
            SoundEvent s;
            if (r == 0) s = ModSounds.HELLO.get();
            else if (r == 1) s = ModSounds.HELP.get();
            else s = ModSounds.ISEEYOU.get();
            // Play at the player's position so they actually hear it.
            this.level.playSound(null, nearest.getX(), nearest.getY(), nearest.getZ(),
                    s, SoundSource.HOSTILE, 0.8F, 1.0F);
            ambientSoundCooldown = 600 + this.random.nextInt(1200);
        }
    }

    private boolean isPlayerStaringAt(Player p) {
        if (this.distanceTo(p) > 64.0D) return false;
        double dx = this.getX() - p.getX();
        double dy = this.getEyeY() - p.getEyeY();
        double dz = this.getZ() - p.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001D) return false;
        dx /= len; dy /= len; dz /= len;
        Vec3 look = p.getViewVector(1.0F);
        double dot = look.x * dx + look.y * dy + look.z * dz;
        return dot > 0.9D && p.hasLineOfSight(this);
    }

    // === Block breaking - single-exit pattern for PojavLauncher safety ========

    private void tryBreakBlocks() {
        BlockPos center = this.blockPosition();
        BlockPos targetPos = null;
        scan:
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (canBreakBlockAt(p)) {
                        targetPos = p;
                        break scan;
                    }
                }
            }
        }
        if (targetPos != null) {
            this.level.destroyBlock(targetPos, true, this);
            blockBreakCooldown = 20 + this.random.nextInt(20);
        } else {
            blockBreakCooldown = 30;
        }
    }

    private boolean canBreakBlockAt(BlockPos p) {
        BlockState bs = this.level.getBlockState(p);
        if (bs.isAir()) return false;
        if (isUnbreakable(bs)) return false;
        if (bs.getDestroySpeed(this.level, p) < 0) return false;
        return true;
    }

    private boolean isUnbreakable(BlockState bs) {
        Block b = bs.getBlock();
        return b == Blocks.BEDROCK
                || b == Blocks.COMMAND_BLOCK
                || b == Blocks.BARRIER
                || b == Blocks.STRUCTURE_BLOCK
                || b == Blocks.JIGSAW
                || b == Blocks.LIGHT
                || b == Blocks.END_PORTAL
                || b == Blocks.END_PORTAL_FRAME
                || b == Blocks.NETHER_PORTAL
                || b == Blocks.VOID_AIR;
    }

    // === Animations ===========================================================

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 3, this::predicate));
        data.addAnimationController(new AnimationController<>(this, "attack_controller", 0, this::attackPredicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();
        int rawMode = this.entityData.get(DATA_MODE);

        // 99 = window_seen, 98 = looking_seen, 97 = looking-stare (still uses looking anim)
        if (rawMode == 99) {
            controller.setAnimation(new AnimationBuilder().loop("animation.conductor_no_9.window_seen"));
            return PlayState.CONTINUE;
        }
        if (rawMode == 98) {
            controller.setAnimation(new AnimationBuilder().loop("animation.conductor_no_9.looking_seen"));
            return PlayState.CONTINUE;
        }
        if (rawMode == 97) {
            controller.setAnimation(new AnimationBuilder().loop("animation.conductor_no_9.looking"));
            return PlayState.CONTINUE;
        }

        Mode mode = getMode();
        if (mode == Mode.WINDOW) {
            controller.setAnimation(new AnimationBuilder().loop("animation.conductor_no_9.window"));
            return PlayState.CONTINUE;
        }
        if (mode == Mode.LOOKING) {
            controller.setAnimation(new AnimationBuilder().loop("animation.conductor_no_9.looking"));
            return PlayState.CONTINUE;
        }
        if (mode == Mode.STALKING_POSE) {
            controller.setAnimation(new AnimationBuilder().loop("animation.conductor_no_9.stalking_pose"));
            return PlayState.CONTINUE;
        }
        if (mode == Mode.STALKING_IDLE) {
            controller.setAnimation(new AnimationBuilder().loop("animation.conductor_no_9.idle"));
            return PlayState.CONTINUE;
        }

        // NORMAL mode: running when moving (he doesn't walk - he only runs), idle when still.
        if (event.isMoving()) {
            controller.setAnimation(new AnimationBuilder().loop("animation.conductor_no_9.running"));
        } else {
            controller.setAnimation(new AnimationBuilder().loop("animation.conductor_no_9.idle"));
        }
        return PlayState.CONTINUE;
    }

    private <E extends IAnimatable> PlayState attackPredicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();
        if (this.swinging && controller.getAnimationState() == AnimationState.Stopped) {
            controller.markNeedsReload();
            controller.setAnimation(new AnimationBuilder()
                    .addAnimation("animation.conductor_no_9.attack", EDefaultLoopTypes.PLAY_ONCE));
            this.swinging = false;
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() { return factory; }

    // === Sounds ===============================================================

    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.HURT.get(); }
    @Override protected SoundEvent getDeathSound()              { return ModSounds.DEATH.get(); }
    @Override protected float getSoundVolume()                  { return 1.0F; }

    // === Damage / attack ======================================================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && source.getEntity() instanceof LivingEntity) {
            setConductorAggressive(true);
        }
        return result;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.swing(InteractionHand.MAIN_HAND);
        // If we're already mid-grab, ignore further attack attempts.
        if (grabTicks > 0) return false;
        // Player target -> trigger the grab attack instead of normal damage.
        if (target instanceof Player) {
            this.entityData.set(DATA_GRAB_TARGET, target.getId());
            grabTicks = GRAB_DURATION_TICKS;
            // Freeze AI while the grab plays out so he holds his position.
            this.setNoAi(true);
            return true;
        }
        // Non-player target -> normal damage.
        return super.doHurtTarget(target);
    }

    /**
     * Drives the grab attack: smoothly teleport the player to a "hand" position in front of
     * the entity, then up to head height in the second half of the animation, then kill the
     * player with red blood-burst particles.
     */
    private void tickGrab() {
        if (grabTicks <= 0) {
            if (this.entityData.get(DATA_GRAB_TARGET) != -1) {
                this.entityData.set(DATA_GRAB_TARGET, -1);
                this.setNoAi(false);
            }
            return;
        }

        int targetId = this.entityData.get(DATA_GRAB_TARGET);
        Entity targetEnt = this.level.getEntity(targetId);
        if (!(targetEnt instanceof Player)) {
            grabTicks = 0;
            this.entityData.set(DATA_GRAB_TARGET, -1);
            this.setNoAi(false);
            return;
        }
        Player grabbed = (Player) targetEnt;

        // 0.0 -> 1.0 across the grab.
        float progress = 1.0f - (grabTicks / (float) GRAB_DURATION_TICKS);

        // Smoothly draw the player from "in front of the Conductor" to "inside his mouth".
        // - distance shrinks from 1.0 block in front -> 0.3 block (inside the head volume)
        // - height rises from chest-ish (45% of body height) -> mouth/face (80%)
        float yawRad = (float) Math.toRadians(this.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        double startDist  = 1.0;
        double endDist    = 0.3;
        double distance   = startDist + (endDist - startDist) * progress;

        double startHF    = 0.45; // start near chest
        double endHF      = 0.80; // end at mouth/face
        double heightFrac = startHF + (endHF - startHF) * progress;

        double handX = this.getX() + forwardX * distance;
        double handZ = this.getZ() + forwardZ * distance;
        double handY = this.getY() + this.getBbHeight() * heightFrac;

        // Lock the player to the hand position via a server-side teleport every tick.
        // Face them back toward us so the camera sees the giant conductor's face.
        if (grabbed instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer) grabbed;
            sp.connection.teleport(handX, handY, handZ, this.getYRot() + 180.0F, 0.0F);
        } else {
            grabbed.setPos(handX, handY, handZ);
        }
        grabbed.setDeltaMovement(0, 0, 0);
        grabbed.fallDistance = 0;
        // Brief hurt-flash so the player visually registers being grabbed.
        if (grabTicks == GRAB_DURATION_TICKS) {
            grabbed.hurt(DamageSource.mobAttack(this), 1.0F);
        }

        grabTicks--;
        if (grabTicks <= 0) {
            // End of attack - kill the player and spawn a burst of blood particles at their
            // position. Uses RED_CONCRETE_POWDER's particle - chunky red specks that look
            // like a blood spray.
            if (this.level instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) this.level;
                sl.sendParticles(
                        new net.minecraft.core.particles.BlockParticleOption(
                                net.minecraft.core.particles.ParticleTypes.BLOCK,
                                net.minecraft.world.level.block.Blocks.RED_CONCRETE.defaultBlockState()),
                        grabbed.getX(),
                        grabbed.getY() + grabbed.getBbHeight() * 0.6,
                        grabbed.getZ(),
                        120,   // particle count
                        0.5, 0.5, 0.5,  // spread X/Y/Z
                        0.35); // speed
                // Also play a generic death effect sound.
                sl.playSound(null, grabbed.getX(), grabbed.getY(), grabbed.getZ(),
                        net.minecraft.sounds.SoundEvents.GENERIC_DEATH, SoundSource.HOSTILE, 1.5F, 0.6F);
            }
            // 1,000,000 damage per spec - obliterates the player regardless of armor.
            grabbed.hurt(DamageSource.mobAttack(this), 1_000_000.0F);
            this.entityData.set(DATA_GRAB_TARGET, -1);
            this.setNoAi(false);
        }
    }

    // === Removal hook =========================================================

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level.isClientSide) {
            if (isConductorAggressive()) {
                stopChaseThemeForNearbyPlayers();
            }
        }
        super.remove(reason);
    }

    private void stopChaseThemeForNearbyPlayers() {
        if (!(this.level instanceof ServerLevel)) return;
        ServerLevel serverLevel = (ServerLevel) this.level;
        ResourceLocation sound = ModSounds.CHASE_THEME.get().getLocation();
        ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(sound, SoundSource.HOSTILE);
        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(this) < 96.0D * 96.0D) {
                p.connection.send(packet);
            }
        }
    }

    // === NBT ==================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Aggressive", isConductorAggressive());
        tag.putInt("Mode", this.entityData.get(DATA_MODE));
        tag.putInt("AliveTicks", aliveTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Aggressive")) setConductorAggressive(true);
        this.entityData.set(DATA_MODE, tag.getInt("Mode"));
        aliveTicks = tag.getInt("AliveTicks");
    }

    @Override public boolean canBeAffected(MobEffectInstance effect) { return true; }
}
