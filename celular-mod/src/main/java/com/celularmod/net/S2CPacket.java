package com.celularmod.net;

import com.celularmod.CelularMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor → cliente. JSON + bloco binário opcional (pedaços de arquivos de sites). Limite vanilla: 1 MiB. */
public record S2CPacket(String json, byte[] bin) implements CustomPacketPayload {
    public static final int MAX_JSON_CHARS = 100_000;
    public static final int MAX_BIN = 180_000;

    public static final Type<S2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CelularMod.MODID, "s2c"));
    public static final StreamCodec<ByteBuf, S2CPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_JSON_CHARS), S2CPacket::json,
            ByteBufCodecs.byteArray(MAX_BIN), S2CPacket::bin,
            S2CPacket::new);

    public S2CPacket(String json) { this(json, new byte[0]); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
