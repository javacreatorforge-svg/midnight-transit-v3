package com.redstonedev.midnighttransit.item;

import com.redstonedev.midnighttransit.init.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Message [Audio] item - right-click to play the audio. No on-screen text.
 */
public class MessageAudioItem extends Item {

    public MessageAudioItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            // Play the audio at the player's position.
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.MESSAGE_AUDIO.get(), SoundSource.VOICE, 1.0F, 1.0F);
        }
        return InteractionResultHolder.success(stack);
    }
}
