package com.redstonedev.midnighttransit;

import com.mojang.logging.LogUtils;
import com.redstonedev.midnighttransit.client.ClientSetup;
import com.redstonedev.midnighttransit.entity.ConductorNo9Entity;
import com.redstonedev.midnighttransit.event.ForgeEvents;
import com.redstonedev.midnighttransit.init.ModEntities;
import com.redstonedev.midnighttransit.init.ModItems;
import com.redstonedev.midnighttransit.init.ModSounds;
import com.redstonedev.midnighttransit.network.PacketHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import software.bernie.geckolib3.GeckoLib;

@Mod(MidnightTransit.MODID)
public class MidnightTransit {
    public static final String MODID = "midnight_transit";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MidnightTransit() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        GeckoLib.initialize();

        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);
        modBus.addListener(this::entityAttributes);

        MinecraftForge.EVENT_BUS.register(new ForgeEvents());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
        LOGGER.info("Midnight Transit common setup - the train is here.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ClientSetup.onClientSetup(event);
    }

    private void entityAttributes(final EntityAttributeCreationEvent event) {
        event.put(ModEntities.CONDUCTOR_NO_9.get(), ConductorNo9Entity.createAttributes().build());
    }
}
