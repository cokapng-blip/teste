package com.celularmod.core;

import com.celularmod.browser.LocalSiteServer;
import com.celularmod.browser.SiteCache;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Sobe o MESMO pipeline usado no jogo (aprovação -> manifesto -> cache do cliente -> servidor local)
 * com os sites de exemplo, para testar num Chromium real (tools/test-browser.py).
 * Uso: BrowserDemo <pasta-sample-sites> <allowExternal:true|false>
 */
public class BrowserDemo {
    public static void main(String[] a) throws Exception {
        Path samples = Path.of(a[0]);
        boolean ext = a.length > 1 && a[1].equals("true");
        Path tmp = Files.createTempDirectory("celular-demo");
        ServerSettings st = ServerSettings.load(tmp);
        SiteService ss = new SiteService(tmp, st, System::currentTimeMillis);
        try (Stream<Path> s = Files.list(samples)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String id = p.getFileName().toString();
                Path dest = ss.submissionsDir().resolve(id);
                try (Stream<Path> w = Files.walk(p)) {
                    for (Path f : (Iterable<Path>) w::iterator) {
                        Path t = dest.resolve(p.relativize(f).toString());
                        if (Files.isDirectory(f)) Files.createDirectories(t); else Files.copy(f, t);
                    }
                }
                Res<SiteService.Site> r = ss.approve(id, "demo");
                if (!r.ok) throw new IllegalStateException(id + ": " + r.error);
            }
        }
        SiteCache cache = new SiteCache(tmp.resolve("cache"));
        for (SiteService.Site site : ss.list()) {
            List<SiteCache.Entry> es = new ArrayList<>();
            for (SiteService.FileInfo f : ss.manifest(site.id)) es.add(new SiteCache.Entry(f.path, f.size, f.sha256));
            if (cache.begin(site.id, site.title, site.description, site.hash, es) != null) throw new IllegalStateException();
            for (SiteCache.Entry e : es) cache.chunk(site.id, e.path, 0, ss.readFile(site.id, e.path));
        }
        LocalSiteServer srv = new LocalSiteServer(cache, ext);
        srv.setNavigationListener((id, p) -> System.out.println("NAV " + id + p));
        System.out.println("PORT " + srv.port());
        System.out.flush();
        Thread.sleep(120_000);
    }
}
