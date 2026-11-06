package com.celularmod.core;

import java.util.regex.Pattern;

/** Validação e sanitização de textos vindos de clientes (nunca confie no cliente). */
public final class Text {
    private Text() {}

    public static final int NAME_MIN = 2, NAME_MAX = 24;
    public static final int MESSAGE_MAX = 500;
    public static final int POST_MAX = 280;

    private static final Pattern SITE_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{1,31}$");

    /** Remove caracteres de controle e o caractere de formatação '§'; colapsa espaços. */
    public static String clean(String s, boolean keepNewlines) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' && keepNewlines) { sb.append('\n'); lastSpace = false; continue; }
            if (c == '§' || Character.isISOControl(c) && c != ' ') c = ' ';
            if (Character.isWhitespace(c)) {
                if (!lastSpace) sb.append(' ');
                lastSpace = true;
            } else {
                sb.append(c);
                lastSpace = false;
            }
        }
        return sb.toString().trim();
    }

    public static Res<String> displayName(String raw) {
        String n = clean(raw, false);
        if (n.length() < NAME_MIN || n.length() > NAME_MAX)
            return Res.fail("O nome deve ter entre " + NAME_MIN + " e " + NAME_MAX + " caracteres.");
        return Res.ok(n);
    }

    public static Res<String> message(String raw) {
        String m = clean(raw, false);
        if (m.isEmpty()) return Res.fail("Mensagem vazia.");
        if (m.length() > MESSAGE_MAX) return Res.fail("Mensagem muito longa (máx. " + MESSAGE_MAX + ").");
        return Res.ok(m);
    }

    public static Res<String> post(String raw) {
        String m = clean(raw, false);
        if (m.isEmpty()) return Res.fail("A publicação está vazia.");
        if (m.length() > POST_MAX) return Res.fail("Publicação muito longa (máx. " + POST_MAX + ").");
        return Res.ok(m);
    }

    /** IDs reservados: "celular-host" é a origem da página-moldura do navegador. */
    public static boolean validSiteId(String id) {
        return id != null && SITE_ID.matcher(id).matches() && !id.equals("celular-host");
    }

    /** Só aceita convites https do Discord (ou vazio para desativar). */
    public static Res<String> discordUrl(String raw) {
        String u = raw == null ? "" : raw.trim();
        if (u.isEmpty()) return Res.ok("");
        if (u.length() > 200) return Res.fail("URL muito longa.");
        if (!Pattern.compile("^https://(discord\\.gg|discord\\.com|www\\.discord\\.com|discordapp\\.com)/[A-Za-z0-9/_\\-.?=&]+$")
                .matcher(u).matches())
            return Res.fail("Use um link https de convite do Discord (discord.gg/... ou discord.com/invite/...).");
        return Res.ok(u);
    }
}
