package com.slest.questdata;

import net.runelite.api.QuestState;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class QuestStatesParity
{
    private static final int[] SCRIPT_RESULTS = {0, 1, 2, 3};
    private static final int[] SKILL_LEVELS = {1, 50, 99};

    private static int scriptResult;
    private static int varValue;
    private static int skillLevel;

    public static void main(String[] args) throws Exception
    {
        Path jar = args.length > 0 ? Paths.get(args[0]) : newestHubJar();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()},
                QuestStatesParity.class.getClassLoader()))
        {
            QhMirror mirror = QhMirror.using(loader);
            QuestDataExporter exporter = new QuestDataExporter(mirror).withStubClient();
            Object client = fakeClient();
            Method getState = mirror.cls("questinfo.QuestHelperQuest")
                    .getMethod("getState", Class.forName("net.runelite.api.Client"));

            QuestStates.GameReads reads = new QuestStates.GameReads()
            {
                public int questScriptState(int questId) { return scriptResult; }
                public int varbit(int id) { return varValue; }
                public int varp(int id) { return varValue; }
                public int realSkillLevel(String skill) { return skillLevel; }
            };

            int quests = 0;
            int checks = 0;
            List<String> disagreements = new ArrayList<>();
            for (Object quest : exporter.quests())
            {
                QuestData data = exporter.exportQuest(quest);
                quests++;
                int complete = data.completeValue == null ? 5 : data.completeValue;
                int[] vars = {0, 1, complete - 1, complete, complete + 1, 999};
                for (int s : SCRIPT_RESULTS)
                {
                    for (int v : vars)
                    {
                        for (int l : SKILL_LEVELS)
                        {
                            scriptResult = s;
                            varValue = v;
                            skillLevel = l;
                            QuestState expected = (QuestState) getState.invoke(quest, client);
                            QuestState actual = QuestStates.stateOf(data, reads);
                            checks++;
                            if (expected != actual)
                            {
                                disagreements.add(data.questKey + " script=" + s + " var=" + v
                                        + " level=" + l + ": QuestHelper=" + expected
                                        + " ours=" + actual);
                            }
                        }
                    }
                }
            }
            System.out.println(quests + " quests, " + checks + " checks, "
                    + disagreements.size() + " disagreements");
            disagreements.stream().limit(20).forEach(d -> System.out.println("  " + d));
            if (!disagreements.isEmpty())
            {
                System.exit(1);
            }
        }
    }

    // runScript is a no-op: Quest Helper reads the script result straight off getIntStack()
    private static Object fakeClient() throws ClassNotFoundException
    {
        Class<?> type = Class.forName("net.runelite.api.Client");
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            switch (m.getName())
            {
                case "getIntStack": return new int[]{scriptResult};
                case "getVarbitValue":
                case "getVarpValue": return varValue;
                case "getRealSkillLevel":
                case "getBoostedSkillLevel": return skillLevel;
                default: break;
            }
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == long.class) return 0L;
            if (r == int[].class) return new int[0];
            return null;
        });
    }

    private static Path newestHubJar() throws Exception
    {
        Path dir = Paths.get(System.getProperty("user.home"), ".runelite", "plugins");
        try (Stream<Path> s = Files.list(dir))
        {
            return s.filter(p -> p.getFileName().toString().startsWith("quest-helper"))
                    .max((a, b) -> Long.compare(a.toFile().lastModified(), b.toFile().lastModified()))
                    .orElseThrow();
        }
    }

    private QuestStatesParity()
    {
    }
}
