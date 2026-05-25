package com.redstonedev.midnighttransit.network;

import com.redstonedev.midnighttransit.MidnightTransit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MidnightTransit.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++,
                HideOverlayPacket.class,
                HideOverlayPacket::encode,
                HideOverlayPacket::decode,
                HideOverlayPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /**
     * Triggers the HIDE overlay on the client. duration is total seconds; phase 0 = "HIDE" countdown,
     * phase 1 = "HE'S HERE" with shake. seconds counts down on the client.
     */
    public static class HideOverlayPacket {
        public final int totalSeconds;
        public final int phase; // 0 = HIDE countdown, 1 = HE'S HERE, 2 = clear

        public HideOverlayPacket(int totalSeconds, int phase) {
            this.totalSeconds = totalSeconds;
            this.phase = phase;
        }

        public static void encode(HideOverlayPacket p, FriendlyByteBuf buf) {
            buf.writeInt(p.totalSeconds);
            buf.writeInt(p.phase);
        }

        public static HideOverlayPacket decode(FriendlyByteBuf buf) {
            return new HideOverlayPacket(buf.readInt(), buf.readInt());
        }

        public static void handle(HideOverlayPacket p, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // Run only on client - the overlay handler is client-only.
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                        net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.redstonedev.midnighttransit.client.overlay.HideOverlayState.set(p.phase, p.totalSeconds));
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
