package com.celularmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Sons do celular, respeitando o volume/ligar-desligar das configurações locais. */
public final class Sfx {
    private Sfx() {}

    public static void click() {
        if (!ClientSettings.sounds || ClientSettings.volume <= 0) return;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, ClientSettings.volume / 100f * 0.6f));
    }

    public static void notification() {
        if (!ClientSettings.sounds || ClientSettings.volume <= 0) return;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, ClientSettings.volume / 100f));
    }
}
