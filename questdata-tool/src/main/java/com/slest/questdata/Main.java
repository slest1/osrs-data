package com.slest.questdata;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Main
{
    private static final String PLUGIN_DIR = ".runelite/plugins";

    public static void main(String[] args) throws Exception
    {
        Args parsed;
        try
        {
            parsed = Args.parse(args);
        }
        catch (IllegalArgumentException e)
        {
            System.err.println("questdata: " + e.getMessage());
            usage(System.err);
            System.exit(2);
            return;
        }
        if (parsed.help)
        {
            usage(System.out);
            return;
        }

        Path jar = parsed.jar != null ? parsed.jar : findQuestHelperJar();
        if (jar == null || !Files.isRegularFile(jar))
        {
            System.err.println("questdata: no quest-helper jar found"
                    + (jar == null ? " under ~/" + PLUGIN_DIR + "; pass --jar <path>" : ": " + jar));
            System.exit(2);
            return;
        }
        System.out.println("quest-helper: " + jar);

        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()},
                Main.class.getClassLoader()))
        {
            QhMirror mirror = QhMirror.using(loader);
            if (mirror == null || !mirror.isAvailable())
            {
                System.err.println("questdata: no Quest Helper classes in that jar");
                System.exit(2);
                return;
            }
            System.out.println("package root: " + mirror.getRoot());

            QuestDataExporter exporter = new QuestDataExporter(mirror);
            if (parsed.stubClient)
            {
                exporter.withStubClient(parsed.skillLevel, parsed.accountType);
                System.out.println("stub client:  on (skill level " + parsed.skillLevel
                        + ", " + parsed.accountType + ")");
            }
            QuestDataExporter.ExportResult result;

            if (parsed.quest != null)
            {
                result = exportOne(exporter, mirror, parsed.quest, parsed.out);
            }
            else
            {
                System.out.println("output:       " + parsed.out);
                result = exporter.writeAll(parsed.out);
            }

            System.out.println();
            System.out.println("exported " + result.quests.size()
                    + ", needs a live client: " + result.errors.size()
                    + (result.empty.isEmpty() ? "" : ", NO STEPS: " + result.empty.size()));
            if (!result.empty.isEmpty())
            {
                System.out.println("  exported but unwalkable: " + String.join(", ", result.empty));
            }
            if (parsed.verbose)
            {
                for (String error : result.errors)
                {
                    System.out.println("  " + error);
                }
            }
            else if (!result.errors.isEmpty())
            {
                System.out.println("  (--verbose to list them)");
            }

            if (parsed.strict && (!result.errors.isEmpty() || !result.empty.isEmpty()))
            {
                System.exit(1);
            }
            if (result.quests.isEmpty())
            {
                System.err.println("questdata: nothing exported");
                System.exit(1);
            }
        }
    }

    private static QuestDataExporter.ExportResult exportOne(QuestDataExporter exporter,
                                                            QhMirror mirror,
                                                            String key,
                                                            Path out) throws IOException
    {
        QuestDataExporter.ExportResult result = new QuestDataExporter.ExportResult();
        for (Object quest : exporter.quests())
        {
            if (!(quest instanceof Enum<?>) || !((Enum<?>) quest).name().equalsIgnoreCase(key))
            {
                continue;
            }
            try
            {
                QuestData data = exporter.exportQuest(quest);
                exporter.writeQuest(out, data);
                result.quests.add(data);
                System.out.println("wrote " + out.resolve(data.questKey.toLowerCase(Locale.ROOT) + ".json"));
            }
            catch (Throwable t)
            {
                result.errors.add(((Enum<?>) quest).name() + ": " + t.getMessage());
            }
            return result;
        }
        throw new IllegalArgumentException("no quest named '" + key + "'");
    }

    private static Path findQuestHelperJar() throws IOException
    {
        Path dir = Paths.get(System.getProperty("user.home"), PLUGIN_DIR);
        if (!Files.isDirectory(dir))
        {
            return null;
        }
        List<Path> found = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir, "quest-helper*.jar"))
        {
            for (Path entry : entries)
            {
                found.add(entry);
            }
        }
        return found.stream().max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
    }

    private static void usage(java.io.PrintStream to)
    {
        to.println("usage: questdata [options]");
        to.println();
        to.println("  --jar <path>     quest-helper jar to export from");
        to.println("                   (default: newest ~/" + PLUGIN_DIR + "/quest-helper*.jar)");
        to.println("  --out <dir>      output directory (required)");
        to.println("  --quest <KEY>    export one quest, e.g. DORICS_QUEST");
        to.println("  --verbose        list the quests that need a live client");
        to.println("  --strict         exit 1 if any quest could not be exported");
        to.println("  --stub-client    also export quests that read the account, against a");
        to.println("                   logged-out stand-in (see osrs-data's README.md)");
        to.println("  --skill-level N  profile the stand-in reports (default 1; try 99)");
        to.println("  --account-type T NORMAL | IRONMAN | ULTIMATE_IRONMAN (default NORMAL)");
        to.println("  --help");
    }

    private static final class Args
    {
        Path jar;
        Path out;
        String quest;
        boolean verbose;
        boolean strict;
        boolean stubClient;
        int skillLevel = 1;
        String accountType = "NORMAL";
        boolean help;

        static Args parse(String[] argv)
        {
            Args args = new Args();
            for (int i = 0; i < argv.length; i++)
            {
                String arg = argv[i];
                switch (arg)
                {
                    case "--jar":
                        args.jar = Paths.get(require(argv, ++i, "--jar"));
                        break;
                    case "--out":
                        args.out = Paths.get(require(argv, ++i, "--out"));
                        break;
                    case "--quest":
                        args.quest = require(argv, ++i, "--quest");
                        break;
                    case "--verbose":
                        args.verbose = true;
                        break;
                    case "--strict":
                        args.strict = true;
                        break;
                    case "--stub-client":
                        args.stubClient = true;
                        break;
                    case "--skill-level":
                        args.stubClient = true;
                        args.skillLevel = Integer.parseInt(require(argv, ++i, "--skill-level"));
                        break;
                    case "--account-type":
                        args.stubClient = true;
                        args.accountType = require(argv, ++i, "--account-type").toUpperCase(Locale.ROOT);
                        break;
                    case "--help":
                    case "-h":
                        args.help = true;
                        break;
                    default:
                        throw new IllegalArgumentException("unknown option: " + arg);
                }
            }
            if (!args.help && args.out == null)
            {
                throw new IllegalArgumentException("--out <dir> is required");
            }
            return args;
        }

        private static String require(String[] argv, int index, String option)
        {
            if (index >= argv.length)
            {
                throw new IllegalArgumentException(option + " needs a value");
            }
            return argv[index];
        }
    }

    private Main()
    {
    }
}
