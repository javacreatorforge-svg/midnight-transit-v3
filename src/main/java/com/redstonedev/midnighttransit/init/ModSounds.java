package com.redstonedev.midnighttransit.init;

import com.redstonedev.midnighttransit.MidnightTransit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MidnightTransit.MODID);

    public static final RegistryObject<SoundEvent> CHASE_THEME    = register("conductor_chase_theme");
    public static final RegistryObject<SoundEvent> HURT           = register("conductor_hurt");
    public static final RegistryObject<SoundEvent> DEATH          = register("conductor_death");
    public static final RegistryObject<SoundEvent> FOOTSTEPS      = register("conductor_footsteps");
    public static final RegistryObject<SoundEvent> HELLO          = register("conductor_hello");
    public static final RegistryObject<SoundEvent> HELP           = register("conductor_help");
    public static final RegistryObject<SoundEvent> ISEEYOU        = register("conductor_iseeyou");
    public static final RegistryObject<SoundEvent> MESSAGE_AUDIO  = register("message_audio");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> new SoundEvent(new ResourceLocation(MidnightTransit.MODID, name)));
    }
}
