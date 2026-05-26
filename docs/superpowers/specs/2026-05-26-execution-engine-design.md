# Execution Engine — Design Spec

**Date:** 2026-05-26
**Status:** Approved
**Sub-project:** 4 of 8 (Workflow Automation)

---

## Overview

Wire the Run, Run Selected, and Stop toolbar buttons in `AutomationView` to a sequential background execution engine. Each step's `StepStatus` is updated live; `viewer.refresh()` drives the visual update already built in sub-project 3.

Also switches the step table from single-select to multi-select, enabling "Run Selected" to act on multiple chosen steps.

---

## Architecture

Two files change:

| File | Change |
|---|---|
| `com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java` | New — all execution logic |
| `com.example.automation/src/main/java/com/example/automation/AutomationView.java` | Multi-select + Run/Stop wiring |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRunnerTest.java` | New — 5 unit tests |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/AutomationViewTest.java` | +1 SWTBot test |

`StepLabelProvider`, the model, `WorkflowRepository`, and the API are untouched.

---

## WorkflowRunner

### Constructor

```java
public WorkflowRunner(
    List<Step> steps,
    Consumer<Runnable> uiRunner,
    Runnable refresh,
    Runnable onDone)
```

| Parameter | Production value | Test value |
|---|---|---|
| `steps` | Steps to execute (pre-reset to WHITE by caller) | Stub `Step` list |
| `uiRunner` | `display::asyncExec` | `r -> r.run()` (synchronous) |
| `refresh` | `() -> viewer.refresh()` | `() -> {}` |
| `onDone` | `() -> { activeRunner = null; updateButtonStates(); viewer.refresh(); }` | `latch::countDown` or assertion |

### Lifecycle

- `start()` — creates a daemon `Thread` named `"WorkflowRunner"`, starts it, returns the thread (so callers can `join()` in tests).
- `cancel()` — sets `volatile boolean cancelled = true`.

### Run loop

```
for each step in steps:
    if cancelled → break

    step.setStatus(YELLOW)
    step.setProgress(0)
    uiRunner.accept(refresh)

    action = ActionRegistry.getInstance().getAction(step.getActionId())
    if action == null:
        step.setStatus(RED)
        uiRunner.accept(refresh)
        break

    try:
        action.execute(step.getConfig(), new ActionContextImpl(step))
        if !cancelled:
            step.setStatus(GREEN)
            uiRunner.accept(refresh)
    catch Exception:
        step.setStatus(RED)
        uiRunner.accept(refresh)
        break

uiRunner.accept(onDone)
```

**On cancel:** the currently running step stays YELLOW (shows where execution stopped). Steps not yet reached stay WHITE.

**On error:** the failing step is marked RED, the loop stops. Remaining steps keep their current status (WHITE if not yet run).

**Unknown action:** treated identically to a thrown exception — RED + stop.

### ActionContextImpl (private inner class)

| Method | Behaviour |
|---|---|
| `setProgress(int percent)` | `step.setProgress(percent)`, then `uiRunner.accept(refresh)` |
| `isCancelled()` | returns `cancelled` |
| `getOutputStream()` | `OutputStream.nullOutputStream()` (console wiring is sub-project 5) |
| `getErrorStream()` | `OutputStream.nullOutputStream()` |

---

## AutomationView Changes

### Multi-select

Table style changes from `SWT.SINGLE` to `SWT.MULTI`.

`updateButtonStates()` for Move Up / Move Down:

```java
int selCount = viewer.getStructuredSelection().size();
moveUpItem.setEnabled(selCount == 1 && selIdx > 0);
moveDownItem.setEnabled(selCount == 1 && selIdx < stepCount - 1);
```

Delete remains `hasStep` (works on the first selected element for consistency).

### New field

```java
private WorkflowRunner activeRunner;
```

### updateButtonStates() additions

```java
boolean running = activeRunner != null;
runItem.setEnabled(!running && hasWorkflow && stepCount > 0);
runSelectedItem.setEnabled(!running && hasStep);
stopItem.setEnabled(running);
```

### onRun()

1. Reset every step in `currentWorkflow.getSteps()` to WHITE, call `viewer.refresh()`
2. Build the shared `onDone` lambda: `() -> { activeRunner = null; updateButtonStates(); viewer.refresh(); }`
3. Create `new WorkflowRunner(allSteps, display::asyncExec, viewer::refresh, onDone)`
4. Set `activeRunner`, call `updateButtonStates()`, call `runner.start()`

### onRunSelected()

1. Get selected elements: `viewer.getStructuredSelection().toList()`
2. Sort by index in `currentWorkflow.getSteps()` to preserve list order regardless of selection order
3. Reset those steps to WHITE, call `viewer.refresh()`
4. Create and start `WorkflowRunner` with the sorted selected steps

### onStop()

```java
if (activeRunner != null) activeRunner.cancel();
```

---

## Data Flow

```
Run clicked
  └─ AutomationView resets all steps to WHITE → viewer.refresh()
  └─ WorkflowRunner created + started → activeRunner set, updateButtonStates()

WorkflowRunner (background thread)
  └─ per step:
       └─ step.setStatus(YELLOW) → uiRunner.accept(refresh) → viewer.refresh() [UI thread]
       └─ action.execute(config, ctx)
            └─ ctx.setProgress(n) → step.setProgress(n) → uiRunner.accept(refresh)
            └─ ctx.isCancelled() → polls cancelled flag
       └─ step.setStatus(GREEN or RED) → uiRunner.accept(refresh)
  └─ uiRunner.accept(onDone) → activeRunner=null, updateButtonStates(), viewer.refresh() [UI thread]

Stop clicked
  └─ activeRunner.cancel() → cancelled=true
  └─ next ctx.isCancelled() check in action → action returns early
  └─ runner sees !cancelled is false → marks remaining steps skipped, calls onDone
```

---

## Test Coverage

### WorkflowRunnerTest (new, plain JUnit 4 — no Eclipse runtime)

Uses `ActionRegistry(List<IAction>)` test constructor, `r -> r.run()` as `uiRunner`, and `thread.join()` for synchronisation.

| Test | Verifies |
|---|---|
| `successfulStep_statusGreen` | No-op action → step ends GREEN |
| `unknownActionId_statusRed` | ActionRegistry returns null → step RED, loop stops |
| `throwingAction_statusRed_nextStepSkipped` | Action throws → first step RED, second step stays WHITE |
| `cancelBeforeStart_noStepsRun` | `cancel()` before `start()` → all steps stay WHITE |
| `progressUpdatesStep` | Action calls `ctx.setProgress(50)` → `step.getProgress() == 50` |

### AutomationViewTest (existing SWTBot harness — +1 test)

| Test | Verifies |
|---|---|
| `multipleStepsCanBeSelected` | Ctrl+click two rows → both rows selected (table is SWT.MULTI) |

---

## What Is NOT in Scope

- Console output (stdout/stderr from actions) — sub-project 5
- Properties View on row selection — sub-project 6
- New Workflow dialog — sub-project 7
- Built-in action implementations — sub-project 8
- Progress bar rendered in the Status column (progress is stored on Step but not visually rendered beyond the YELLOW colour)
