package com.celularmod.client;

import com.celularmod.CelularMod;
import com.celularmod.item.PhoneItem;
import com.celularmod.net.ClientBridge;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Inicialização do cliente (bus do mod). */
@EventBusSubscriber(modid = CelularMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientInit {
    private ClientInit() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            ClientSettings.load();
            PhoneItem.clientUseHook = ClientEvents::openPhone;
            ClientBridge.handler = ClientState::handle;
        });
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CelularMod.MODID, "phone_hud"), PhoneHud::render);
    }
}
