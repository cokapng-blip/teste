package com.celularmod.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Configurações GLOBAIS do servidor (editáveis só por administradores). Arquivo: celular/server-settings.json */
public final class ServerSettings {
    public String discordUrl = "";
    public long maxSiteBytes = 8L * 1024 * 1024;
    public long maxSiteFileBytes = 2L * 1024 * 1024;
    public int maxSiteFiles = 200;
    /** Se false (padrão), os sites só podem carregar recursos deles mesmos (CSP default-src 'self'). */
    public boolean allowExternalResources = false;
    public int adminPermissionLevel = 3;
    private Path file;

    public static ServerSettings load(Path dir) throws IOException {
        ServerSettings s = new ServerSettings();
        s.file = dir.resolve("server-settings.json");
        if (Files.exists(s.file)) {
            Map<String, Object> m = Json.asMap(Json.parse(Files.readString(s.file, StandardCharsets.UTF_8)));
            s.discordUrl = Json.str(m, "discordUrl", "");
            s.maxSiteBytes = Json.num(m, "maxSiteBytes", s.maxSiteBytes);
            s.maxSiteFileBytes = Json.num(m, "maxSiteFileBytes", s.maxSiteFileBytes);
            s.maxSiteFiles = (int) Json.num(m, "maxSiteFiles", s.maxSiteFiles);
            s.allowExternalResources = Json.bool(m, "allowExternalResources", false);
            s.adminPermissionLevel = (int) Json.num(m, "adminPermissionLevel", 3);
        }
        Files.createDirectories(dir);
        s.save();   // grava o arquivo com os padrões para o administrador editar
        return s;
    }

    public void save() throws IOException {
        Files.writeString(file, Json.stringify(Json.obj(
                "discordUrl", discordUrl, "maxSiteBytes", maxSiteBytes, "maxSiteFileBytes", maxSiteFileBytes,
                "maxSiteFiles", maxSiteFiles, "allowExternalResources", allowExternalResources,
                "adminPermissionLevel", adminPermissionLevel)), StandardCharsets.UTF_8);
    }
}
