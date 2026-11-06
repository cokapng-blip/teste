package com.celularmod.client.apps;

import com.celularmod.client.PhoneRenderer;

/** Atalhos para o Ui compartilhado do celular. */
final class PhoneUiHelper {
    private PhoneUiHelper() {}

    static void resetScroll(String id) { PhoneRenderer.ui.resetScroll(id); }
}
