package com.redstonedev.midnighttransit.client.renderer;

import com.redstonedev.midnighttransit.client.model.ConductorNo9Model;
import com.redstonedev.midnighttransit.entity.ConductorNo9Entity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class ConductorNo9Renderer extends GeoEntityRenderer<ConductorNo9Entity> {
    public ConductorNo9Renderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ConductorNo9Model());
        this.shadowRadius = 0.8F;
        // Geo was authored at the original smaller hitbox - scale up to match the new 1.5 x 4.5
        // hitbox. Both axes scaled by 1.5x; tweak these two numbers to taste.
        this.widthScale  = 1.5F;
        this.heightScale = 1.5F;
    }
}
