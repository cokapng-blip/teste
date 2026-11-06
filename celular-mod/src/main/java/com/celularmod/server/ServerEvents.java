package com.celularmod.server;

import com.celularmod.CelularMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Eventos do servidor (bus do jogo). Registrados automaticamente pela anotação. */
@EventBusSubscriber(modid = CelularMod.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onStarting(ServerStartingEvent e) { ServerState.start(e.getServer()); }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) { ServerState.stop(); }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        ServerState s = ServerState.get();
        if (s != null) s.tick();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ServerState s = ServerState.get();
        if (s != null) s.forget(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) { CelularCommands.register(e.getDispatcher()); }
}
