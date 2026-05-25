package com.redstonedev.midnighttransit.event;

import com.redstonedev.midnighttransit.MidnightTransit;
import com.redstonedev.midnighttransit.entity.ConductorNo9Entity;
import com.redstonedev.midnighttransit.entity.ConductorNo9Entity.Mode;
import com.redstonedev.midnighttransit.init.ModEntities;
import com.redstonedev.midnighttransit.init.ModItems;
import com.redstonedev.midnighttransit.init.ModSounds;
import com.redstonedev.midnighttransit.network.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ForgeEvents {

    private static final Random RNG = new Random();
    private int tickCounter = 0;

    /** Per-player state for the "footsteps -> hide -> conductor spawns" scenario. */
    private static class HideState {
        int phase;            // 0 = HIDE countdown, 1 = HE'S HERE wandering, 2 = none
        int ticksLeft;        // ticks until phase end
        UUID conductorId;     // tracked conductor for this scenario (null until phase 1 spawn)
        HideState(int p, int t) { this.phase = p; this.ticksLeft = t; }
    }

    private static final Map<UUID, HideState> hideStates = new HashMap<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (event.getServer() == null) return;

        // Every tick: progress active HIDE scenarios.
        tickHideStates(event.getServer().getAllLevels());

        // Every 5s: ambient spawn check + footsteps trigger check.
        if (tickCounter % 100 == 0) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                tryAmbientSpawn(level);
                tryTriggerFootsteps(level);
            }
        }
    }

    // --- Ambient spawning -----------------------------------------------------

    private boolean hasConductor(ServerLevel level) {
        return !level.getEntities(ModEntities.CONDUCTOR_NO_9.get(), n -> !n.isRemoved()).isEmpty();
    }

    private void tryAmbientSpawn(ServerLevel level) {
        // Per spec: "He stalks in night only."
        if (level.isDay()) return;
        List<? extends ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        if (hasConductor(level)) return;

        for (ServerPlayer player : players) {
            if (RNG.nextInt(250) != 0) continue; // ~1 in 250 per 5s tick per player at night

            // Pick a random behavior mode for this spawn.
            // Most spawns are stalking. Rarely WINDOW (near player's nearby window) or LOOKING
            // (behind a wall/tree). Even rarer: NORMAL aggro spawn.
            int r = RNG.nextInt(100);
            Mode mode;
            BlockPos spawnPos;
            if (r < 70) {
                // Stalking: 50/50 idle or pose animation.
                mode = RNG.nextBoolean() ? Mode.STALKING_IDLE : Mode.STALKING_POSE;
                spawnPos = pickSurfaceSpawnPos(level, player, 14, 28);
            } else if (r < 85) {
                // Window scenario.
                mode = Mode.WINDOW;
                spawnPos = pickSurfaceSpawnPos(level, player, 5, 10);
            } else if (r < 95) {
                // Looking-from-behind-cover scenario.
                mode = Mode.LOOKING;
                spawnPos = pickSurfaceSpawnPos(level, player, 8, 14);
            } else {
                // Rare: spawn already walking.
                mode = Mode.NORMAL;
                spawnPos = pickSurfaceSpawnPos(level, player, 16, 32);
            }

            if (spawnPos == null) continue;

            ConductorNo9Entity c = ModEntities.CONDUCTOR_NO_9.get().create(level);
            if (c == null) return;
            c.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    level.getRandom().nextFloat() * 360F, 0);
            c.setMode(mode);
            c.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.EVENT, null, null);
            level.addFreshEntity(c);

            MidnightTransit.LOGGER.debug("Spawned Conductor No 9 mode={} near {}",
                    mode, player.getName().getString());
            return;
        }
    }

    private BlockPos pickSurfaceSpawnPos(ServerLevel level, ServerPlayer player, int minDist, int maxDist) {
        BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = RNG.nextDouble() * Math.PI * 2.0;
            double dist = minDist + RNG.nextInt(Math.max(1, maxDist - minDist + 1));
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            BlockState here = level.getBlockState(candidate);
            BlockState above = level.getBlockState(candidate.above());
            BlockState below = level.getBlockState(candidate.below());
            if (here.isAir() && above.isAir() && !below.isAir()) {
                return candidate;
            }
        }
        return null;
    }

    // --- Footsteps / HIDE scenario --------------------------------------------

    /**
     * Per spec: "footsteps sounds plays sometimes, and, after sound ends, player has 10
     * seconds to hide, after 10 seconds, the monster spawns and wanders randomly trying to
     * hide [find] player and if player didn't hide or the monster spots player, then, he
     * chases the player". Followed by a 2-minute hunting window.
     */
    private void tryTriggerFootsteps(ServerLevel level) {
        if (level.isDay()) return; // night only
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            if (hideStates.containsKey(id)) continue; // already running for this player
            // Rare: ~1 in 600 per 5s tick per player = roughly once an hour of nighttime.
            if (RNG.nextInt(600) != 0) continue;
            // Don't trigger if a conductor is already loaded in this level (would compete).
            if (hasConductor(level)) continue;

            // Play footsteps at the player's position so they actually hear it.
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.FOOTSTEPS.get(), SoundSource.HOSTILE, 1.2F, 1.0F);

            // Start phase 0: 10-second HIDE countdown.
            hideStates.put(id, new HideState(0, 200)); // 200 ticks = 10s
            PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketHandler.HideOverlayPacket(10, 0));
        }
    }

    private void tickHideStates(Iterable<ServerLevel> levels) {
        if (hideStates.isEmpty()) return;
        // Iterate snapshot so we can remove entries safely.
        for (UUID id : hideStates.keySet().toArray(new UUID[0])) {
            HideState st = hideStates.get(id);
            if (st == null) continue;

            // Find the player by UUID across all loaded levels.
            ServerPlayer player = null;
            ServerLevel inLevel = null;
            for (ServerLevel l : levels) {
                ServerPlayer sp = l.getServer().getPlayerList().getPlayer(id);
                if (sp != null) { player = sp; inLevel = (ServerLevel) sp.level; break; }
            }
            if (player == null) {
                hideStates.remove(id);
                continue;
            }

            st.ticksLeft--;

            if (st.phase == 0) {
                // Countdown to HE'S HERE. Send periodic refresh packet so countdown UI updates.
                if (st.ticksLeft % 20 == 0) {
                    int secondsLeft = Math.max(0, (st.ticksLeft + 19) / 20);
                    PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(player2(player)),
                            new PacketHandler.HideOverlayPacket(secondsLeft, 0));
                }
                if (st.ticksLeft <= 0) {
                    // Transition to phase 1: spawn the Conductor in "NORMAL" wandering mode
                    // and start the 2-minute hunt clock.
                    st.phase = 1;
                    st.ticksLeft = 2400; // 2 minutes
                    BlockPos spawnPos = pickSurfaceSpawnPos(inLevel, player, 16, 24);
                    if (spawnPos == null) {
                        // Couldn't find a spot - cancel
                        hideStates.remove(id);
                        PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(player2(player)),
                                new PacketHandler.HideOverlayPacket(0, 2));
                        continue;
                    }
                    ConductorNo9Entity c = ModEntities.CONDUCTOR_NO_9.get().create(inLevel);
                    if (c != null) {
                        c.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                                inLevel.getRandom().nextFloat() * 360F, 0);
                        c.setMode(Mode.NORMAL);
                        c.finalizeSpawn(inLevel, inLevel.getCurrentDifficultyAt(spawnPos),
                                MobSpawnType.EVENT, null, null);
                        inLevel.addFreshEntity(c);
                        st.conductorId = c.getUUID();
                    }
                    PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(player2(player)),
                            new PacketHandler.HideOverlayPacket(120, 1));
                }
            } else if (st.phase == 1) {
                // 2-minute hunt window. End when timer expires OR the conductor went aggressive.
                if (st.ticksLeft <= 0) {
                    // Despawn the wander conductor if still alive.
                    if (st.conductorId != null) {
                        net.minecraft.world.entity.Entity e = inLevel.getEntity(st.conductorId);
                        if (e instanceof ConductorNo9Entity && !((ConductorNo9Entity) e).isConductorAggressive()) {
                            e.discard();
                        }
                    }
                    hideStates.remove(id);
                    PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(player2(player)),
                            new PacketHandler.HideOverlayPacket(0, 2));
                }
            }
        }
    }

    /** Small helper because PacketDistributor.PLAYER.with(...) takes a Supplier. */
    private static java.util.function.Supplier<ServerPlayer> player2(ServerPlayer p) {
        return () -> p;
    }

    // --- Auto-give Message [Audio] item ---------------------------------------

    /** Persistent flag key stored under Forge's PERSISTED_NBT_TAG so it survives respawns. */
    private static final String GIVEN_FLAG = "midnight_transit.given_message_audio";

    /**
     * First time a player joins this world, give them the Message [Audio] item. The flag
     * is stored in the player's persistent NBT, which is per-player per-world - so each
     * world hands the item out once, and subsequent logins to the same world don't re-give it.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level.isClientSide) return;

        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);

        if (!persisted.getBoolean(GIVEN_FLAG)) {
            ItemStack stack = new ItemStack(ModItems.MESSAGE_AUDIO.get());
            if (!player.getInventory().add(stack)) {
                // Inventory full - just drop it near them.
                player.drop(stack, false);
            }
            persisted.putBoolean(GIVEN_FLAG, true);
            root.put(Player.PERSISTED_NBT_TAG, persisted);
            MidnightTransit.LOGGER.debug("Gave Message [Audio] to {} (first join in this world)",
                    player.getName().getString());
        }
    }
}
