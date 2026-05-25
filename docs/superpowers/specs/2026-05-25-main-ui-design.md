# Main UI — Design Spec

**Date:** 2026-05-25
**Status:** Approved
**Sub-project:** 3 of 8 (Workflow Automation)

---

## Overview

Replace the placeholder `"Preview"` label in `AutomationView` with the full workflow automation UI: a workflow selector ComboBox, a two-group toolbar, and a step table with live-status rendering.

Execution wiring (Run / Stop), Properties View integration, console double-click, and the New Workflow dialog are explicitly out of scope — they are covered in sub-projects 4, 6, 7, and 8 respectively.

---

## View Layout

`AutomationView.createPartControl()` fills its parent `Composite` with a `GridLayout(1, false)` containing three rows:

1. **Combo row** — a read-only `Combo` (`SWT.READ_ONLY`) spanning full width via `GridData(SWT.FILL, SWT.CENTER, true, false)`.
2. **Toolbar row** — a `ToolBar` with three button groups separated by `SWT.SEPARATOR` items.
3. **Table row** — a `TableViewer` with `SWT.FULL_SELECTION | SWT.BORDER` that fills the remaining space via `GridData(SWT.FILL, SWT.FILL, true, true)`.

---

## Combo (Workflow Selector)

- Populated from `WorkflowRepository.list()` on `createPartControl()`.
- Each item displays `workflow.getDisplayName()`; the corresponding `Workflow` object is stored via `setData()`.
- If at least one workflow exists: select the first item and load its steps into the table.
- If no workflows exist: add a single disabled item `"(no workflows)"`, leave the table empty.
- On selection change: call `viewer.setInput(selectedWorkflow.getSteps())` and `updateButtonStates()`.

---

## Toolbar

Three groups separated by `SWT.SEPARATOR` items:

### Group 1 — Workflow management
| Button | Always enabled | Behaviour |
|---|---|---|
| New | yes | Logs placeholder; wired in sub-project 7 |

### Group 2 — Step management
| Button | Enabled when | Behaviour |
|---|---|---|
| Add Step | workflow selected | Appends `new Step("")` to current workflow, saves, refreshes table |
| Delete Step | step row selected | Removes selected step, saves, refreshes, calls `updateButtonStates()` |
| Move Up | step selected AND not first row | Swaps step with predecessor, saves, refreshes, keeps selection on moved row |
| Move Down | step selected AND not last row | Swaps step with successor, saves, refreshes, keeps selection on moved row |

### Group 3 — Run controls
| Button | Enabled when | Behaviour |
|---|---|---|
| Run | workflow selected AND ≥1 step | Logs placeholder; wired in sub-project 4 |
| Run Selected | step row selected | Logs placeholder; wired in sub-project 4 |
| Stop | never (for now) | Logs placeholder; wired in sub-project 4 |

`updateButtonStates()` is called after every Combo or table selection change and after every structural table mutation.

---

## Table Columns

`TableViewer` uses `ArrayContentProvider.getInstance()`. Input is `List<Step>` from the selected workflow.

### Status (40 px, fixed)

`OwnerDrawLabelProvider` — paints a 12×12 filled square centered in the cell:

| `StepStatus` | Color |
|---|---|
| `WHITE` | `#C0C0C0` (light gray) |
| `GREEN` | `#00AA00` |
| `YELLOW` | `#FFB300` (amber) |
| `RED` | `#CC0000` |

Implemented in `StepLabelProvider.paintStatusCell(Event, Step)` using `GC.fillRectangle()`. Sub-project 4 will drive live updates by calling `Display.asyncExec(() -> viewer.refresh())` — no further changes to this class needed.

### Name (flexible, fills remaining width)

`ColumnLabelProvider` returns:
```
ActionRegistry.getInstance().getAction(step.getActionId()).getName()
```
Falls back to `step.getActionId()` if the action is not registered (unknown id).

### Config (200 px, fixed)

`ColumnLabelProvider` renders the step's config map as `"key=value, key=value"` (keys sorted alphabetically). If the rendered string exceeds 80 characters it is truncated to 77 characters followed by `"..."`.

---

## Data Flow

```
createPartControl()
  └─ WorkflowRepository.list()
       └─ Combo populated
            └─ [if ≥1 workflow] first item selected → viewer.setInput(steps)

Combo selectionChanged
  └─ viewer.setInput(selectedWorkflow.getSteps())
  └─ updateButtonStates()

Add Step
  └─ currentWorkflow.getSteps().add(new Step(""))
  └─ WorkflowRepository.save(currentWorkflow)
  └─ viewer.refresh()

Delete Step
  └─ currentWorkflow.getSteps().remove(selectedIndex)
  └─ WorkflowRepository.save(currentWorkflow)
  └─ viewer.refresh()
  └─ updateButtonStates()

Move Up / Move Down
  └─ Collections.swap(steps, i, i±1)
  └─ WorkflowRepository.save(currentWorkflow)
  └─ viewer.refresh()
  └─ table selection follows moved row
```

---

## Files Changed

| File | Change |
|---|---|
| `com.example.automation/src/main/java/com/example/automation/AutomationView.java` | Rewritten — full UI, toolbar, viewer wiring |
| `com.example.automation/src/main/java/com/example/automation/StepLabelProvider.java` | New — Status/Name/Config column rendering |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/AutomationViewTest.java` | New — SWTBot UI tests |

---

## Test Coverage

### `AutomationViewTest` (SWTBot, existing Eclipse UI harness)

Extends the existing test infrastructure (`useUIHarness=true`, `useUIThread=false`).

| Test | Verifies |
|---|---|
| `comboIsVisible` | The workflow Combo widget is visible in the view |
| `toolbarButtonsAreVisible` | All 9 toolbar buttons are present by tooltip text |
| `tableHasThreeColumns` | Table has columns: Status, Name, Config |
| `selectingWorkflowShowsSteps` | Creating a workflow with one step and selecting it shows the step in the table |

The three existing tests in `AutomationPluginUITest` remain unchanged.

---

## What Is NOT in Scope

- Workflow execution (Run / Run Selected / Stop wiring) — sub-project 4
- Progress bar in Status column during execution — sub-project 4
- Properties View on row selection — sub-project 6
- Double-click to open Console — sub-project 5/7
- New Workflow dialog — sub-project 7
- Built-in action names resolving correctly — sub-project 8
