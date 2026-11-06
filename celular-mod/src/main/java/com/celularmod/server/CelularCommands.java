package com.celularmod.server;

import com.celularmod.core.*;
import com.celularmod.core.Models.Post;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;

/**
 * /celular site request <id> <título>      (qualquer jogador)  pede a aprovação de um site
 * /celular site list                         (qualquer jogador)  sites aprovados
 * /celular site pending|review|approve|reject|remove|reload   (administradores)
 * /celular admin discord|external|posts|post-delete           (administradores)
 */
public final class CelularCommands {
    private CelularCommands() {}

    private static boolean admin(CommandSourceStack src) {
        ServerState s = ServerState.get();
        return src.hasPermission(s == null ? 3 : s.settings.adminPermissionLevel);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("celular");

        root.then(Commands.literal("site")
                .then(Commands.literal("request")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("titulo", StringArgumentType.greedyString())
                                        .executes(c -> request(c, StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "titulo"))))))
                .then(Commands.literal("list").executes(CelularCommands::list))
                .then(Commands.literal("pending").requires(CelularCommands::admin).executes(CelularCommands::pending))
                .then(Commands.literal("review").requires(CelularCommands::admin)
                        .then(Commands.argument("id", StringArgumentType.word()).executes(c -> review(c, StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("approve").requires(CelularCommands::admin)
                        .then(Commands.argument("id", StringArgumentType.word()).executes(c -> approve(c, StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("reject").requires(CelularCommands::admin)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(c -> reject(c, StringArgumentType.getString(c, "id"), ""))
                                .then(Commands.argument("motivo", StringArgumentType.greedyString())
                                        .executes(c -> reject(c, StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "motivo"))))))
                .then(Commands.literal("remove").requires(CelularCommands::admin)
                        .then(Commands.argument("id", StringArgumentType.word()).executes(c -> remove(c, StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("reload").requires(CelularCommands::admin).executes(CelularCommands::reload)));

        root.then(Commands.literal("admin").requires(CelularCommands::admin)
                .then(Commands.literal("discord")
                        .executes(c -> msg(c, "Convite atual do Discord: " + orNone(st(c).settings.discordUrl)))
                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                .executes(c -> discord(c, StringArgumentType.getString(c, "url")))))
                .then(Commands.literal("external")
                        .then(Commands.argument("permitir", BoolArgumentType.bool())
                                .executes(c -> external(c, BoolArgumentType.getBool(c, "permitir")))))
                .then(Commands.literal("posts").executes(CelularCommands::posts))
                .then(Commands.literal("post-delete")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(c -> postDelete(c, LongArgumentType.getLong(c, "id"))))));

        root.executes(c -> msg(c, "Celular — comandos: /celular site request|list" + (admin(c.getSource()) ? "|pending|review|approve|reject|remove|reload e /celular admin discord|external|posts|post-delete" : "")));
        d.register(root);
    }

    // ------------------------------------------------------------ util
    private static ServerState st(CommandContext<CommandSourceStack> c) {
        ServerState s = ServerState.get();
        if (s == null) throw new IllegalStateException("O mod Celular está desativado neste mundo (veja o log do servidor).");
        return s;
    }

    private static String orNone(String s) { return s == null || s.isEmpty() ? "(nenhum)" : s; }

    private static int msg(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int err(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendFailure(Component.literal(text));
        return 0;
    }

    private static MutableComponent button(String label, String cmd, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd)));
    }

    // ------------------------------------------------------------ jogadores
    private static int request(CommandContext<CommandSourceStack> c, String id, String title) {
        ServerState s = st(c);
        ServerPlayer p;
        try { p = c.getSource().getPlayerOrException(); } catch (Exception e) { return err(c, "Só jogadores podem solicitar sites."); }
        if (!s.siteLimiter.allow(p.getUUID(), "request")) return err(c, "Aguarde um pouco antes de pedir de novo.");
        Res<SiteService.Request> r = s.sites.request(p.getUUID(), p.getGameProfile().getName(), id, title);
        if (!r.ok) return err(c, r.error);
        msg(c, "Solicitação enviada! Entregue os arquivos do site (pasta com index.html ou um .zip) a um administrador do servidor. "
                + "Ele analisará e, se aprovar, o site aparecerá no app Google do celular.");
        for (ServerPlayer o : s.server.getPlayerList().getPlayers()) {
            if (!s.isAdmin(o)) continue;
            o.sendSystemMessage(Component.literal("[Celular] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(p.getGameProfile().getName() + " pediu a aprovação do site '" + id + "' (" + r.value.title + "). ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(button("Analisar", "/celular site review " + id, ChatFormatting.YELLOW)));
            s.sendSiteIndex(o);
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<SiteService.Site> l = st(c).sites.list();
        if (l.isEmpty()) return msg(c, "Nenhum site aprovado ainda.");
        StringBuilder sb = new StringBuilder("Sites aprovados:");
        for (SiteService.Site x : l) sb.append("\n - ").append(x.id).append(" — ").append(x.title);
        return msg(c, sb.toString());
    }

    // ------------------------------------------------------------ administradores
    private static int pending(CommandContext<CommandSourceStack> c) {
        ServerState s = st(c);
        List<SiteService.Request> l = s.sites.pending();
        if (l.isEmpty()) return msg(c, "Nenhuma solicitação pendente. Pasta de envios: " + s.sites.submissionsDir());
        for (SiteService.Request r : l) {
            c.getSource().sendSuccess(() -> Component.literal(" - " + r.id + " (" + r.title + ") de " + r.requesterName + "  ")
                    .append(button("Analisar", "/celular site review " + r.id, ChatFormatting.YELLOW)), false);
        }
        return l.size();
    }

    private static int review(CommandContext<CommandSourceStack> c, String id) {
        ServerState s = st(c);
        Res<SiteService.Review> r = s.sites.review(id);
        if (!r.ok) return err(c, r.error);
        SiteService.Review rv = r.value;
        StringBuilder sb = new StringBuilder("Análise de '" + id + "': " + rv.files.size() + " arquivos, " + rv.totalSize + " bytes, título \"" + rv.title + "\"");
        for (String e : rv.errors) sb.append("\n ERRO: ").append(e);
        for (String w : rv.warnings) sb.append("\n AVISO: ").append(w);
        sb.append("\n Revise o conteúdo em ").append(s.sites.submissionsDir().resolve(id)).append(" antes de aprovar.");
        msg(c, sb.toString());
        if (rv.ok()) {
            c.getSource().sendSuccess(() -> button("Aprovar", "/celular site approve " + id, ChatFormatting.GREEN)
                    .append(Component.literal(" ")).append(button("Rejeitar", "/celular site reject " + id + " ", ChatFormatting.RED)), false);
        }
        return rv.ok() ? 1 : 0;
    }

    private static int approve(CommandContext<CommandSourceStack> c, String id) {
        ServerState s = st(c);
        Res<SiteService.Site> r = s.sites.approve(id, c.getSource().getTextName());
        if (!r.ok) return err(c, r.error);
        msg(c, "Site '" + id + "' aprovado e publicado (" + r.value.files + " arquivos).");
        s.broadcastSiteIndex();
        return 1;
    }

    private static int reject(CommandContext<CommandSourceStack> c, String id, String reason) {
        ServerState s = st(c);
        Res<SiteService.Request> r = s.sites.reject(id, reason);
        if (!r.ok) return err(c, r.error);
        msg(c, "Site '" + id + "' rejeitado e arquivado.");
        if (r.value != null) {
            ServerPlayer p = s.online(r.value.requester);
            if (p != null) p.sendSystemMessage(Component.literal("[Celular] Seu site '" + id + "' foi rejeitado" + (reason.isEmpty() ? "." : ": " + reason)).withStyle(ChatFormatting.RED));
        }
        s.broadcastSiteIndex();
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c, String id) {
        ServerState s = st(c);
        Res<SiteService.Site> r = s.sites.remove(id);
        if (!r.ok) return err(c, r.error);
        s.broadcastSiteIndex();
        return msg(c, "Site '" + id + "' removido. Os clientes apagarão a cópia local.");
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        ServerState s = st(c);
        try { s.sites.reload(); } catch (IOException e) { return err(c, "Erro: " + e.getMessage()); }
        s.broadcastSiteIndex();
        return msg(c, "Sites recarregados do disco.");
    }

    private static int discord(CommandContext<CommandSourceStack> c, String url) {
        ServerState s = st(c);
        Res<String> r = Text.discordUrl(url.equals("clear") ? "" : url);
        if (!r.ok) return err(c, r.error);
        s.settings.discordUrl = r.value;
        try { s.settings.save(); } catch (IOException e) { return err(c, "Erro ao salvar: " + e.getMessage()); }
        for (ServerPlayer p : s.server.getPlayerList().getPlayers())
            s.send(p, Json.obj("t", "hello", "discord", s.settings.discordUrl, "allowExternal", s.settings.allowExternalResources,
                    "admin", s.isAdmin(p), "server", s.server.getMotd(), "proto", 1L));
        return msg(c, "Convite do Discord: " + orNone(r.value));
    }

    private static int external(CommandContext<CommandSourceStack> c, boolean allow) {
        ServerState s = st(c);
        s.settings.allowExternalResources = allow;
        try { s.settings.save(); } catch (IOException e) { return err(c, "Erro ao salvar: " + e.getMessage()); }
        for (ServerPlayer p : s.server.getPlayerList().getPlayers())
            s.send(p, Json.obj("t", "hello", "discord", s.settings.discordUrl, "allowExternalResources", allow, "allowExternal", allow,
                    "admin", s.isAdmin(p), "server", s.server.getMotd(), "proto", 1L));
        return msg(c, "Recursos externos nos sites: " + (allow ? "PERMITIDOS (menos seguro)" : "bloqueados (recomendado)"));
    }

    private static int posts(CommandContext<CommandSourceStack> c) {
        ServerState s = st(c);
        StringBuilder sb = new StringBuilder("Últimas publicações do Threads:");
        int n = 0;
        for (int i = s.db.posts.size() - 1; i >= 0 && n < 10; i--) {
            Post p = s.db.posts.get(i);
            if (p.deleted) continue;
            Models.Account a = s.db.thAccounts.get(p.author);
            sb.append("\n #").append(p.id).append(" @").append(a == null ? "?" : a.handle).append(": ").append(p.text);
            n++;
        }
        return msg(c, sb.toString());
    }

    private static int postDelete(CommandContext<CommandSourceStack> c, long id) {
        ServerState s = st(c);
        Res<Post> r = s.th.delete(null, id, true);
        if (!r.ok) return err(c, r.error);
        for (ServerPlayer o : s.server.getPlayerList().getPlayers()) s.send(o, Json.obj("t", "th.gone", "id", id));
        return msg(c, "Publicação #" + id + " removida.");
    }
}
