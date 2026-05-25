package com.redstonedev.midnighttransit.client.overlay;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Simple static state for the HIDE overlay. The server sends HideOverlayPacket and the
 * packet handler calls set(); the HUD render listener reads currentPhase / secondsShown.
 *
 * Phase 0 = "HIDE" with N-second countdown
 * Phase 1 = "HE'S HERE" (shake effect)
 * Phase 2 = clear (don't render)
 */
@OnlyIn(Dist.CLIENT)
public final class HideOverlayState {
    private HideOverlayState() {}

    public static volatile int currentPhase = 2;   // 2 = nothing showing
    public static volatile int secondsShown = 0;

    public static void set(int phase, int seconds) {
        currentPhase = phase;
        secondsShown = seconds;
    }
}
