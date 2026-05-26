# New Workflow Dialog — Design Spec

**Date:** 2026-05-26
**Status:** Approved
**Sub-project:** 7 of 8 (Workflow Automation)

---

## Overview

The "New Workflow" toolbar button in `AutomationView` currently logs a stub message. This sub-project replaces it with a `TitleAreaDialog` that collects a display name and optional description, auto-derives a unique workflow ID, saves the workflow, and selects it in the combo.

---

## Architecture

Two files change:

| File | Change |
|---|---|
| `com.example.automation/…/NewWorkflowDialog.java` | **New.** `TitleAreaDialog` subclass — display name field, description field, inline validation, ID generation |
| `com.example.automation/…/AutomationView.java` | **Modified.** `onNew()` opens the dialog; on OK, saves the new workflow, reloads the combo, selects the new entry |

`NewWorkflowDialog` is decoupled from `AutomationView`: it receives a `Set<String>` of existing workflow IDs for uniqueness checking and exposes `getResult()` returning a ready-to-save `Workflow`. All persistence stays in `AutomationView.onNew()`. No new OSGi dependencies — `org.eclipse.jface` is already in `Require-Bundle`.

---

## NewWorkflowDialog

### Constructor

```java
public NewWorkflowDialog(Shell parent, Set<String> existingIds)
```

`existingIds` is used solely for uniqueness checking during ID derivation.

### Layout

- **Title area:** title "New Workflow", no custom icon
- **Name field** (required `Text`, single line) — label "Name:". As the user types, a read-only label below the field shows the derived ID in grey: `ID: my-workflow`. OK is disabled while the field is empty.
- **Description field** (optional `Text`, 3 lines, multi-line) — label "Description:". No validation.

### ID Generation

Package-private static method, separately testable:

```java
static String deriveId(String displayName, Set<String> existingIds)
```

**Algorithm:**
1. Lowercase the display name
2. Replace runs of non-alphanumeric characters with a single hyphen
3. Strip leading and trailing hyphens
4. If the result is already in `existingIds`, append `-2`, `-3`, … until unique

**Examples:**

| Display name | Existing IDs | Result |
|---|---|---|
| `"My Workflow"` | `{}` | `my-workflow` |
| `"Build & Release!!"` | `{}` | `build-release` |
| `"  !!hello!!"` | `{}` | `hello` |
| `"My Workflow"` | `{"my-workflow"}` | `my-workflow-2` |
| `"My Workflow"` | `{"my-workflow","my-workflow-2"}` | `my-workflow-3` |

### getResult()

```java
public Workflow getResult()
```

Returns the `Workflow(id, displayName, description)` constructed on OK. Returns `null` if the dialog was cancelled or not yet opened.

### Validation

- OK button is disabled while the Name field is empty.
- No other validation is needed: ID conflicts are resolved by auto-suffix; the description is optional.
- The `TitleAreaDialog` message area shows a prompt while the name is empty: `"Enter a display name."` It clears once the user starts typing.

---

## AutomationView Changes

### onNew()

Replaces the current log stub:

```java
private void onNew() {
    Set<String> existingIds = workflows.stream()
        .map(Workflow::getWorkflowId)
        .collect(Collectors.toSet());
    NewWorkflowDialog dialog = new NewWorkflowDialog(getSite().getShell(), existingIds);
    if (dialog.open() == Window.OK) {
        saveNew(dialog.getResult());
    }
}
```

### saveNew(Workflow)

New private helper — saves a freshly created workflow and refreshes the view:

```java
private void saveNew(Workflow wf) {
    try {
        new WorkflowRepository().save(wf);
    } catch (Exception e) {
        Platform.getLog(getClass()).error("Failed to save new workflow", e);
        return;
    }
    loadWorkflows();
    // Select the newly created workflow in the combo
    for (int i = 0; i < workflows.size(); i++) {
        if (wf.getWorkflowId().equals(workflows.get(i).getWorkflowId())) {
            workflowCombo.select(i);
            currentWorkflow = workflows.get(i);
            viewer.setInput(currentWorkflow.getSteps());
            break;
        }
    }
    updateButtonStates();
}
```

The existing `save()` method (saves `currentWorkflow`) is unchanged.

---

## Test Coverage

### NewWorkflowDialogTest (new, plain JUnit 4 — no Eclipse runtime)

Tests target `NewWorkflowDialog.deriveId(String, Set<String>)` directly.

| Test | Verifies |
|---|---|
| `deriveId_basic` | `"My Workflow"` → `"my-workflow"` |
| `deriveId_specialChars` | `"Build & Release!!"` → `"build-release"` |
| `deriveId_leadingTrailingSpecial` | `"  !!hello!!"` → `"hello"` |
| `deriveId_conflict` | existing `{"my-workflow"}` + name `"My Workflow"` → `"my-workflow-2"` |
| `deriveId_multipleConflicts` | existing `{"my-workflow","my-workflow-2"}` + name `"My Workflow"` → `"my-workflow-3"` |

### AutomationViewTest (no new test)

Dialog interaction via SWTBot requires opening a shell and simulating keyboard input — fragile and adds little coverage over the unit tests above.

---

## Data Flow

```
User clicks "New Workflow"
  └─ AutomationView.onNew()
       └─ collects existing workflow IDs from this.workflows
       └─ opens NewWorkflowDialog(shell, existingIds)

User types display name
  └─ NewWorkflowDialog derives ID live: deriveId(name, existingIds)
  └─ updates ID label; enables/disables OK button

User clicks OK
  └─ NewWorkflowDialog.getResult() → Workflow(id, name, description)
  └─ AutomationView.saveNew(wf)
       └─ WorkflowRepository.save(wf)
       └─ loadWorkflows() — refreshes this.workflows and combo
       └─ selects new workflow in combo, sets currentWorkflow
       └─ updateButtonStates()
```

---

## What Is NOT in Scope

- Editing an existing workflow's name or description
- Deleting workflows
- Built-in action implementations — sub-project 8
- Validation of the derived ID format beyond the auto-suffix rule
- Custom dialog icons
