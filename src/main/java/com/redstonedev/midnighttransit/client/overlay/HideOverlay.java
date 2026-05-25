package com.redstonedev.midnighttransit.client.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The HUD overlay that draws "HIDE" + countdown (phase 0) or shaking "HE'S HERE" (phase 1).
 * Registered in ClientSetup via RegisterGuiOverlaysEvent.
 */
@OnlyIn(Dist.CLIENT)
public class HideOverlay implements IGuiOverlay {

    public static final HideOverlay INSTANCE = new HideOverlay();

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, PoseStack poseStack,
                       float partialTick, int width, int height) {
        int phase = HideOverlayState.currentPhase;
        if (phase == 2) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        String titleText;
        String subtitleText;
        int titleColor;
        float shakeX = 0F;
        float shakeY = 0F;

        if (phase == 0) {
            titleText = "HIDE";
            subtitleText = HideOverlayState.secondsShown + "s";
            titleColor = 0xFFCC0000; // red
        } else {
            titleText = "HE'S HERE";
            subtitleText = "";
            titleColor = 0xFFFF0000; // brighter red
            // Violent shake - random offset per render frame.
            java.util.Random r = new java.util.Random(System.currentTimeMillis() / 50);
            shakeX = (r.nextFloat() - 0.5F) * 6F;
            shakeY = (r.nextFloat() - 0.5F) * 6F;
        }

        // Push pose, translate for shake, render scaled, pop.
        poseStack.pushPose();
        poseStack.translate(width / 2.0F + shakeX, height / 4.0F + shakeY, 0);
        poseStack.scale(3.0F, 3.0F, 1.0F);

        Component titleComp = Component.literal(titleText)
                .withStyle(ChatFormatting.BOLD);
        int titleWidth = font.width(titleComp);
        GuiComponent.drawString(poseStack, font, titleComp, -titleWidth / 2, 0, titleColor);

        poseStack.popPose();

        if (!subtitleText.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(width / 2.0F, height / 4.0F + 40, 0);
            poseStack.scale(2.0F, 2.0F, 1.0F);
            Component subComp = Component.literal(subtitleText).withStyle(ChatFormatting.BOLD);
            int subWidth = font.width(subComp);
            GuiComponent.drawString(poseStack, font, subComp, -subWidth / 2, 0, 0xFFFFFFFF);
            poseStack.popPose();
        }
    }
}
