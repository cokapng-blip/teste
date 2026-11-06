package com.celularmod.client;

import com.celularmod.CelularMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Eventos do cliente: segurar o item, clique direito, login/logout e tick. */
@EventBusSubscriber(modid = CelularMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    /** Quando o celular foi fechado pela última vez — evita reabrir pelo mesmo clique. */
    public static volatile long lastClose;
    private static int helloTries, helloCooldown;

    public static boolean holdingPhone(Player p) {
        return p != null && (p.getMainHandItem().is(CelularMod.PHONE.get()) || p.getOffhandItem().is(CelularMod.PHONE.get()));
    }

    public static void openPhone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (System.currentTimeMillis() - lastClose < 250) return;
        if (!holdingPhone(mc.player)) return;
        mc.setScreen(new PhoneScreen());
    }

    /** Clique direito com o celular na mão: desbloqueia (e NÃO usa o item/bloco por baixo). */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.isUseItem() && mc.screen == null && holdingPhone(mc.player)) {
            e.setCanceled(true);
            e.setSwingHand(false);
            openPhone();
        }
    }

    /** Sem mão do personagem segurando o aparelho: o celular é só a interface 2D. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent e) {
        if (e.getItemStack().is(CelularMod.PHONE.get())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        boolean holding = mc.player != null && holdingPhone(mc.player);
        PhoneRenderer.setHolding(holding);
        if (!holding && mc.screen instanceof PhoneScreen) mc.setScreen(null);   // trocou de item: fecha
        Toasts.prune();
        // apresenta-se ao servidor (algumas tentativas, caso o canal ainda não esteja pronto)
        if (mc.player != null && !ClientState.hello && helloTries < 6 && --helloCooldown <= 0) {
            if (ClientNet.canSend()) { ClientNet.send("hello"); helloTries++; }
            helloCooldown = 60;
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
        ClientState.reset();
        ClientSettings.load();
        BrowserManager.start();
        helloTries = 0;
        helloCooldown = 20;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        BrowserManager.stop();
        com.celularmod.client.browser.McefBridge.closeAll();
        ClientState.reset();
        PhoneRenderer.onScreenClosed();
    }
}
