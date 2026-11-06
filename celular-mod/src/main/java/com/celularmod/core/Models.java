package com.celularmod.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Modelos de dados persistidos. Todos têm toMap/fromMap para o JSON do mod. */
public final class Models {
    private Models() {}

    /** Conta de um app (WhatsApp ou Threads). {@code handle} é o identificador único (número ou @usuário). */
    public static final class Account {
        public UUID owner;
        public String handle;
        public String name;
        public long createdAt;
        public long nameChangedAt;
        public boolean allowStrangers = true;   // WhatsApp: aceitar mensagens de quem não é contato
        public boolean searchable = true;       // aparecer em buscas por nome

        public Map<String, Object> toMap() {
            return Json.obj("owner", owner.toString(), "handle", handle, "name", name, "createdAt", createdAt,
                    "nameChangedAt", nameChangedAt, "allowStrangers", allowStrangers, "searchable", searchable);
        }

        public static Account fromMap(Map<String, Object> m) {
            Account a = new Account();
            a.owner = UUID.fromString(Json.str(m, "owner", ""));
            a.handle = Json.str(m, "handle", "");
            a.name = Json.str(m, "name", "?");
            a.createdAt = Json.num(m, "createdAt", 0);
            a.nameChangedAt = Json.num(m, "nameChangedAt", 0);
            a.allowStrangers = Json.bool(m, "allowStrangers", true);
            a.searchable = Json.bool(m, "searchable", true);
            return a;
        }

        /** Visão pública (sem UUID do dono). */
        public Map<String, Object> publicView() {
            return Json.obj("handle", handle, "name", name);
        }
    }

    public static final class Msg {
        public long id;
        public UUID from, to;
        public String text;
        public long time;
        public boolean read;
        public String kind = "text";   // "text" | "share"

        public Map<String, Object> toMap() {
            return Json.obj("id", id, "from", from.toString(), "to", to.toString(), "text", text, "time", time,
                    "read", read, "kind", kind);
        }

        public static Msg fromMap(Map<String, Object> m) {
            Msg x = new Msg();
            x.id = Json.num(m, "id", 0);
            x.from = UUID.fromString(Json.str(m, "from", ""));
            x.to = UUID.fromString(Json.str(m, "to", ""));
            x.text = Json.str(m, "text", "");
            x.time = Json.num(m, "time", 0);
            x.read = Json.bool(m, "read", false);
            x.kind = Json.str(m, "kind", "text");
            return x;
        }
    }

    public static final class Post {
        public long id;
        public UUID author;
        public String text;
        public long time;
        public boolean deleted;
        public final Set<UUID> likes = new LinkedHashSet<>();

        public Map<String, Object> toMap() {
            java.util.List<String> l = new java.util.ArrayList<>();
            for (UUID u : likes) l.add(u.toString());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("author", author.toString()); m.put("text", text); m.put("time", time);
            m.put("deleted", deleted); m.put("likes", l);
            return m;
        }

        public static Post fromMap(Map<String, Object> m) {
            Post p = new Post();
            p.id = Json.num(m, "id", 0);
            p.author = UUID.fromString(Json.str(m, "author", ""));
            p.text = Json.str(m, "text", "");
            p.time = Json.num(m, "time", 0);
            p.deleted = Json.bool(m, "deleted", false);
            for (Object o : Json.asList(m.get("likes"))) {
                try { p.likes.add(UUID.fromString(String.valueOf(o))); } catch (IllegalArgumentException ignored) {}
            }
            return p;
        }
    }
}
