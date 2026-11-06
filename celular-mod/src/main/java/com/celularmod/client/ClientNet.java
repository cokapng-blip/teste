package com.celularmod.client;

import com.celularmod.core.Json;
import com.celularmod.net.C2SPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

/** Envio de pacotes ao servidor. Só envia se o servidor realmente tem o mod (canal registrado). */
public final class ClientNet {
    private ClientNet() {}

    public static boolean canSend() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return false;
        try {
            return mc.getConnection().hasChannel(C2SPacket.TYPE);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void send(Map<String, Object> msg) {
        if (!canSend()) return;
        String json = Json.stringify(msg);
        if (json.length() > 7900) return;   // o servidor também rejeita; evita desconexão por pacote grande
        PacketDistributor.sendToServer(new C2SPacket(json));
    }

    public static void send(String type, Object... kv) {
        Map<String, Object> m = Json.obj(kv);
        m.put("t", type);
        send(m);
    }
}
