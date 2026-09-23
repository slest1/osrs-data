package com.slest.osrsdata.pipeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.slest.questdata.QuestData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ExportFixtures
{
    static final Gson EXPORTER_GSON = new GsonBuilder().setPrettyPrinting().create();

    static final String UPSTREAM = "0123456789abcdef0123456789abcdef01234567";

    static QuestHelperJar questHelper()
    {
        return new QuestHelperJar("1.12.39", "4.17.1", "5BKt5NHCMQGfZxzx3NU1loph_1RujcPxn-_fL39e2vI", 3675043);
    }

    static QuestData.RequirementData requirement(String type, String text)
    {
        QuestData.RequirementData r = new QuestData.RequirementData();
        r.type = type;
        r.displayText = text;
        r.data.put("id", 434);
        r.data.put("quantity", 6);
        r.data.put("ratio", 1.5);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("skill", "MINING");
        nested.put("level", 10);
        r.data.put("nested", nested);
        r.data.put("alternates", List.of(435, 436));
        return r;
    }

    static QuestData.QuestStepData step(String id)
    {
        QuestData.QuestStepData s = new QuestData.QuestStepData();
        s.id = id;
        s.type = "NpcStep";
        s.text.add("Talk to Doric.");
        s.overlayText.add("overlay");
        s.worldPoint = new QuestData.WorldPointData();
        s.worldPoint.x = 2951;
        s.worldPoint.y = 3451;
        s.worldPoint.plane = 0;
        s.targets = new QuestData.StepTargetData();
        s.targets.npcIds.add(3893);
        s.lockable = true;
        s.lockingCondition = requirement("VarbitRequirement", "locked");
        s.requirements.add(requirement("ItemRequirement", "6 x Clay"));
        s.recommended.add(requirement("ItemRequirement", "Pickaxe"));
        s.teleports.add(requirement("TeleportItemRequirement", "Falador teleport"));
        s.substeps.add("step-9");
        s.conditional = new QuestData.ConditionalData();
        QuestData.DialogChoiceData dialog = new QuestData.DialogChoiceData();
        dialog.choiceText = "Yes";
        dialog.varbitValueToAnswer = new LinkedHashMap<>();
        dialog.varbitValueToAnswer.put(1, "one");
        s.dialogChoices.add(dialog);
        QuestData.WidgetChoiceData widget = new QuestData.WidgetChoiceData();
        widget.choiceText = "Pick";
        s.widgetChoices.add(widget);
        s.extra.put("anything", "goes");
        return s;
    }

    static QuestData.QuestPanelData panel(String header, int id, boolean withChild)
    {
        QuestData.QuestPanelData p = new QuestData.QuestPanelData();
        p.header = header;
        p.id = id;
        p.lockingStepId = "step-1";
        p.stepIds.add("step-1");
        p.requirements.add(requirement("ItemRequirement", "6 x Clay"));
        p.recommended.add(requirement("ItemRequirement", "Pickaxe"));
        p.vars.add(31);
        if (withChild)
        {
            p.children.add(panel(header + " child", id + 100, false));
        }
        return p;
    }

    static QuestData quest(String key)
    {
        QuestData q = new QuestData();
        q.questKey = key;
        q.name = key.replace('_', ' ') + "'s <quest>";
        q.questId = 30;
        q.type = "F2P";
        q.difficulty = "NOVICE";
        q.progressVarbitId = 1;
        q.progressVarpId = 31;
        q.completeValue = 100;
        q.stateSkill = "MINING";
        q.rewards.questPoints = 1;
        QuestData.ExperienceReward xp = new QuestData.ExperienceReward();
        xp.skill = "Mining";
        xp.amount = 1300;
        xp.lamp = false;
        q.rewards.experience.add(xp);
        q.itemRequirements.add(requirement("ItemRequirement", "6 x Clay"));
        q.itemRecommended.add(requirement("ItemRequirement", "Pickaxe"));
        q.generalRequirements.add(requirement("SkillRequirement", "10 Mining"));
        q.combatRequirements.add("Level 2 goblin");
        q.notes.add("A note.");
        q.steps.add(step("step-1"));
        q.steps.add(step("step-2"));
        QuestData.StageStepMapping stage = new QuestData.StageStepMapping();
        stage.stage = 0;
        stage.stepId = "step-1";
        q.stageSteps.add(stage);
        q.panels.add(panel("Starting off", 1, true));
        return q;
    }

    static void write(Path dir, String fileName, QuestData quest) throws IOException
    {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), EXPORTER_GSON.toJson(quest), StandardCharsets.UTF_8);
    }

    static Path exportDir(Path parent, String name) throws IOException
    {
        Path dir = parent.resolve(name);
        write(dir, "a.json", quest("ZULU_QUEST"));
        write(dir, "b.json", quest("ALPHA_QUEST"));
        write(dir, "c.json", quest("MIKE_QUEST"));
        return dir;
    }

    static Bundler.Request request(Path normal, Path ironman)
    {
        List<Bundler.Input> inputs = new ArrayList<>();
        inputs.add(new Bundler.Input("NORMAL", normal));
        if (ironman != null)
        {
            inputs.add(new Bundler.Input("IRONMAN", ironman));
        }
        return new Bundler.Request(inputs, questHelper(), UPSTREAM, 1, null, java.util.Set.of());
    }

    private ExportFixtures()
    {
    }
}
