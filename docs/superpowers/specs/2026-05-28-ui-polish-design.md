# UI Polish — Workflow Header, Toolbar Icons, Picker Dialogs — Design Spec

**Date:** 2026-05-28
**Status:** Approved

---

## Overview

Four targeted improvements to `AutomationView` and its supporting dialogs:

1. Replace the workflow `Combo` with a label area (name + description) and an **Open Workflow** toolbar button.
2. Make all toolbar buttons icon-only with tooltips (no text labels), using Eclipse standard icons.
3. Replace the blank-step insertion in **Add Step** with a picker dialog showing all registered actions and their descriptions.
4. Surface the workflow description that was previously invisible after creation.

---

## Architecture

### 1. Header area

The `Combo` widget is removed. In its place, a small composite with two stacked `Label` widgets:

- **Name label** — bold font, fills width. Shows `currentWorkflow.getDisplayName()`, or `"(no workflows)"` when none exist.
- **Description label** — system color `SWT.COLOR_DARK_GRAY`, fills width. Shows `currentWorkflow.getDescription()`, or empty string.

A new private helper `updateHeader()` sets both labels and is called whenever `currentWorkflow` changes (on load, after open, after new).

The `Combo`-related field (`workflowCombo`) and all references to it are removed. `loadWorkflows()` no longer populates a combo; it still populates the `workflows` list and calls `updateHeader()`.

### 2. Toolbar — icon-only with tooltips

All `ToolItem`s lose their `setText()` call. Each gets an `Image` via:

- `PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_…)` for standard platform icons.
- `DebugUITools.getImage(IDebugUIConstants.IMG_…)` for run/stop icons.

`MANIFEST.MF` gains one new `Require-Bundle` entry: `org.eclipse.debug.ui`.

**Icon mapping:**

| Button | Tooltip | Icon constant | Source |
|---|---|---|---|
| New Workflow | "New Workflow" | `ISharedImages.IMG_TOOL_NEW_WIZARD` | `ISharedImages` |
| Open Workflow | "Open Workflow" | `ISharedImages.IMG_OBJ_FOLDER` | `ISharedImages` |
| Add Step | "Add Step" | `ISharedImages.IMG_OBJ_ADD` | `ISharedImages` |
| Delete Step | "Delete Step" | `ISharedImages.IMG_TOOL_DELETE` | `ISharedImages` |
| Move Up | "Move Step Up" | `ISharedImages.IMG_TOOL_UP` | `ISharedImages` |
| Move Down | "Move Step Down" | `ISharedImages.IMG_TOOL_DOWN` | `ISharedImages` |
| Run | "Run Workflow" | `IDebugUIConstants.IMG_ACT_RUN` | `org.eclipse.debug.ui` |
| Run Selected | "Run Selected Steps" | `IDebugUIConstants.IMG_ACT_RUN` | `org.eclipse.debug.ui` |
| Stop | "Stop" | `IDebugUIConstants.IMG_ELCL_STOP` (verify — may be `IMG_LCL_TERMINATE` in the target Eclipse version) | `org.eclipse.debug.ui` |

The `makeButton` helper is updated to accept an `Image` parameter instead of a `String` text parameter.

A new **Open Workflow** toolbar item is inserted between New Workflow and the first separator. It calls a new `onOpenWorkflow()` method.

### 3. `WorkflowPickerDialog` (new)

`TitleAreaDialog` subclass. Allows the user to select an existing workflow from a table.

**Constructor:** `WorkflowPickerDialog(Shell parent, List<Workflow> workflows)`

**UI:**
- Title: "Open Workflow"
- Message: "Select a workflow to open."
- `TableViewer` with full-selection, border, and two columns:
  - **Name** (expanding) — `workflow.getDisplayName()`
  - **Description** (fixed 250 px) — `workflow.getDescription()`
- Double-click on a row triggers OK.
- OK button disabled until a row is selected; enabled by `ISelectionChangedListener`.

**`getResult()`:** Returns the selected `Workflow`, or `null` if dialog was cancelled.

`AutomationView.onOpenWorkflow()` opens this dialog and, on `Window.OK`, sets `currentWorkflow`, refreshes the table, and calls `updateHeader()` and `updateButtonStates()`.

### 4. `AddStepDialog` (new)

`TitleAreaDialog` subclass. Allows the user to pick an action type for a new step.

**Constructor:** `AddStepDialog(Shell parent, ActionRegistry registry)`

**UI:**
- Title: "Add Step"
- Message: "Select an action type for the new step."
- `TableViewer` with full-selection, border, and two columns:
  - **Name** (fixed 180 px) — `action.getName()`
  - **Description** (expanding) — `action.getDescription()`
- Double-click on a row triggers OK.
- OK button disabled until a row is selected.

**`getResult()`:** Returns a ready-to-use `Step`, built by the package-private static helper:

```java
static Step createStep(IAction action) {
    Step step = new Step(action.getId());
    step.setConfig(new HashMap<>(action.getDefaultConfig()));
    return step;
}
```

`getDefaultConfig()` provides the initial config map; a defensive copy ensures the action's own map is not mutated.

`AutomationView.onAddStep()` replaces the current blank-step logic: it opens `AddStepDialog`, and on `Window.OK` appends `dialog.getResult()` to `currentWorkflow.getSteps()`, saves, and refreshes.

---

## File Structure

| File | Change |
|---|---|
| `com.example.automation/META-INF/MANIFEST.MF` | Add `org.eclipse.debug.ui` to `Require-Bundle` |
| `com.example.automation/src/…/AutomationView.java` | Remove combo; add header labels + `updateHeader()`; icon toolbar with Open Workflow; `onAddStep()` opens `AddStepDialog`; `onOpenWorkflow()` |
| `com.example.automation/src/…/WorkflowPickerDialog.java` | **New** |
| `com.example.automation/src/…/AddStepDialog.java` | **New** (includes `static createStep()`) |
| `com.example.automation.tests/src/…/AddStepDialogTest.java` | **New** — 2 unit tests for `createStep()` |

---

## Testing Strategy

**`AddStepDialogTest`** (2 pure JUnit 4 tests, no Eclipse runtime required):

1. `createStep_setsActionId` — given a mock `IAction` returning id `"shell-command"`, asserts `step.getActionId().equals("shell-command")`.
2. `createStep_copiesDefaultConfig` — given a mock `IAction` returning `{"command": "echo hi"}`, asserts config is equal but not the same map instance (defensive copy).

`WorkflowPickerDialog` has no unit-testable logic (pure display and selection); no test class.

**Expected test count:** 72 existing + 2 new = 74 tests passing.

---

## What Is NOT in Scope

- Delete Workflow button (not requested)
- Editing workflow name or description after creation (Properties View is the right place if needed later)
- Filtering/search in the picker dialogs
- Icons for the status column in the step table
