package com.slest.questdata;

import net.runelite.api.QuestState;

public final class QuestStates
{
    public interface GameReads
    {
        int questScriptState(int questId);

        int varbit(int id);

        int varp(int id);

        int realSkillLevel(String skillName);
    }

    public static QuestState stateOf(QuestData quest, GameReads game)
    {
        if (quest.questId != null && quest.questId != -1)
        {
            switch (game.questScriptState(quest.questId))
            {
                case 2:
                    return QuestState.FINISHED;
                case 1:
                    return QuestState.NOT_STARTED;
                default:
                    return QuestState.IN_PROGRESS;
            }
        }

        int complete = quest.completeValue == null ? -1 : quest.completeValue;

        if (quest.stateSkill != null)
        {
            return game.realSkillLevel(quest.stateSkill) >= complete
                    ? QuestState.FINISHED
                    : QuestState.IN_PROGRESS;
        }

        int value;
        if (quest.progressVarbitId != null)
        {
            value = game.varbit(quest.progressVarbitId);
        }
        else if (quest.progressVarpId != null)
        {
            value = game.varp(quest.progressVarpId);
        }
        else
        {
            value = -1;
        }

        if (value == -1)
        {
            return QuestState.IN_PROGRESS;
        }
        if (complete != -1)
        {
            if (value == complete)
            {
                return QuestState.FINISHED;
            }
            if (value == 0)
            {
                return QuestState.NOT_STARTED;
            }
            return QuestState.IN_PROGRESS;
        }
        return QuestState.NOT_STARTED;
    }

    private QuestStates()
    {
    }
}
