package com.celularmod.net;

import com.celularmod.CelularMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente → servidor. Corpo JSON {"t":"tipo",...}. Limite do protocolo vanilla: 32 KiB. */
public record C2SPacket(String json) implements CustomPacketPayload {
    public static final Type<C2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CelularMod.MODID, "c2s"));
    public static final StreamCodec<ByteBuf, C2SPacket> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(8000), C2SPacket::json, C2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
