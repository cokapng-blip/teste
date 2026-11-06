package com.celularmod.server;

import com.celularmod.core.*;
import com.celularmod.net.S2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Estado do mod no servidor (um por mundo). Todo o acesso acontece na thread principal do servidor. */
public final class ServerState {
    public static final Logger LOG = LoggerFactory.getLogger("Celular");
    private static ServerState instance;

    public static ServerState get() { return instance; }

    public final MinecraftServer server;
    public final Database db;
    public final WhatsAppService wa;
    public final ThreadsService th;
    public final SiteService sites;
    public final ServerSettings settings;

    public final RateLimiter anyLimiter, msgLimiter, postLimiter, likeLimiter, searchLimiter, siteLimiter;
    /** Jogadores que enviaram "hello" (têm o mod instalado) — só para eles enviamos pacotes. */
    public final Set<UUID> modded = new HashSet<>();
    private final Map<UUID, ArrayDeque<S2CPacket>> outbox = new HashMap<>();
    private int ticks;

    private ServerState(MinecraftServer server) throws IOException {
        this.server = server;
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("celular");
        java.util.function.LongSupplier clock = System::currentTimeMillis;
        this.settings = ServerSettings.load(dir);
        this.db = new Database(dir.resolve("data"), clock);
        this.db.load();
        this.wa = new WhatsAppService(db);
        this.th = new ThreadsService(db, wa);
        this.sites = new SiteService(dir, settings, clock);
        anyLimiter = new RateLimiter(60, 3000, clock);
        msgLimiter = new RateLimiter(8, 5000, clock);
        postLimiter = new RateLimiter(4, 30_000, clock);
        likeLimiter = new RateLimiter(20, 10_000, clock);
        searchLimiter = new RateLimiter(10, 10_000, clock);
        siteLimiter = new RateLimiter(6, 30_000, clock);
    }

    public static void start(MinecraftServer server) {
        try {
            instance = new ServerState(server);
            LOG.info("Celular: dados em {}", instance.db.dir);
        } catch (IOException | RuntimeException e) {
            // Falhar alto: melhor não iniciar o mod do que sobrescrever dados corrompidos.
            instance = null;
            LOG.error("Celular: não foi possível carregar os dados — o mod ficará DESATIVADO neste mundo.", e);
        }
    }

    public static void stop() {
        if (instance != null) {
            instance.db.flushIfDirty();
            instance = null;
        }
    }

    public void tick() {
        ticks++;
        // Envio de arquivos de sites: no máximo 2 pedaços por jogador por tick (não inunda a conexão)
        for (Iterator<Map.Entry<UUID, ArrayDeque<S2CPacket>>> it = outbox.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ArrayDeque<S2CPacket>> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) { it.remove(); continue; }
            for (int i = 0; i < 2 && !e.getValue().isEmpty(); i++) PacketDistributor.sendToPlayer(p, e.getValue().poll());
            if (e.getValue().isEmpty()) it.remove();
        }
        if (ticks % 200 == 0) db.flushIfDirty();
    }

    public void forget(UUID id) {
        modded.remove(id);
        outbox.remove(id);
        for (RateLimiter r : new RateLimiter[]{anyLimiter, msgLimiter, postLimiter, likeLimiter, searchLimiter, siteLimiter}) r.forget(id);
    }

    public boolean isAdmin(ServerPlayer p) { return p.hasPermissions(settings.adminPermissionLevel); }

    // ------------------------------------------------------------------ envio
    public void send(ServerPlayer p, Map<String, Object> msg) {
        if (!modded.contains(p.getUUID())) return;
        String json = Json.stringify(msg);
        if (json.length() > S2CPacket.MAX_JSON_CHARS) {
            LOG.warn("Mensagem {} grande demais ({} chars) descartada", msg.get("t"), json.length());
            return;
        }
        PacketDistributor.sendToPlayer(p, new S2CPacket(json));
    }

    public void queue(ServerPlayer p, Map<String, Object> header, byte[] bin) {
        if (!modded.contains(p.getUUID())) return;
        outbox.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>())
                .add(new S2CPacket(Json.stringify(header), bin));
    }

    public ServerPlayer online(UUID id) { return server.getPlayerList().getPlayer(id); }

    public void broadcastSiteIndex() {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendSiteIndex(p);
    }

    public void sendSiteIndex(ServerPlayer p) {
        List<Object> l = new ArrayList<>();
        for (SiteService.Site s : sites.list()) {
            l.add(Json.obj("id", s.id, "title", s.title, "description", s.description, "hash", s.hash, "size", s.size));
        }
        send(p, Json.obj("t", "site.index", "sites", l, "pending", isAdmin(p) ? sites.pending().size() : 0));
    }

    /** Enfileira o manifesto + arquivos de um site para o jogador (download em pedaços). */
    public void queueSite(ServerPlayer p, String id) throws IOException {
        SiteService.Site s = sites.get(id);
        if (s == null) return;
        List<SiteService.FileInfo> files = sites.manifest(id);
        List<Object> fl = new ArrayList<>();
        for (SiteService.FileInfo f : files) fl.add(Json.obj("p", f.path, "s", f.size, "h", f.sha256));
        queue(p, Json.obj("t", "site.manifest", "id", s.id, "title", s.title, "description", s.description,
                "hash", s.hash, "files", fl), new byte[0]);
        for (SiteService.FileInfo f : files) {
            byte[] data = sites.readFile(id, f.path);
            if (data == null) continue;
            for (int off = 0; off < data.length; off += S2CPacket.MAX_BIN) {
                int end = Math.min(data.length, off + S2CPacket.MAX_BIN);
                queue(p, Json.obj("t", "site.chunk", "id", id, "p", f.path, "o", off),
                        Arrays.copyOfRange(data, off, end));
            }
        }
    }

    public static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }
}
