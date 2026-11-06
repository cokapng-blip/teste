package com.celularmod;

import com.celularmod.item.PhoneItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;

/** Ponto de entrada comum (cliente + servidor dedicado). Nada de classes de cliente aqui. */
@Mod(CelularMod.MODID)
public class CelularMod {
    public static final String MODID = "celular";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<PhoneItem> PHONE =
            ITEMS.registerItem("phone", PhoneItem::new, new Item.Properties().stacksTo(1));

    public CelularMod(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void creativeTab(BuildCreativeModeTabContentsEvent e) {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(PHONE.get());
        }
    }
}
