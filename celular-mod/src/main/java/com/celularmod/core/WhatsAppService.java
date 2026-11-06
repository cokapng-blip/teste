package com.celularmod.core;

import com.celularmod.core.Models.Account;
import com.celularmod.core.Models.Msg;

import java.security.SecureRandom;
import java.util.*;

/** Regras do WhatsApp: contas, contatos e mensagens. Toda validação acontece aqui (servidor). */
public final class WhatsAppService {
    public static final int HISTORY_LIMIT = 200;
    private static final int MAX_STORED_PER_CONVERSATION = 2000;
    private static final int MAX_CONTACTS = 200;

    private final Database db;
    private final SecureRandom rnd = new SecureRandom();

    public WhatsAppService(Database db) { this.db = db; }

    public Account account(UUID player) { return db.waAccounts.get(player); }

    public Account byNumber(String number) {
        if (number == null) return null;
        for (Account a : db.waAccounts.values()) if (a.handle.equals(number)) return a;
        return null;
    }

    // ---------------- contas ----------------
    public Res<Account> createAccount(UUID player, String rawName) {
        if (db.waAccounts.containsKey(player)) return Res.fail("Você já tem uma conta do WhatsApp.");
        Res<String> n = Text.displayName(rawName);
        if (!n.ok) return Res.fail(n.error);
        Account a = new Account();
        a.owner = player;
        a.name = n.value;
        a.createdAt = db.clock.getAsLong();
        a.nameChangedAt = a.createdAt;
        a.handle = newNumber();
        db.waAccounts.put(player, a);
        db.markWa();
        return Res.ok(a);
    }

    /** Número no formato 9XXXX-XXXX, único. */
    private String newNumber() {
        while (true) {
            String s = String.format("9%04d-%04d", rnd.nextInt(10000), rnd.nextInt(10000));
            if (byNumber(s) == null) return s;
        }
    }

    public Res<Account> rename(UUID player, String rawName) {
        Account a = account(player);
        if (a == null) return Res.fail("Crie uma conta primeiro.");
        Res<String> n = Text.displayName(rawName);
        if (!n.ok) return Res.fail(n.error);
        a.name = n.value;
        a.nameChangedAt = db.clock.getAsLong();
        db.markWa();
        return Res.ok(a);
    }

    public Res<Account> setPrivacy(UUID player, boolean allowStrangers, boolean searchable) {
        Account a = account(player);
        if (a == null) return Res.fail("Crie uma conta primeiro.");
        a.allowStrangers = allowStrangers;
        a.searchable = searchable;
        db.markWa();
        return Res.ok(a);
    }

    // ---------------- contatos ----------------
    public Set<UUID> contacts(UUID player) { return db.waContacts.getOrDefault(player, Collections.emptySet()); }

    public Res<List<Account>> search(UUID player, String query) {
        if (account(player) == null) return Res.fail("Crie uma conta primeiro.");
        String q = Text.clean(query, false).toLowerCase(Locale.ROOT);
        if (q.length() < 2) return Res.fail("Digite ao menos 2 caracteres.");
        List<Account> out = new ArrayList<>();
        for (Account a : db.waAccounts.values()) {
            if (a.owner.equals(player)) continue;
            boolean exact = a.handle.toLowerCase(Locale.ROOT).equals(q) || a.handle.replace("-", "").equals(q.replace("-", ""));
            boolean byName = a.searchable && a.name.toLowerCase(Locale.ROOT).contains(q);
            if (exact || byName) out.add(a);
            if (out.size() >= 20) break;
        }
        return Res.ok(out);
    }

    public Res<Account> addContact(UUID player, String number) {
        if (account(player) == null) return Res.fail("Crie uma conta primeiro.");
        Account target = byNumber(number);
        if (target == null) return Res.fail("Essa conta não existe.");
        if (target.owner.equals(player)) return Res.fail("Você não pode adicionar a si mesmo.");
        Set<UUID> set = db.waContacts.computeIfAbsent(player, k -> new LinkedHashSet<>());
        if (set.contains(target.owner)) return Res.fail("Já está nos seus contatos.");
        if (set.size() >= MAX_CONTACTS) return Res.fail("Limite de contatos atingido.");
        set.add(target.owner);
        db.markWa();
        return Res.ok(target);
    }

    public Res<Boolean> removeContact(UUID player, String number) {
        Account target = byNumber(number);
        if (target == null) return Res.fail("Essa conta não existe.");
        Set<UUID> set = db.waContacts.get(player);
        if (set == null || !set.remove(target.owner)) return Res.fail("Essa pessoa não está nos seus contatos.");
        db.markWa();
        return Res.ok(true);
    }

    // ---------------- mensagens ----------------
    public Res<Msg> send(UUID from, String toNumber, String rawText) {
        return send(from, toNumber, rawText, "text");
    }

