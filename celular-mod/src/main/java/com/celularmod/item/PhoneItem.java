package com.celularmod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * O item Celular. A interface é 100% 2D e vive no cliente; o item só dispara o gancho de "desbloquear"
 * (clique direito). O gancho é um Runnable para esta classe não referenciar código de cliente.
 */
public class PhoneItem extends Item {
    /** Definido pelo cliente em ClientInit. No servidor dedicado fica nulo (não faz nada). */
    public static volatile Runnable clientUseHook = null;

    public PhoneItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && clientUseHook != null) clientUseHook.run();
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.celular.phone.tooltip"));
    }
}
