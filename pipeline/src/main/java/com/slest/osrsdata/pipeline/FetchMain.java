package com.slest.osrsdata.pipeline;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class FetchMain
{
    public static void main(String[] args)
    {
        Path out = null;
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].equals("--out") && i + 1 < args.length)
            {
                out = Paths.get(args[++i]);
            }
            else
            {
                usage("unknown or incomplete option: " + args[i]);
                return;
            }
        }
        if (out == null)
        {
            usage("--out is required");
            return;
        }

        try
        {
            QuestHelperJar jar = new PluginHub(new JdkHttp()).fetch(out);
            System.out.println("signature:    manifest verified against RuneLite's plugin-hub certificate "
                    + PluginManifest.fingerprint(PluginManifest.runeliteCertificate()));
            System.out.println("runelite:     " + jar.runeliteVersion());
            System.out.println("quest-helper: " + jar.questHelperVersion());
            System.out.println("jarHash:      " + jar.jarHash());
            System.out.println("jarSize:      " + jar.jarSize());
            System.out.println("wrote " + out.resolve(QuestHelperJar.JAR_FILE) + " and "
                    + out.resolve(QuestHelperJar.METADATA_FILE));
        }
        catch (Exception e)
        {
            System.err.println("fetch: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void usage(String problem)
    {
        System.err.println("fetch: " + problem);
        System.err.println("usage: fetch --out <dir>");
        System.exit(2);
    }

    private FetchMain()
    {
    }
}