    /** {@code kind} "share" só é usado internamente (compartilhamento de posts); o cliente nunca escolhe. */
    Res<Msg> send(UUID from, String toNumber, String rawText, String kind) {
        Account sender = account(from);
        if (sender == null) return Res.fail("Crie uma conta primeiro.");
        Account rcpt = byNumber(toNumber);
        if (rcpt == null) return Res.fail("Destinatário inexistente.");
        if (rcpt.owner.equals(from)) return Res.fail("Você não pode enviar mensagem para si mesmo.");
        Res<String> t = Text.message(rawText);
        if (!t.ok) return Res.fail(t.error);
        if (!rcpt.allowStrangers && !contacts(rcpt.owner).contains(from))
            return Res.fail("Este usuário só recebe mensagens de contatos.");
        Msg m = new Msg();
        m.id = db.nextMsgId++;
        m.from = from;
        m.to = rcpt.owner;
        m.text = t.value;
        m.time = db.clock.getAsLong();
        m.kind = kind;
        db.messages.add(m);
        trim(from, rcpt.owner);
        db.markMsgs();
        return Res.ok(m);
    }

    private void trim(UUID a, UUID b) {
        int count = 0;
        for (Msg x : db.messages) if (between(x, a, b)) count++;
        if (count <= MAX_STORED_PER_CONVERSATION) return;
        int drop = count - MAX_STORED_PER_CONVERSATION;
        for (Iterator<Msg> it = db.messages.iterator(); it.hasNext() && drop > 0; ) {
            if (between(it.next(), a, b)) { it.remove(); drop--; }
        }
    }

    private static boolean between(Msg m, UUID a, UUID b) {
        return (m.from.equals(a) && m.to.equals(b)) || (m.from.equals(b) && m.to.equals(a));
    }

    /** Últimas mensagens da conversa (ordem cronológica). */
    public Res<List<Msg>> history(UUID player, String otherNumber) {
        if (account(player) == null) return Res.fail("Crie uma conta primeiro.");
        Account other = byNumber(otherNumber);
        if (other == null) return Res.fail("Conversa inexistente.");
        List<Msg> out = new ArrayList<>();
        for (Msg m : db.messages) if (between(m, player, other.owner)) out.add(m);
        if (out.size() > HISTORY_LIMIT) out = new ArrayList<>(out.subList(out.size() - HISTORY_LIMIT, out.size()));
        return Res.ok(out);
    }

    /** Marca como lidas as mensagens recebidas de {@code otherNumber}. */
    public Res<Integer> markRead(UUID player, String otherNumber) {
        Account other = byNumber(otherNumber);
        if (other == null) return Res.fail("Conversa inexistente.");
        int n = 0;
        for (Msg m : db.messages) if (m.to.equals(player) && m.from.equals(other.owner) && !m.read) { m.read = true; n++; }
        if (n > 0) db.markMsgs();
        return Res.ok(n);
    }

    public int totalUnread(UUID player) {
        int n = 0;
        for (Msg m : db.messages) if (m.to.equals(player) && !m.read) n++;
        return n;
    }

    /** Lista de conversas: contatos + quem já trocou mensagens, ordenada pela atividade mais recente. */
    public List<Map<String, Object>> conversations(UUID player) {
        Map<UUID, long[]> stats = new LinkedHashMap<>();   // [lastTime, unread]
        Map<UUID, String> last = new HashMap<>();
        for (Msg m : db.messages) {
            UUID other;
            if (m.from.equals(player)) other = m.to; else if (m.to.equals(player)) other = m.from; else continue;
            long[] s = stats.computeIfAbsent(other, k -> new long[2]);
            s[0] = Math.max(s[0], m.time);
            if (m.to.equals(player) && !m.read) s[1]++;
            last.put(other, (m.from.equals(player) ? "Você: " : "") + m.text);
        }
        Set<UUID> contacts = contacts(player);
        for (UUID c : contacts) stats.computeIfAbsent(c, k -> new long[2]);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<UUID, long[]> e : stats.entrySet()) {
            Account a = db.waAccounts.get(e.getKey());
            if (a == null) continue;
            out.add(Json.obj("handle", a.handle, "name", a.name, "contact", contacts.contains(e.getKey()),
                    "lastTime", e.getValue()[0], "unread", e.getValue()[1], "last", last.getOrDefault(e.getKey(), "")));
        }
        out.sort((x, y) -> {
            int c = Long.compare(Json.num(y, "lastTime", 0), Json.num(x, "lastTime", 0));
            return c != 0 ? c : Json.str(x, "name", "").compareToIgnoreCase(Json.str(y, "name", ""));
        });
        return out;
    }

    /** Visão de mensagem para o cliente: direção relativa ao {@code viewer}. */
    public Map<String, Object> view(UUID viewer, Msg m) {
        Account other = db.waAccounts.get(m.from.equals(viewer) ? m.to : m.from);
        return Json.obj("id", m.id, "mine", m.from.equals(viewer), "text", m.text, "time", m.time, "kind", m.kind,
                "peer", other == null ? "" : other.handle);
    }
}
