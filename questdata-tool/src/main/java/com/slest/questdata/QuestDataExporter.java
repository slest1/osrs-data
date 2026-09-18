package com.slest.questdata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class QuestDataExporter
{
    private static final int SCHEMA_VERSION = 3;

    private static final List<String> EXPORTED_TYPES =
            List.of("P2P", "F2P", "ACHIEVEMENT_DIARY", "MINIQUEST");

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final QhMirror mirror;
    private final Object questHelperConfig;
    private final Object configManager;
    private Object client;
    private Object questHelperPlugin;

    public QuestDataExporter(QhMirror mirror)
    {
        this(mirror, mirror == null ? null : mirror.defaultConfig());
    }

    public QuestDataExporter(QhMirror mirror, Object questHelperConfig)
    {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.questHelperConfig = questHelperConfig;
        this.configManager = mirror.stubConfigManager();
    }

    public QuestDataExporter withStubClient()
    {
        return withStubClient(1, "NORMAL");
    }

    public QuestDataExporter withStubClient(int skillLevel, String accountType)
    {
        this.client = mirror.stubClient(skillLevel);
        this.questHelperPlugin = mirror.stubQuestHelperPlugin(this.client, accountType);
        return this;
    }

    public static class ExportResult
    {
        public final List<QuestData> quests = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public final List<String> empty = new ArrayList<>();
    }

    public QuestData exportQuest(Object quest)
    {
        QuestData data = new QuestData();
        data.schemaVersion = SCHEMA_VERSION;
        data.questKey = quest instanceof Enum<?> ? ((Enum<?>) quest).name() : null;
        data.name = mirror.callString(quest, "getName");
        data.questId = mirror.callInt(quest, "getId");
        populateProgressVariable(data, quest);

        Object questType = mirror.call(quest, "getQuestType");
        if (questType != null)
        {
            data.type = QhMirror.nameOf(questType);
        }
        Object difficulty = mirror.call(quest, "getDifficulty");
        if (difficulty != null)
        {
            data.difficulty = QhMirror.nameOf(difficulty);
        }

        Object helper = mirror.call(quest, "getQuestHelper");
        if (helper == null)
        {
            return data;
        }

        ensureHelperContext(quest, helper);
        try
        {
            mirror.callOrThrow(helper, "initializeRequirements");
        }
        catch (Throwable t)
        {
            throw new IllegalStateException("initializeRequirements: " + describe(t), t);
        }

        // steps before requirements: some helpers assign their requirement fields in setupSteps()
        Map<Integer, Object> stepMap = loadSteps(helper);
        IdentityHashMap<Object, String> stepIds = new IdentityHashMap<>();
        List<QuestData.QuestStepData> stepData = new ArrayList<>();
        IdentityHashMap<Object, QuestData.QuestStepData> visited = new IdentityHashMap<>();
        if (stepMap != null)
        {
            for (Map.Entry<Integer, Object> entry : stepMap.entrySet())
            {
                Object step = entry.getValue();
                String stepId = getStepId(stepIds, step);
                QuestData.StageStepMapping mapping = new QuestData.StageStepMapping();
                mapping.stage = entry.getKey();
                mapping.stepId = stepId;
                data.stageSteps.add(mapping);
                collectStep(step, stepIds, stepData, visited);
            }
        }
        data.steps.addAll(stepData);

        List<Object> requiredItems = mirror.callListNonNull(helper, "getItemRequirements");
        if (!requiredItems.isEmpty())
        {
            data.itemRequirements.addAll(exportRequirements(requiredItems));
        }
        List<Object> recommendedItems = mirror.callListNonNull(helper, "getItemRecommended");
        if (!recommendedItems.isEmpty())
        {
            data.itemRecommended.addAll(exportRequirements(recommendedItems));
        }
        List<Object> generalRequirements = mirror.callListNonNull(helper, "getGeneralRequirements");
        if (!generalRequirements.isEmpty())
        {
            data.generalRequirements.addAll(exportRequirements(generalRequirements));
        }
        List<Object> combatRequirements = mirror.callListNonNull(helper, "getCombatRequirements");
        for (Object entry : combatRequirements)
        {
            data.combatRequirements.add(String.valueOf(entry));
        }
        List<Object> notes = mirror.callListNonNull(helper, "getNotes");
        for (Object note : notes)
        {
            data.notes.add(String.valueOf(note));
        }

        Object questPointReward = mirror.call(helper, "getQuestPointReward");
        if (questPointReward != null)
        {
            data.rewards.questPoints = mirror.callInt(questPointReward, "getPoints");
        }
        List<Object> experienceRewards = mirror.callListNonNull(helper, "getExperienceRewards");
        if (!experienceRewards.isEmpty())
        {
            data.rewards.experience.addAll(exportExperienceRewards(experienceRewards));
        }
        List<Object> itemRewards = mirror.callListNonNull(helper, "getItemRewards");
        if (!itemRewards.isEmpty())
        {
            data.rewards.items.addAll(exportItemRewards(itemRewards));
        }
        List<Object> unlockRewards = mirror.callListNonNull(helper, "getUnlockRewards");
        if (!unlockRewards.isEmpty())
        {
            data.rewards.unlocks.addAll(exportUnlockRewards(unlockRewards));
        }

        List<Object> panels = mirror.callListNonNull(helper, "getPanels");
        if (!panels.isEmpty())
        {
            data.panels.addAll(exportPanels(panels, stepIds));
        }

        return data;
    }

    private void populateProgressVariable(QuestData data, Object quest)
    {
        if (data == null || quest == null)
        {
            return;
        }

        Integer completeValue = mirror.read(quest, "completeValue", Integer.class);
        boolean hasQuestId = data.questId != null && data.questId != -1;
        if (!hasQuestId && completeValue != null && completeValue != -1)
        {
            data.completeValue = completeValue;
        }
        Object skill = mirror.read(quest, "skill", Object.class);
        if (skill != null)
        {
            data.stateSkill = QhMirror.nameOf(skill);
        }

        // getter first, then the field: the plugin-hub build has only the field
        Object varbit = mirror.call(quest, "getVarbit");
        if (varbit == null)
        {
            varbit = mirror.read(quest, "varbit", Object.class);
        }
        if (varbit != null)
        {
            data.progressVarbitId = mirror.callInt(varbit, "getId");
            data.progressVarpId = null;
            return;
        }

        Object varPlayer = mirror.call(quest, "getVarPlayer");
        if (varPlayer == null)
        {
            varPlayer = mirror.read(quest, "varPlayer", Object.class);
        }
        if (varPlayer != null)
        {
            data.progressVarpId = mirror.callInt(varPlayer, "getId");
            data.progressVarbitId = null;
        }
    }

    private void ensureHelperContext(Object quest, Object helper)
    {
        if (mirror.call(helper, "getQuest") == null)
        {
            invokeOneArg(helper, "setQuest", quest);
        }
        if (questHelperConfig != null && mirror.call(helper, "getConfig") == null)
        {
            invokeOneArg(helper, "setConfig", questHelperConfig);
        }
        if (configManager != null && mirror.read(helper, "configManager", Object.class) == null)
        {
            mirror.write(helper, "configManager", configManager);
        }
        if (client != null && mirror.read(helper, "client", Object.class) == null)
        {
            mirror.write(helper, "client", client);
        }
        if (questHelperPlugin != null && mirror.read(helper, "questHelperPlugin", Object.class) == null)
        {
            mirror.write(helper, "questHelperPlugin", questHelperPlugin);
        }
        for (Object sub : mirror.callList(helper, "getQuestHelpers"))
        {
            if (questHelperConfig != null && mirror.call(sub, "getConfig") == null)
            {
                invokeOneArg(sub, "setConfig", questHelperConfig);
            }
            if (configManager != null && mirror.read(sub, "configManager", Object.class) == null)
            {
                mirror.write(sub, "configManager", configManager);
            }
            if (client != null && mirror.read(sub, "client", Object.class) == null)
            {
                mirror.write(sub, "client", client);
            }
            if (questHelperPlugin != null && mirror.read(sub, "questHelperPlugin", Object.class) == null)
            {
                mirror.write(sub, "questHelperPlugin", questHelperPlugin);
            }
        }
    }

    private void invokeOneArg(Object target, String name, Object arg)
    {
        if (target == null || arg == null)
        {
            return;
        }
        for (java.lang.reflect.Method m : target.getClass().getMethods())
        {
            if (m.getParameterCount() != 1 || !m.getName().equals(name))
            {
                continue;
            }
            if (!m.getParameterTypes()[0].isInstance(arg))
            {
                continue;
            }
            try
            {
                m.invoke(target, arg);
                return;
            }
            catch (Throwable ignored)
            {
                return;
            }
        }
    }

    public List<Object> quests()
    {
        List<Object> out = new ArrayList<>();
        for (Object quest : mirror.enumValues("questinfo.QuestHelperQuest"))
        {
            String type = QhMirror.nameOf(mirror.call(quest, "getQuestType"));
            if (type != null && EXPORTED_TYPES.contains(type))
            {
                out.add(quest);
            }
        }
        return out;
    }

    public ExportResult exportAllSafe()
    {
        ExportResult result = new ExportResult();
        for (Object quest : quests())
        {
            try
            {
                QuestData data = exportQuest(quest);
                result.quests.add(data);
                if (data.steps.isEmpty())
                {
                    result.empty.add(data.questKey);
                }
            }
            catch (Throwable t)
            {
                String key = quest instanceof Enum<?> ? ((Enum<?>) quest).name() : String.valueOf(quest);
                String message = key + ": " + t.getClass().getSimpleName();
                if (t.getMessage() != null && !t.getMessage().isEmpty())
                {
                    message += " - " + t.getMessage();
                }
                result.errors.add(message);
            }
        }
        return result;
    }

    public void writeQuest(Path outputDir, QuestData data) throws IOException
    {
        Files.createDirectories(outputDir);
        String fileName = data.questKey == null ? "unknown" : data.questKey.toLowerCase();
        Path outputPath = outputDir.resolve(fileName + ".json");
        Files.writeString(outputPath, gson.toJson(data), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public ExportResult writeAll(Path outputDir) throws IOException
    {
        Files.createDirectories(outputDir);
        ExportResult result = exportAllSafe();
        for (QuestData data : result.quests)
        {
            writeQuest(outputDir, data);
        }
        return result;
    }

    private List<QuestData.RequirementData> exportRequirements(List<?> requirements)
    {
        return exportRequirements(requirements, new IdentityHashMap<>());
    }

    private List<QuestData.RequirementData> exportRequirements(List<?> requirements,
                                                               IdentityHashMap<Object, QuestData.RequirementData> seen)
    {
        if (requirements == null)
        {
            return List.of();
        }
        List<QuestData.RequirementData> exported = new ArrayList<>();
        for (Object requirement : requirements)
        {
            QuestData.RequirementData data = exportRequirement(requirement, seen);
            if (data != null)
            {
                exported.add(data);
            }
        }
        return exported;
    }

    private QuestData.RequirementData exportRequirement(Object requirement,
                                                        IdentityHashMap<Object, QuestData.RequirementData> seen)
    {
        if (requirement == null)
        {
            return null;
        }
        QuestData.RequirementData existing = seen.get(requirement);
        if (existing != null)
        {
            QuestData.RequirementData reference = new QuestData.RequirementData();
            reference.type = existing.type;
            reference.displayText = existing.displayText;
            reference.data.put("reference", true);
            return reference;
        }

        QuestData.RequirementData data = new QuestData.RequirementData();
        data.type = mirror.typeOf(requirement);
        data.displayText = safeDisplayText(requirement);
        seen.put(requirement, data);

        if (mirror.isA(requirement, "ItemRequirements"))
        {
            data.data.put("logicType", QhMirror.nameOf(mirror.call(requirement, "getLogicType")));
            data.data.put("items", mirror.callList(requirement, "getItemRequirements").stream()
                    .map(req -> exportRequirement(req, seen))
                    .collect(Collectors.toList()));
        }
        else if (mirror.isA(requirement, "ItemRequirement"))
        {
            Integer id = mirror.callInt(requirement, "getId");
            data.data.put("id", id);
            data.data.put("name", mirror.callString(requirement, "getName"));
            data.data.put("quantity", mirror.callInt(requirement, "getQuantity"));
            data.data.put("mustBeEquipped", mirror.callBool(requirement, "mustBeEquipped"));
            data.data.put("consumed", mirror.callBool(requirement, "isConsumedItem"));
            data.data.put("charged", mirror.callBool(requirement, "isChargedItem"));
            data.data.put("displayItemId", mirror.callInt(requirement, "getDisplayItemId"));
            List<Integer> alternates = new ArrayList<>();
            for (Object raw : mirror.callList(requirement, "getAllIds"))
            {
                if (raw instanceof Number)
                {
                    int value = ((Number) raw).intValue();
                    if (id == null || value != id)
                    {
                        alternates.add(value);
                    }
                }
            }
            data.data.put("alternates", alternates);
            if (mirror.isA(requirement, "TeleportItemRequirement"))
            {
                data.data.put("teleportItem", true);
            }
        }
        else if (mirror.isA(requirement, "ComplexRequirement"))
        {
            data.data.put("logicType", QhMirror.nameOf(mirror.call(requirement, "getLogicType")));
            data.data.put("requirements", exportRequirements(toList(mirror.call(requirement, "getRequirements")), seen));
            data.data.put("name", mirror.callString(requirement, "getName"));
        }
        else if (mirror.isA(requirement, "Conditions"))
        {
            data.data.put("logicType", QhMirror.nameOf(mirror.read(requirement, "logicType", Object.class)));
            data.data.put("operation", QhMirror.nameOf(mirror.read(requirement, "operation", Object.class)));
            data.data.put("quantity", mirror.read(requirement, "quantity", Integer.class));
            data.data.put("onlyNeedToPassOnce", mirror.read(requirement, "onlyNeedToPassOnce", Boolean.class));
            data.data.put("conditions", exportRequirements(mirror.callList(requirement, "getConditions"), seen));
        }
        else if (mirror.isA(requirement, "ConditionForStep"))
        {
            data.data.put("logicType", QhMirror.nameOf(mirror.read(requirement, "logicType", Object.class)));
            data.data.put("onlyNeedToPassOnce", mirror.read(requirement, "onlyNeedToPassOnce", Boolean.class));
            data.data.put("conditions", exportRequirements(mirror.callList(requirement, "getConditions"), seen));
        }
        else if (mirror.isA(requirement, "SkillRequirement"))
        {
            data.data.put("skill", QhMirror.nameOf(mirror.call(requirement, "getSkill")));
            data.data.put("requiredLevel", mirror.callInt(requirement, "getRequiredLevel"));
            data.data.put("operation", QhMirror.nameOf(mirror.call(requirement, "getOperation")));
            data.data.put("canBeBoosted", mirror.read(requirement, "canBeBoosted", Boolean.class));
        }
        else if (mirror.isA(requirement, "QuestRequirement"))
        {
            Object quest = mirror.call(requirement, "getQuest");
            if (quest != null)
            {
                data.data.put("questKey", quest instanceof Enum<?> ? ((Enum<?>) quest).name() : null);
                data.data.put("questName", mirror.callString(quest, "getName"));
            }
            Object state = mirror.call(requirement, "getRequiredState");
            if (state != null)
            {
                data.data.put("requiredState", QhMirror.nameOf(state));
            }
            data.data.put("minimumVarValue", mirror.callInt(requirement, "getMinimumVarValue"));
        }
        else if (mirror.isA(requirement, "QuestPointRequirement"))
        {
            data.data.put("requiredQuestPoints", mirror.callInt(requirement, "getRequiredQuestPoints"));
            data.data.put("operation", QhMirror.nameOf(mirror.read(requirement, "operation", Object.class)));
        }
        else if (mirror.isA(requirement, "VarbitRequirement"))
        {
            data.data.put("varbitId", mirror.callInt(requirement, "getVarbitID"));
            data.data.put("requiredValue", mirror.callInt(requirement, "getRequiredValue"));
            data.data.put("operation", QhMirror.nameOf(mirror.call(requirement, "getOperation")));
        }
        else if (mirror.isA(requirement, "ZoneRequirement"))
        {
            data.data.put("checkInZone", mirror.read(requirement, "checkInZone", Boolean.class));
            data.data.put("zones", exportZones(mirror.callList(requirement, "getZones")));
        }
        else if (mirror.isA(requirement, "WidgetTextRequirement"))
        {
            data.data.put("groupId", mirror.callInt(requirement, "getGroupId"));
            data.data.put("childId", mirror.read(requirement, "childId", Integer.class));
            data.data.put("childChildId", mirror.read(requirement, "childChildId", Integer.class));
            data.data.put("text", mirror.read(requirement, "text", List.class));
            data.data.put("checkChildren", mirror.read(requirement, "checkChildren", Boolean.class));
            data.data.put("min", mirror.read(requirement, "min", Integer.class));
            data.data.put("max", mirror.read(requirement, "max", Integer.class));
        }
        else if (mirror.isA(requirement, "WidgetModelRequirement"))
        {
            data.data.put("groupId", mirror.read(requirement, "groupId", Integer.class));
            data.data.put("childId", mirror.read(requirement, "childId", Integer.class));
            data.data.put("childChildId", mirror.read(requirement, "childChildId", Integer.class));
            data.data.put("modelId", mirror.read(requirement, "id", Integer.class));
        }
        else if (mirror.isA(requirement, "WidgetPresenceRequirement"))
        {
            data.data.put("groupId", mirror.callInt(requirement, "getGroupId"));
            data.data.put("childId", mirror.read(requirement, "childId", Integer.class));
            data.data.put("childChildId", mirror.read(requirement, "childChildId", Integer.class));
        }
        else if (mirror.isA(requirement, "PrayerRequirement"))
        {
            data.data.put("prayer", QhMirror.nameOf(mirror.read(requirement, "prayer", Object.class)));
        }
        else if (mirror.isA(requirement, "SpellbookRequirement"))
        {
            data.data.put("spellbook", QhMirror.nameOf(mirror.read(requirement, "spellBook", Object.class)));
        }
        else if (mirror.isA(requirement, "FreeInventorySlotRequirement"))
        {
            data.data.put("numSlotsFree", mirror.callInt(requirement, "getNumSlotsFree"));
            data.data.put("inventoryId", mirror.callInt(requirement, "getInventoryID"));
        }
        else if (mirror.isA(requirement, "NpcCondition"))
        {
            data.data.put("npcId", mirror.read(requirement, "npcID", Integer.class));
            data.data.put("zone", exportZone(mirror.read(requirement, "zone", Object.class)));
            data.data.put("animationId", mirror.read(requirement, "animationIDRequired", Integer.class));
        }
        else if (mirror.isA(requirement, "ObjectCondition"))
        {
            data.data.put("objectIds", mirror.read(requirement, "objectIDs", java.util.Set.class));
            data.data.put("zone", exportZone(mirror.read(requirement, "zone", Object.class)));
            data.data.put("maxDistanceFromPlayer", mirror.read(requirement, "maxDistanceFromPlayer", Integer.class));
            data.data.put("onlyCheckGameObjects", mirror.read(requirement, "onlyCheckGameObjects", Boolean.class));
        }
        else if (mirror.isA(requirement, "NpcInteractingRequirement"))
        {
            data.data.put("npcIds", mirror.read(requirement, "npcIDs", List.class));
        }
        else if (mirror.isA(requirement, "ItemOnTileRequirement"))
        {
            data.data.put("itemIds", mirror.read(requirement, "itemID", List.class));
            data.data.put("worldPoint", exportWorldPoint(mirror.read(requirement, "definedPoint", Object.class)));
        }

        return data;
    }

    private List<QuestData.ExperienceReward> exportExperienceRewards(List<?> rewards)
    {
        List<QuestData.ExperienceReward> exported = new ArrayList<>();
        for (Object reward : rewards)
        {
            QuestData.ExperienceReward data = new QuestData.ExperienceReward();
            Object skill = mirror.call(reward, "getSkill");
            data.skill = skill instanceof Skill ? ((Skill) skill).getName() : QhMirror.nameOf(skill);
            data.amount = mirror.read(reward, "experience", Integer.class);
            data.lamp = mirror.read(reward, "lamp", Boolean.class);
            exported.add(data);
        }
        return exported;
    }

    private List<QuestData.ItemReward> exportItemRewards(List<?> rewards)
    {
        List<QuestData.ItemReward> exported = new ArrayList<>();
        for (Object reward : rewards)
        {
            QuestData.ItemReward data = new QuestData.ItemReward();
            data.name = mirror.callString(reward, "getName");
            data.itemId = mirror.read(reward, "itemID", Integer.class);
            data.quantity = mirror.read(reward, "quantity", Integer.class);
            exported.add(data);
        }
        return exported;
    }

    private List<QuestData.UnlockReward> exportUnlockRewards(List<?> rewards)
    {
        List<QuestData.UnlockReward> exported = new ArrayList<>();
        for (Object reward : rewards)
        {
            QuestData.UnlockReward data = new QuestData.UnlockReward();
            data.text = mirror.read(reward, "unlock", String.class);
            exported.add(data);
        }
        return exported;
    }

    private void collectStep(Object step, IdentityHashMap<Object, String> stepIds,
                             List<QuestData.QuestStepData> output,
                             IdentityHashMap<Object, QuestData.QuestStepData> visited)
    {
        if (step == null || visited.containsKey(step))
        {
            return;
        }

        QuestData.QuestStepData data = new QuestData.QuestStepData();
        data.id = getStepId(stepIds, step);
        // before the substep walk, or a step that reaches itself recurses forever
        visited.put(step, data);
        data.type = mirror.typeOf(step);
        for (Object line : mirror.callList(step, "getText"))
        {
            data.text.add(String.valueOf(line));
        }
        for (Object line : mirror.callList(step, "getOverlayText"))
        {
            data.overlayText.add(String.valueOf(line));
        }
        data.lockable = mirror.callBool(step, "isLockable");
        data.lockingCondition = exportRequirement(mirror.call(step, "getLockingCondition"), new IdentityHashMap<>());

        if (mirror.isA(step, "DetailedQuestStep"))
        {
            data.worldPoint = exportWorldPoint(mirror.call(step, "getDefinedPoint"));
            data.requirements.addAll(exportRequirements(mirror.callList(step, "getRequirements")));
            data.recommended.addAll(exportRequirements(mirror.callList(step, "getRecommended")));
            data.teleports.addAll(exportRequirements(mirror.callList(step, "getTeleport")));
        }

        data.targets = extractTargets(step);
        data.dialogChoices.addAll(exportDialogChoices(step));
        data.widgetChoices.addAll(exportWidgetChoices(step));

        if (mirror.isA(step, "ConditionalStep"))
        {
            QuestData.ConditionalData conditionalData = new QuestData.ConditionalData();
            conditionalData.lockingCondition =
                    exportRequirement(mirror.call(step, "getLockingCondition"), new IdentityHashMap<>());
            List<?> conditionalRequirements = mirror.read(step, "requirements", List.class);
            if (conditionalRequirements != null)
            {
                conditionalData.requirements.addAll(exportRequirements(conditionalRequirements));
            }
            for (Map.Entry<Object, Object> entry : mirror.<Object, Object>callMap(step, "getStepsMap").entrySet())
            {
                QuestData.ConditionalBranchData branch = new QuestData.ConditionalBranchData();
                branch.condition = exportRequirement(entry.getKey(), new IdentityHashMap<>());
                branch.stepId = getStepId(stepIds, entry.getValue());
                conditionalData.branches.add(branch);
                collectStep(entry.getValue(), stepIds, output, visited);
            }
            data.conditional = conditionalData;

            if (mirror.isA(step, "PuzzleWrapperStep"))
            {
                data.extra.put("puzzleWrapper", true);
            }
        }

        for (Object substep : mirror.callList(step, "getSubsteps"))
        {
            data.substeps.add(getStepId(stepIds, substep));
            collectStep(substep, stepIds, output, visited);
        }

        output.add(data);
    }

    private QuestData.StepTargetData extractTargets(Object step)
    {
        QuestData.StepTargetData targets = new QuestData.StepTargetData();
        boolean hasTargets = false;

        if (mirror.isA(step, "ObjectStep"))
        {
            Integer objectId = mirror.read(step, "objectID", Integer.class);
            if (objectId != null)
            {
                targets.objectIds.add(objectId);
                hasTargets = true;
            }
            List<Integer> alternate = mirror.read(step, "alternateObjectIDs", List.class);
            if (alternate != null)
            {
                targets.objectIds.addAll(alternate);
                hasTargets = true;
            }
        }

        if (mirror.isA(step, "NpcStep"))
        {
            Integer npcId = mirror.read(step, "npcID", Integer.class);
            if (npcId != null)
            {
                targets.npcIds.add(npcId);
                hasTargets = true;
            }
            List<Integer> alternate = mirror.read(step, "alternateNpcIDs", List.class);
            if (alternate != null)
            {
                targets.npcIds.addAll(alternate);
                hasTargets = true;
            }
            String npcName = mirror.read(step, "npcName", String.class);
            if (npcName != null && !npcName.isEmpty())
            {
                targets.npcName = npcName;
            }
        }

        if (mirror.isA(step, "WidgetStep"))
        {
            List<?> widgetDetails = mirror.callList(step, "getWidgetDetails");
            if (widgetDetails.isEmpty())
            {
                widgetDetails = mirror.readList(step, "widgetDetails");
            }
            for (Object details : widgetDetails)
            {
                QuestData.WidgetTargetData widget = new QuestData.WidgetTargetData();
                Integer groupId = mirror.callInt(details, "getGroupID");
                widget.groupId = groupId != null ? groupId : mirror.read(details, "groupID", Integer.class);
                Integer childId = mirror.callInt(details, "getChildID");
                widget.childId = childId != null ? childId : mirror.read(details, "childID", Integer.class);
                Integer childChildId = mirror.callInt(details, "getChildChildID");
                widget.childChildId =
                        childChildId != null ? childChildId : mirror.read(details, "childChildID", Integer.class);
                targets.widgets.add(widget);
                hasTargets = true;
            }
        }

        return hasTargets ? targets : null;
    }

    private List<QuestData.DialogChoiceData> exportDialogChoices(Object step)
    {
        List<QuestData.DialogChoiceData> output = new ArrayList<>();
        for (Object choice : mirror.callList(mirror.call(step, "getChoices"), "getChoices"))
        {
            QuestData.DialogChoiceData data = new QuestData.DialogChoiceData();
            data.choiceId = mirror.read(choice, "choiceById", Integer.class);
            data.choiceText = mirror.callString(choice, "getChoice");
            Object pattern = mirror.read(choice, "pattern", Object.class);
            data.regex = pattern != null ? pattern.toString() : null;
            data.groupId = mirror.read(choice, "groupId", Integer.class);
            data.childId = mirror.read(choice, "childId", Integer.class);
            data.varbitId = mirror.read(choice, "varbitId", Integer.class);
            data.varbitValueToAnswer = sortedByKey(mirror.read(choice, "varbitValueToAnswer", Map.class));
            data.excludedStrings = safeStrings(mirror.read(choice, "excludedStrings", List.class));
            data.excludedGroupId = mirror.read(choice, "excludedGroupId", Integer.class);
            data.excludedChildId = mirror.read(choice, "excludedChildId", Integer.class);
            data.expectedPreviousLine = mirror.callString(choice, "getExpectedPreviousLine");
            output.add(data);
        }
        return output;
    }

    private List<QuestData.WidgetChoiceData> exportWidgetChoices(Object step)
    {
        List<QuestData.WidgetChoiceData> output = new ArrayList<>();
        for (Object choice : mirror.callList(mirror.call(step, "getWidgetChoices"), "getChoices"))
        {
            QuestData.WidgetChoiceData data = new QuestData.WidgetChoiceData();
            data.choiceId = mirror.read(choice, "choiceById", Integer.class);
            data.choiceText = mirror.read(choice, "choice", String.class);
            Object pattern = mirror.read(choice, "pattern", Object.class);
            data.regex = pattern != null ? pattern.toString() : null;
            data.groupId = mirror.read(choice, "groupId", Integer.class);
            data.childId = mirror.read(choice, "childId", Integer.class);
            data.groupIdForChecking = mirror.callInt(choice, "getGroupIdForChecking");
            data.varbitId = mirror.read(choice, "varbitId", Integer.class);
            data.varbitValueToAnswer = sortedByKey(mirror.read(choice, "varbitValueToAnswer", Map.class));
            data.excludedStrings = safeStrings(mirror.read(choice, "excludedStrings", List.class));
            data.excludedGroupId = mirror.read(choice, "excludedGroupId", Integer.class);
            data.excludedChildId = mirror.read(choice, "excludedChildId", Integer.class);
            output.add(data);
        }
        return output;
    }

    private List<QuestData.QuestPanelData> exportPanels(List<?> panels, IdentityHashMap<Object, String> stepIds)
    {
        List<QuestData.QuestPanelData> exported = new ArrayList<>();
        for (Object panel : panels)
        {
            exported.add(exportPanel(panel, stepIds));
        }
        return exported;
    }

    private QuestData.QuestPanelData exportPanel(Object panel, IdentityHashMap<Object, String> stepIds)
    {
        QuestData.QuestPanelData data = new QuestData.QuestPanelData();
        data.header = mirror.callString(panel, "getHeader");
        data.id = mirror.callInt(panel, "getId");
        Object lockingStep = mirror.call(panel, "getLockingQuestSteps");
        if (lockingStep != null)
        {
            data.lockingStepId = getStepId(stepIds, lockingStep);
        }
        for (Object step : mirror.callList(panel, "getSteps"))
        {
            data.stepIds.add(getStepId(stepIds, step));
        }
        data.requirements.addAll(exportRequirements(mirror.callList(panel, "getRequirements")));
        data.recommended.addAll(exportRequirements(mirror.callList(panel, "getRecommended")));
        for (Object var : mirror.callList(panel, "getVars"))
        {
            if (var instanceof Number)
            {
                data.vars.add(((Number) var).intValue());
            }
        }

        if (mirror.isA(panel, "TopLevelPanelDetails"))
        {
            for (Object child : mirror.callList(panel, "getPanelDetails"))
            {
                data.children.add(exportPanel(child, stepIds));
            }
        }
        return data;
    }

    private QuestData.WorldPointData exportWorldPoint(Object point)
    {
        if (point == null)
        {
            return null;
        }
        WorldPoint worldPoint = point instanceof WorldPoint
                ? (WorldPoint) point
                : asWorldPoint(mirror.call(point, "getWorldPoint"));
        if (worldPoint == null)
        {
            return null;
        }
        QuestData.WorldPointData data = new QuestData.WorldPointData();
        data.x = worldPoint.getX();
        data.y = worldPoint.getY();
        data.plane = worldPoint.getPlane();
        return data;
    }

    private WorldPoint asWorldPoint(Object value)
    {
        return value instanceof WorldPoint ? (WorldPoint) value : null;
    }

    private Map<String, Object> exportZone(Object zone)
    {
        if (zone == null)
        {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("minX", mirror.callInt(zone, "getMinX"));
        data.put("maxX", mirror.callInt(zone, "getMaxX"));
        data.put("minY", mirror.callInt(zone, "getMinY"));
        data.put("maxY", mirror.callInt(zone, "getMaxY"));
        data.put("minPlane", mirror.read(zone, "minPlane", Integer.class));
        data.put("maxPlane", mirror.read(zone, "maxPlane", Integer.class));
        return data;
    }

    private List<Map<String, Object>> exportZones(List<?> zones)
    {
        if (zones == null)
        {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object zone : zones)
        {
            Map<String, Object> data = exportZone(zone);
            if (data != null)
            {
                out.add(data);
            }
        }
        return out;
    }

    private String getStepId(IdentityHashMap<Object, String> stepIds, Object step)
    {
        if (step == null)
        {
            return null;
        }
        return stepIds.computeIfAbsent(step, key -> "step-" + (stepIds.size() + 1));
    }

    private static String describe(Throwable t)
    {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null || message.isEmpty() ? "" : " - " + message);
    }

    private String safeDisplayText(Object requirement)
    {
        try
        {
            Object text = mirror.callOrThrow(requirement, "getDisplayText");
            return text == null ? null : String.valueOf(text);
        }
        catch (Throwable ex)
        {
            return mirror.typeOf(requirement);
        }
    }

    /** Map.of iteration order varies per JVM run; sorting keeps re-exports byte-stable. */
    @SuppressWarnings("unchecked")
    private Map<Integer, String> sortedByKey(Map<?, ?> source)
    {
        if (source == null)
        {
            return null;
        }
        Map<Integer, String> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet())
        {
            if (entry.getKey() instanceof Number)
            {
                sorted.put(((Number) entry.getKey()).intValue(),
                        entry.getValue() == null ? null : String.valueOf(entry.getValue()));
            }
        }
        return sorted;
    }

    private List<String> safeStrings(List<?> list)
    {
        if (list == null)
        {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list)
        {
            if (item != null)
            {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private List<Object> toList(Object value)
    {
        List<Object> out = new ArrayList<>();
        if (value == null)
        {
            return out;
        }
        if (value instanceof Collection)
        {
            for (Object item : (Collection<?>) value)
            {
                out.add(item);
            }
            return out;
        }
        if (value.getClass().isArray())
        {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++)
            {
                out.add(Array.get(value, i));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Object> loadSteps(Object helper)
    {
        if (mirror.isA(helper, "BasicQuestHelper"))
        {
            Object result;
            try
            {
                result = mirror.callOrThrow(helper, "loadSteps");
            }
            catch (Throwable t)
            {
                throw new IllegalStateException("loadSteps: " + describe(t), t);
            }
            return result instanceof Map ? new LinkedHashMap<>((Map<Integer, Object>) result) : null;
        }
        if (mirror.isA(helper, "ComplexStateQuestHelper"))
        {
            Object step;
            try
            {
                step = mirror.callOrThrow(helper, "loadStep");
            }
            catch (Throwable t)
            {
                throw new IllegalStateException("loadStep: " + describe(t), t);
            }
            if (step == null)
            {
                return null;
            }
            Map<Integer, Object> steps = new LinkedHashMap<>();
            steps.put(0, step);
            return steps;
        }
        Map<Integer, Object> steps = mirror.read(helper, "steps", Map.class);
        if (steps != null)
        {
            return steps;
        }
        Object result = mirror.call(helper, "loadSteps");
        return result instanceof Map ? new LinkedHashMap<>((Map<Integer, Object>) result) : null;
    }
}
