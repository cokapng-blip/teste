package com.celularmod.net;

import java.util.function.Consumer;

/** Ponte para o cliente: evita que o código comum carregue classes de cliente no servidor dedicado. */
public final class ClientBridge {
    private ClientBridge() {}

    public static volatile Consumer<S2CPacket> handler = p -> {};
}
