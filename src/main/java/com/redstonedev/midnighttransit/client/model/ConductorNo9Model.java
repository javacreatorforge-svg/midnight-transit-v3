package com.redstonedev.midnighttransit.client.model;

import com.redstonedev.midnighttransit.MidnightTransit;
import com.redstonedev.midnighttransit.entity.ConductorNo9Entity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class ConductorNo9Model extends AnimatedGeoModel<ConductorNo9Entity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(MidnightTransit.MODID, "geo/conductor_no_9.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MidnightTransit.MODID, "textures/entity/conductor_no_9.png");
    private static final ResourceLocation ANIMATIONS =
            new ResourceLocation(MidnightTransit.MODID, "animations/conductor_no_9.animation.json");

    @Override public ResourceLocation getModelResource(ConductorNo9Entity e)     { return MODEL; }
    @Override public ResourceLocation getTextureResource(ConductorNo9Entity e)   { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(ConductorNo9Entity e) { return ANIMATIONS; }
}
