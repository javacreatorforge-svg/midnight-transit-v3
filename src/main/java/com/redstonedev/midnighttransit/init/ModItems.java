package com.redstonedev.midnighttransit.init;

import com.redstonedev.midnighttransit.MidnightTransit;
import com.redstonedev.midnighttransit.item.MessageAudioItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MidnightTransit.MODID);

    // Spawn egg with "human skin / darker human skin" tints per the spec.
    public static final RegistryObject<ForgeSpawnEggItem> CONDUCTOR_NO_9_SPAWN_EGG =
            ITEMS.register("conductor_no_9_spawn_egg",
                    () -> new ForgeSpawnEggItem(
                            ModEntities.CONDUCTOR_NO_9,
                            0xE8B294, // base: warm human skin tone
                            0x6B4423, // spot: darker human skin
                            new Item.Properties().tab(CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> MESSAGE_AUDIO =
            ITEMS.register("message_audio",
                    () -> new MessageAudioItem(new Item.Properties()
                            .tab(CreativeModeTab.TAB_MISC)
                            .stacksTo(1)));
}
