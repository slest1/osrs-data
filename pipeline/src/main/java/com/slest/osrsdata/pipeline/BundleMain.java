package com.slest.osrsdata.pipeline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BundleMain
{
    public static void main(String[] args)
    {
        List<Bundler.Input> inputs = new ArrayList<>();
        Path questHelper = null;
        Path out = null;
        Path upstream = Paths.get("UPSTREAM");
        Path previous = null;
        int skillLevel = 1;
        try
        {
            for (int i = 0; i < args.length; i++)
            {
                String arg = args[i];
                switch (arg)
                {
                    case "--export":
                        String spec = value(args, ++i, arg);
                        int eq = spec.indexOf('=');
                        if (eq <= 0 || eq == spec.length() - 1)
                        {
                            throw new IllegalArgumentException("--export wants ACCOUNT_TYPE=<dir>, got " + spec);
                        }
                        inputs.add(new Bundler.Input(spec.substring(0, eq), Paths.get(spec.substring(eq + 1))));
                        break;
                    case "--quest-helper":
                        questHelper = Paths.get(value(args, ++i, arg));
                        break;
                    case "--out":
                        out = Paths.get(value(args, ++i, arg));
                        break;
                    case "--upstream":
                        upstream = Paths.get(value(args, ++i, arg));
                        break;
                    case "--previous-manifest":
                        previous = Paths.get(value(args, ++i, arg));
                        break;
                    case "--skill-level":
                        skillLevel = Integer.parseInt(value(args, ++i, arg));
                        break;
                    default:
                        throw new IllegalArgumentException("unknown option: " + arg);
                }
            }
            if (inputs.isEmpty() || questHelper == null || out == null)
            {
                throw new IllegalArgumentException("--export, --quest-helper and --out are required");
            }
        }
        catch (IllegalArgumentException e)
        {
            System.err.println("bundle: " + e.getMessage());
            System.err.println("usage: bundle --export NORMAL=<dir> [--export IRONMAN=<dir>] "
                    + "--quest-helper <quest-helper.json> --out <dir> [--upstream <file>] [--skill-level N] "
                    + "[--previous-manifest <manifest.json>]");
            System.exit(2);
            return;
        }

        try
        {
            if (!Files.isRegularFile(questHelper))
            {
                throw new PipelineException(questHelper + " does not exist; run fetch first");
            }
            if (!Files.isRegularFile(upstream))
            {
                throw new PipelineException("missing source metadata: " + upstream + " (upstreamCommit) does not exist");
            }
            String upstreamCommit = Files.readString(upstream, StandardCharsets.UTF_8).trim();
            Set<String> allowlist = Bundler.defaultAllowlist();
            List<Bundler.FileEntry> files = Bundler.bundle(new Bundler.Request(inputs,
                    QuestHelperJar.read(questHelper), upstreamCommit, skillLevel, previous, allowlist), out);

            System.out.println("allowlist (no steps or panels): " + (allowlist.isEmpty() ? "empty" : allowlist));
            for (Bundler.FileEntry file : files)
            {
                System.out.printf("%-23s %-8s %3d entries  gz %7d bytes sha256 %s%n", file.name(),
                        file.accountType(), file.entries(), file.bytes(), file.sha256());
                System.out.printf("%-23s %-8s %3s          json %8d bytes sha256 %s%n", "", "", "",
                        file.uncompressedBytes(), file.uncompressedSha256());
            }
            System.out.println("wrote " + out.resolve(Bundler.MANIFEST_FILE));
        }
        catch (Exception e)
        {
            System.err.println("bundle: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String value(String[] args, int index, String option)
    {
        if (index >= args.length)
        {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private BundleMain()
    {
    }
}
