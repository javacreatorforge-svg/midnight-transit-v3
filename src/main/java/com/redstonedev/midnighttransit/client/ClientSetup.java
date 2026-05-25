package com.redstonedev.midnighttransit.client;

import com.redstonedev.midnighttransit.MidnightTransit;
import com.redstonedev.midnighttransit.client.overlay.HideOverlay;
import com.redstonedev.midnighttransit.client.renderer.ConductorNo9Renderer;
import com.redstonedev.midnighttransit.init.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            EntityRenderers.register(ModEntities.CONDUCTOR_NO_9.get(), ConductorNo9Renderer::new);
        });
    }

    @Mod.EventBusSubscriber(modid = MidnightTransit.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class OverlayRegistration {
        @SubscribeEvent
        public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "midnight_transit_hide", HideOverlay.INSTANCE);
        }
    }
}
