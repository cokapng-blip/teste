package com.celularmod.core;

import com.celularmod.core.Models.Account;
import com.celularmod.core.Models.Msg;
import com.celularmod.core.Models.Post;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Armazenamento em memória com persistência em JSON (escrita atômica: arquivo temporário + move).
 * Acesso só pela thread do servidor; {@link #flushIfDirty()} é chamado periodicamente e ao desligar.
 */
public final class Database {
    public final Path dir;
    public final LongSupplier clock;

    public final Map<UUID, Account> waAccounts = new LinkedHashMap<>();
    public final Map<UUID, Set<UUID>> waContacts = new HashMap<>();
    public final List<Msg> messages = new ArrayList<>();
    public final Map<UUID, Account> thAccounts = new LinkedHashMap<>();
    public final List<Post> posts = new ArrayList<>();
    public long nextMsgId = 1, nextPostId = 1;

    private boolean dirtyWa, dirtyMsgs, dirtyTh, dirtyPosts;

    public Database(Path dir, LongSupplier clock) {
        this.dir = dir;
        this.clock = clock;
    }

    public void markWa() { dirtyWa = true; }
    public void markMsgs() { dirtyMsgs = true; }
    public void markTh() { dirtyTh = true; }
    public void markPosts() { dirtyPosts = true; }

    // ---------------- carga ----------------
    public void load() throws IOException {
        Files.createDirectories(dir);
        Map<String, Object> wa = read("whatsapp_accounts.json");
        for (Object o : Json.asList(wa.get("accounts"))) { Account a = Account.fromMap(Json.asMap(o)); waAccounts.put(a.owner, a); }
        for (Map.Entry<String, Object> e : Json.asMap(wa.get("contacts")).entrySet()) {
            Set<UUID> s = new LinkedHashSet<>();
            for (Object o : Json.asList(e.getValue())) s.add(UUID.fromString(String.valueOf(o)));
            waContacts.put(UUID.fromString(e.getKey()), s);
        }
        Map<String, Object> ms = read("whatsapp_messages.json");
        for (Object o : Json.asList(ms.get("messages"))) messages.add(Msg.fromMap(Json.asMap(o)));
        nextMsgId = Math.max(Json.num(ms, "nextId", 1), maxId(messages) + 1);
        Map<String, Object> th = read("threads_accounts.json");
        for (Object o : Json.asList(th.get("accounts"))) { Account a = Account.fromMap(Json.asMap(o)); thAccounts.put(a.owner, a); }
        Map<String, Object> ps = read("threads_posts.json");
        for (Object o : Json.asList(ps.get("posts"))) posts.add(Post.fromMap(Json.asMap(o)));
        long mp = 0;
        for (Post p : posts) mp = Math.max(mp, p.id);
        nextPostId = Math.max(Json.num(ps, "nextId", 1), mp + 1);
    }

    private static long maxId(List<Msg> l) {
        long m = 0;
        for (Msg x : l) m = Math.max(m, x.id);
        return m;
    }

    private Map<String, Object> read(String name) throws IOException {
        Path f = dir.resolve(name);
        if (!Files.exists(f)) return new LinkedHashMap<>();
        try {
            return Json.asMap(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException ex) {
            // arquivo corrompido: preserva uma cópia e começa vazio em vez de perder tudo silenciosamente
            Files.copy(f, dir.resolve(name + ".corrupt-" + clock.getAsLong()), StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("Arquivo corrompido: " + name + " (cópia preservada)", ex);
        }
    }

    // ---------------- gravação ----------------
    public synchronized void flushIfDirty() {
        try {
            if (dirtyWa) {
                List<Object> accs = new ArrayList<>();
                for (Account a : waAccounts.values()) accs.add(a.toMap());
                Map<String, Object> contacts = new LinkedHashMap<>();
                for (Map.Entry<UUID, Set<UUID>> e : waContacts.entrySet()) {
                    List<String> l = new ArrayList<>();
                    for (UUID u : e.getValue()) l.add(u.toString());
                    contacts.put(e.getKey().toString(), l);
                }
                write("whatsapp_accounts.json", Json.obj("accounts", accs, "contacts", contacts));
                dirtyWa = false;
            }
            if (dirtyMsgs) {
                List<Object> l = new ArrayList<>();
                for (Msg m : messages) l.add(m.toMap());
                write("whatsapp_messages.json", Json.obj("nextId", nextMsgId, "messages", l));
                dirtyMsgs = false;
            }
            if (dirtyTh) {
                List<Object> accs = new ArrayList<>();
                for (Account a : thAccounts.values()) accs.add(a.toMap());
                write("threads_accounts.json", Json.obj("accounts", accs));
                dirtyTh = false;
            }
            if (dirtyPosts) {
                List<Object> l = new ArrayList<>();
                for (Post p : posts) l.add(p.toMap());
                write("threads_posts.json", Json.obj("nextId", nextPostId, "posts", l));
                dirtyPosts = false;
            }
        } catch (IOException e) {
            throw new RuntimeException("Falha ao salvar dados do Celular: " + e.getMessage(), e);
        }
    }

    private void write(String name, Object data) throws IOException {
        Files.createDirectories(dir);
        Path tmp = dir.resolve(name + ".tmp");
        Files.writeString(tmp, Json.stringify(data), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
