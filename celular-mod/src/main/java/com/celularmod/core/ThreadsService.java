package com.celularmod.core;

import com.celularmod.core.Models.Account;
import com.celularmod.core.Models.Msg;
import com.celularmod.core.Models.Post;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.*;

/** Regras do Threads: contas, publicações, curtidas, feed, moderação e compartilhamento via WhatsApp. */
public final class ThreadsService {
    public static final long RENAME_COOLDOWN_MS = 5 * 60_000L;
    public static final int FEED_LIMIT = 40;
    private static final int MAX_POSTS_STORED = 5000;

    private final Database db;
    private final WhatsAppService wa;
    private final SecureRandom rnd = new SecureRandom();

    public ThreadsService(Database db, WhatsAppService wa) {
        this.db = db;
        this.wa = wa;
    }

    public Account account(UUID p) { return db.thAccounts.get(p); }

    public Account byUser(String user) {
        if (user == null) return null;
        String u = user.startsWith("@") ? user.substring(1) : user;
        for (Account a : db.thAccounts.values()) if (a.handle.equalsIgnoreCase(u)) return a;
        return null;
    }

    public Res<Account> createAccount(UUID p, String rawName) {
        if (db.thAccounts.containsKey(p)) return Res.fail("Você já tem uma conta do Threads.");
        Res<String> n = Text.displayName(rawName);
        if (!n.ok) return Res.fail(n.error);
        Account a = new Account();
        a.owner = p;
        a.name = n.value;
        a.createdAt = db.clock.getAsLong();
        a.nameChangedAt = a.createdAt;
        a.handle = newUser(n.value);
        db.thAccounts.put(p, a);
        db.markTh();
        return Res.ok(a);
    }

    private String newUser(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (base.length() < 3) base = "jogador";
        if (base.length() > 14) base = base.substring(0, 14);
        while (true) {
            String u = base + (1000 + rnd.nextInt(9000));
            if (byUser(u) == null) return u;
        }
    }

    public Res<Account> rename(UUID p, String rawName) {
        Account a = account(p);
        if (a == null) return Res.fail("Crie uma conta primeiro.");
        Res<String> n = Text.displayName(rawName);
        if (!n.ok) return Res.fail(n.error);
        long now = db.clock.getAsLong();
        long wait = a.nameChangedAt + RENAME_COOLDOWN_MS - now;
        if (wait > 0) return Res.fail("Aguarde " + (wait / 1000 + 1) + "s para alterar o nome novamente.");
        a.name = n.value;
        a.nameChangedAt = now;
        db.markTh();
        return Res.ok(a);
    }

    // ---------------- publicações ----------------
    public Res<Post> post(UUID p, String rawText) {
        if (account(p) == null) return Res.fail("Crie uma conta primeiro.");
        Res<String> t = Text.post(rawText);
        if (!t.ok) return Res.fail(t.error);
        Post post = new Post();
        post.id = db.nextPostId++;
        post.author = p;
        post.text = t.value;
        post.time = db.clock.getAsLong();
        db.posts.add(post);
        while (db.posts.size() > MAX_POSTS_STORED) db.posts.remove(0);
        db.markPosts();
        return Res.ok(post);
    }

    public Post find(long id) {
        for (Post p : db.posts) if (p.id == id && !p.deleted) return p;
        return null;
    }

    /** Curtir/descurtir é idempotente: um conjunto de UUIDs impede curtidas duplicadas. */
    public Res<Post> like(UUID p, long postId, boolean like) {
        if (account(p) == null) return Res.fail("Crie uma conta primeiro.");
        Post post = find(postId);
        if (post == null) return Res.fail("Publicação não encontrada.");
        boolean changed = like ? post.likes.add(p) : post.likes.remove(p);
        if (changed) db.markPosts();
        return Res.ok(post);
    }

    public Res<Post> delete(UUID actor, long postId, boolean actorIsAdmin) {
        Post post = find(postId);
        if (post == null) return Res.fail("Publicação não encontrada.");
        if (!actorIsAdmin && !post.author.equals(actor)) return Res.fail("Você só pode apagar suas próprias publicações.");
        post.deleted = true;
        db.markPosts();
        return Res.ok(post);
    }

    public Res<List<Post>> feed(UUID viewer, String tab, String profileUser) {
        if (account(viewer) == null) return Res.fail("Crie uma conta primeiro.");
        List<Post> l = new ArrayList<>();
        UUID only = null;
        if ("profile".equals(tab)) {
            Account a = byUser(profileUser);
            if (a == null) return Res.fail("Perfil não encontrado.");
            only = a.owner;
        }
        for (Post p : db.posts) if (!p.deleted && (only == null || p.author.equals(only))) l.add(p);
        if ("foryou".equals(tab)) {
            final long now = db.clock.getAsLong();
            // "Para você": publicações recentes, com impulso para as mais curtidas
            l.sort((a, b) -> Long.compare(score(b, now), score(a, now)));
        } else {
            l.sort((a, b) -> Long.compare(b.time, a.time));
        }
        if (l.size() > FEED_LIMIT) l = new ArrayList<>(l.subList(0, FEED_LIMIT));
        return Res.ok(l);
    }

    private static long score(Post p, long now) { return p.time + p.likes.size() * 120_000L; }

    public Map<String, Object> view(UUID viewer, Post p) {
        Account a = db.thAccounts.get(p.author);
        return Json.obj("id", p.id, "user", a == null ? "?" : a.handle, "name", a == null ? "Conta removida" : a.name,
                "text", p.text, "time", p.time, "likes", p.likes.size(), "liked", p.likes.contains(viewer),
                "mine", p.author.equals(viewer));
    }

    public Map<String, Object> profile(UUID viewer, String user) {
        Account a = byUser(user);
        if (a == null) return null;
        int posts = 0, likes = 0;
        for (Post p : db.posts) if (!p.deleted && p.author.equals(a.owner)) { posts++; likes += p.likes.size(); }
        return Json.obj("user", a.handle, "name", a.name, "since", a.createdAt, "posts", posts, "likes", likes,
                "mine", a.owner.equals(viewer));
    }

    // ---------------- compartilhar via WhatsApp ----------------
    /** O texto compartilhado é montado AQUI a partir do post real; o cliente não consegue forjar o conteúdo. */
    public Res<Msg> share(UUID sender, long postId, String toNumber) {
        Post post = find(postId);
        if (post == null) return Res.fail("Publicação não encontrada.");
        Account author = db.thAccounts.get(post.author);
        String who = author == null ? "?" : author.name + " (@" + author.handle + ")";
        String text = "[Threads] " + who + ": " + post.text;
        if (text.length() > Text.MESSAGE_MAX) text = text.substring(0, Text.MESSAGE_MAX - 1) + "…";
        return wa.send(sender, toNumber, text, "share");
    }
}
