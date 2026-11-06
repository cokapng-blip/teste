package com.celularmod.core;

/** Resultado de uma operação de serviço: sucesso com valor, ou erro com mensagem legível (pt-BR). */
public final class Res<T> {
    public final boolean ok;
    public final String error;
    public final T value;

    private Res(boolean ok, String error, T value) {
        this.ok = ok;
        this.error = error;
        this.value = value;
    }

    public static <T> Res<T> ok(T v) { return new Res<>(true, null, v); }

    public static <T> Res<T> fail(String msg) { return new Res<>(false, msg, null); }
}
