package com.redstonedev.midnighttransit.client.sound;

import com.redstonedev.midnighttransit.entity.ConductorNo9Entity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientChaseSoundStarter {
    private ClientChaseSoundStarter() {}
    public static void start(ConductorNo9Entity entity) {
        Minecraft.getInstance().getSoundManager().play(new ConductorChaseSoundInstance(entity));
    }
}
