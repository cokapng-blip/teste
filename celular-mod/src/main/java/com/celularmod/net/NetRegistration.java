package com.celularmod.net;

import com.celularmod.CelularMod;
import com.celularmod.server.ServerHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Canal de rede do mod. {@code optional()}: um cliente SEM o mod ainda consegue entrar no servidor
 * (só não terá o celular); o servidor nunca envia pacotes a quem não tem o canal.
 */
@EventBusSubscriber(modid = CelularMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NetRegistration {
    private NetRegistration() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1").optional();
        r.playToServer(C2SPacket.TYPE, C2SPacket.CODEC, (pkt, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) ServerHandler.handle(sp, pkt.json());
                }));
        r.playToClient(S2CPacket.TYPE, S2CPacket.CODEC, (pkt, ctx) ->
                ctx.enqueueWork(() -> ClientBridge.handler.accept(pkt)));
    }
}
