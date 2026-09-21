package com.slest.osrsdata.pipeline;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.cert.Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginHub
{
    public static final String BOOTSTRAP_URL = "https://static.runelite.net/bootstrap.json";
    public static final String MANIFEST_URL = "https://repo.runelite.net/plugins/manifest/%s_full.js";
    public static final String JAR_URL = "https://repo.runelite.net/plugins/jar/%s_%s.jar";
    public static final String QUEST_HELPER = "quest-helper";

    private static final Pattern CLIENT_ARTIFACT = Pattern.compile("client-(.+)\\.jar");

    private final Http http;
    private final Certificate certificate;

    public PluginHub(Http http)
    {
        this(http, PluginManifest.runeliteCertificate());
    }

    public PluginHub(Http http, Certificate certificate)
    {
        this.http = http;
        this.certificate = certificate;
    }

    public String runeliteVersion() throws IOException
    {
        JsonObject bootstrap = Json.object(new String(http.get(BOOTSTRAP_URL), StandardCharsets.UTF_8),
                "RuneLite bootstrap");
        JsonElement artifacts = bootstrap.get("artifacts");
        if (artifacts != null && artifacts.isJsonArray())
        {
            for (JsonElement artifact : artifacts.getAsJsonArray())
            {
                String name = artifact.isJsonObject() ? Json.string(artifact.getAsJsonObject(), "name") : null;
                Matcher matcher = name == null ? null : CLIENT_ARTIFACT.matcher(name);
                if (matcher != null && matcher.matches())
                {
                    return matcher.group(1);
                }
            }
        }
        throw new PipelineException("RuneLite bootstrap lists no client-<version>.jar artifact");
    }

    public QuestHelperJar resolve() throws IOException
    {
        String runelite = runeliteVersion();
        PluginManifest manifest = PluginManifest.parse(http.get(String.format(MANIFEST_URL, runelite)), certificate);
        PluginManifest.PluginRelease release = manifest.release(QUEST_HELPER);
        return new QuestHelperJar(runelite, release.version(), release.jarHash(), release.jarSize());
    }

    public QuestHelperJar fetch(Path outDir) throws IOException
    {
        QuestHelperJar jar = resolve();
        String url = String.format(JAR_URL, QUEST_HELPER, jar.jarHash());
        byte[] bytes = http.get(url);
        if (bytes.length != jar.jarSize())
        {
            throw new PipelineException("jar size mismatch: " + url + " is " + bytes.length
                    + " bytes but the manifest says jarSize " + jar.jarSize());
        }
        String actual = Hashes.jarHash(bytes);
        if (!actual.equals(jar.jarHash()))
        {
            throw new PipelineException("jar hash mismatch: " + url + " hashes to " + actual
                    + " but the manifest says jarHash " + jar.jarHash());
        }

        Files.createDirectories(outDir);
        Path partial = outDir.resolve(QuestHelperJar.JAR_FILE + ".part");
        Files.write(partial, bytes);
        Files.move(partial, outDir.resolve(QuestHelperJar.JAR_FILE), StandardCopyOption.REPLACE_EXISTING);
        jar.write(outDir);
        return jar;
    }
}
