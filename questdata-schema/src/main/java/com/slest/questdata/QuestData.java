package com.slest.questdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuestData {
    public int schemaVersion = 3;
    public String questKey;
    public String name;
    public Integer questId;
    public String type;
    public String difficulty;

    public Integer progressVarbitId;
    public Integer progressVarpId;

    public Integer completeValue;
    public String stateSkill;

    public QuestRewards rewards = new QuestRewards();
    public List<RequirementData> itemRequirements = new ArrayList<>();
    public List<RequirementData> itemRecommended = new ArrayList<>();
    public List<RequirementData> generalRequirements = new ArrayList<>();
    public List<String> combatRequirements = new ArrayList<>();
    public List<String> notes = new ArrayList<>();

    public List<QuestStepData> steps = new ArrayList<>();
    public List<StageStepMapping> stageSteps = new ArrayList<>();
    public List<QuestPanelData> panels = new ArrayList<>();

    public static class QuestRewards {
        public Integer questPoints;
        public List<ExperienceReward> experience = new ArrayList<>();
        public List<ItemReward> items = new ArrayList<>();
        public List<UnlockReward> unlocks = new ArrayList<>();
    }

    public static class ExperienceReward {
        public String skill;
        public Integer amount;
        public Boolean lamp;
    }

    public static class ItemReward {
        public String name;
        public Integer itemId;
        public Integer quantity;
    }

    public static class UnlockReward {
        public String text;
    }

    public static class StageStepMapping {
        public Integer stage;
        public String stepId;
    }

    public static class QuestPanelData {
        public String header;
        public Integer id;
        public String lockingStepId;
        public List<String> stepIds = new ArrayList<>();
        public List<RequirementData> requirements = new ArrayList<>();
        public List<RequirementData> recommended = new ArrayList<>();
        public List<Integer> vars = new ArrayList<>();
        public List<QuestPanelData> children = new ArrayList<>();
    }

    public static class QuestStepData {
        public String id;
        public String type;
        public List<String> text = new ArrayList<>();
        public List<String> overlayText = new ArrayList<>();
        public WorldPointData worldPoint;
        public StepTargetData targets;
        public Boolean lockable;
        public RequirementData lockingCondition;
        public List<RequirementData> requirements = new ArrayList<>();
        public List<RequirementData> recommended = new ArrayList<>();
        public List<RequirementData> teleports = new ArrayList<>();
        public List<String> substeps = new ArrayList<>();
        public ConditionalData conditional;
        public List<DialogChoiceData> dialogChoices = new ArrayList<>();
        public List<WidgetChoiceData> widgetChoices = new ArrayList<>();
        public Map<String, Object> extra = new LinkedHashMap<>();
    }

    public static class ConditionalData {
        public List<ConditionalBranchData> branches = new ArrayList<>();
        public List<RequirementData> requirements = new ArrayList<>();
        public RequirementData lockingCondition;
    }

    public static class ConditionalBranchData {
        public RequirementData condition;
        public String stepId;
    }

    public static class StepTargetData {
        public List<Integer> npcIds = new ArrayList<>();
        public List<Integer> objectIds = new ArrayList<>();
        public List<Integer> itemIds = new ArrayList<>();
        public List<WidgetTargetData> widgets = new ArrayList<>();
        public String npcName;
    }

    public static class WidgetTargetData {
        public Integer groupId;
        public Integer childId;
        public Integer childChildId;
    }

    public static class WorldPointData {
        public Integer x;
        public Integer y;
        public Integer plane;
    }

    public static class RequirementData {
        public String type;
        public String displayText;
        public Map<String, Object> data = new LinkedHashMap<>();
    }

    public static class DialogChoiceData {
        public Integer choiceId;
        public String choiceText;
        public String regex;
        public Integer groupId;
        public Integer childId;
        public Integer varbitId;
        public Map<Integer, String> varbitValueToAnswer;
        public List<String> excludedStrings = new ArrayList<>();
        public Integer excludedGroupId;
        public Integer excludedChildId;
        public String expectedPreviousLine;
    }

    public static class WidgetChoiceData {
        public Integer choiceId;
        public String choiceText;
        public String regex;
        public Integer groupId;
        public Integer childId;
        public Integer groupIdForChecking;
        public Integer varbitId;
        public Map<Integer, String> varbitValueToAnswer;
        public List<String> excludedStrings = new ArrayList<>();
        public Integer excludedGroupId;
        public Integer excludedChildId;
    }
}
