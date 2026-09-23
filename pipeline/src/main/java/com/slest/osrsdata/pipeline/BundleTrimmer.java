package com.slest.osrsdata.pipeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.slest.questdata.QuestData;
import java.util.List;

public final class BundleTrimmer
{
    public static JsonObject entry(QuestData quest)
    {
        JsonObject entry = new JsonObject();
        put(entry, "questKey", quest.questKey);
        put(entry, "name", quest.name);
        put(entry, "questId", quest.questId);
        put(entry, "type", quest.type);
        put(entry, "difficulty", quest.difficulty);
        put(entry, "rewards", quest.rewards);
        put(entry, "itemRequirements", quest.itemRequirements);
        put(entry, "itemRecommended", quest.itemRecommended);
        put(entry, "generalRequirements", quest.generalRequirements);
        put(entry, "combatRequirements", quest.combatRequirements);
        put(entry, "notes", quest.notes);
        if (quest.panels != null)
        {
            entry.add("panels", panels(quest.panels));
        }
        if (quest.steps != null)
        {
            JsonArray steps = new JsonArray();
            for (QuestData.QuestStepData step : quest.steps)
            {
                if (step != null)
                {
                    steps.add(step(step));
                }
            }
            entry.add("steps", steps);
        }
        return entry;
    }

    private static JsonArray panels(List<QuestData.QuestPanelData> panels)
    {
        JsonArray out = new JsonArray();
        for (QuestData.QuestPanelData panel : panels)
        {
            if (panel == null)
            {
                continue;
            }
            JsonObject json = new JsonObject();
            put(json, "header", panel.header);
            put(json, "id", panel.id);
            put(json, "stepIds", panel.stepIds);
            put(json, "requirements", panel.requirements);
            put(json, "recommended", panel.recommended);
            if (panel.children != null)
            {
                json.add("children", panels(panel.children));
            }
            out.add(json);
        }
        return out;
    }

    private static JsonObject step(QuestData.QuestStepData step)
    {
        JsonObject json = new JsonObject();
        put(json, "id", step.id);
        put(json, "type", step.type);
        put(json, "text", step.text);
        put(json, "worldPoint", step.worldPoint);
        put(json, "requirements", step.requirements);
        put(json, "recommended", step.recommended);
        put(json, "teleports", step.teleports);
        return json;
    }

    private static void put(JsonObject into, String name, Object value)
    {
        if (value != null)
        {
            into.add(name, Json.COMPACT.toJsonTree(value));
        }
    }

    private BundleTrimmer()
    {
    }
}
