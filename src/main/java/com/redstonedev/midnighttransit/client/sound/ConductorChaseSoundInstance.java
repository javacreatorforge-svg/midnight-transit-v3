package com.redstonedev.midnighttransit.client.sound;

import com.redstonedev.midnighttransit.entity.ConductorNo9Entity;
import com.redstonedev.midnighttransit.init.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ConductorChaseSoundInstance extends AbstractTickableSoundInstance {
    private final ConductorNo9Entity conductor;

    public ConductorChaseSoundInstance(ConductorNo9Entity entity) {
        super(ModSounds.CHASE_THEME.get(), SoundSource.HOSTILE, RandomSource.create());
        this.conductor = entity;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.attenuation = Attenuation.LINEAR;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    @Override
    public void tick() {
        if (conductor.isRemoved()
                || !conductor.isAlive()
                || !conductor.isConductorAggressive()) {
            this.stop();
            return;
        }
        this.x = conductor.getX();
        this.y = conductor.getY();
        this.z = conductor.getZ();
    }
}
