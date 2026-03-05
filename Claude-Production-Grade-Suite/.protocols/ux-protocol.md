# UX Protocol — Single Source of Truth

**Every skill in this plugin MUST follow these 6 rules for ALL user interactions.**

## RULE 1: NEVER Ask Open-Ended Questions

**NEVER output text expecting the user to type.** Every user interaction MUST use `AskUserQuestion` with predefined options. Users navigate with arrow keys (up/down) and press Enter.

**WRONG:** "What do you think?" / "Do you approve?" / "Any feedback?"
**RIGHT:** Use AskUserQuestion with 2-4 options + "Chat about this" as last option.

## RULE 2: "Chat about this" Always Last

Every `AskUserQuestion` MUST have `"Chat about this"` as the last option — the user's escape hatch for free-form typing.

## RULE 3: Recommended Option First

First option = recommended default with `(Recommended)` suffix.

## RULE 4: Continuous Execution

Work continuously until task complete or user presses ESC. Never ask "should I continue?" — just keep going.

## RULE 5: Real-Time Terminal Updates

Constantly print progress. Never go silent.
```
━━━ [Phase/Task Name] ━━━━━━━━━━━━━━━━━━━━━━

⧖ Working on [current step]...
✓ Step completed (details)
✓ Step completed (details)

━━━ Complete ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Summary: [what was produced]
```

## RULE 6: Autonomy

1. Default to sensible choices — minimize questions
2. Self-resolve issues — debug and fix before asking user
3. Report decisions made, don't ask for permission on minor choices
4. Only use AskUserQuestion for major decisions or approval gates
