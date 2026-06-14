# Test Coverage Increase to ~100% — Design Spec

## Goal

Bring line/class coverage from ~50% to as close to 100% as feasible by adding unit tests
for all pure-Java logic and SWTBot tests for all testable UI flows. The only code
intentionally excluded is OSGi infrastructure that has no testable logic (see §Exclusions).

## Architecture

Three layers of tests, already supported by the Tycho Surefire `eclipse-test-plugin` harness:

1. **Pure unit tests** — no display or OSGi platform access required.
   `StepLabelProvider`, `Workflow`, `Step`, and the missing `getId`/`getName` method tests
   on action classes all fall here.

2. **SWT-aware unit tests** — need `Display.getDefault()` but not a workbench.
   `MultiLineTextCellEditor` and `ProjectComboBoxCellEditor` are tested by constructing
   them directly inside `Display.getDefault().syncExec()`.

3. **SWTBot UI tests** — need a running workbench (already provided).
   New workflows, step add/delete/move, run-workflow status, and the Properties view.

## Tech Stack

JUnit 4 · SWTBot (`SWTWorkbenchBot`) · Eclipse OSGi test harness
(`eclipse-test-plugin`, `useUIHarness=true`, `useUIThread=false`)

---

## File Structure

### New files (8)

| File | Package | Layer |
|---|---|---|
| `StepLabelProviderTest.java` | `com.example.automation.tests` | Pure unit |
| `WorkflowTest.java` | `com.example.automation.tests` | Pure unit |
| `StepTest.java` | `com.example.automation.tests` | Pure unit |
| `MultiLineTextCellEditorTest.java` | `com.example.automation.tests` | SWT-aware |
| `ProjectComboBoxCellEditorTest.java` | `com.example.automation.tests` | SWT-aware |
| `NewWorkflowDialogUITest.java` | `com.example.automation.tests` | SWTBot |
| `StepManagementTest.java` | `com.example.automation.tests` | SWTBot |
| `RunWorkflowTest.java` | `com.example.automation.tests` | SWTBot |

### Modified files (13)

| File | Additions |
|---|---|
| `WorkflowRepositoryTest.java` | 5 edge-case tests |
| `ShellCommandActionTest.java` | `getId`, `getName` |
| `WriteFileActionTest.java` | `getId`, `getName` |
| `SetMavenSettingsTest.java` | `getId`, `getName` |
| `MavenRunWithProgressActionTest.java` | `getId`, `getName` |
| `SetActiveTargetPlatformActionTest.java` | `getName` (has `getId` already) |
| `ImportMavenProjectActionTest.java` | `getId`, `getName` |
| `SetCodeFormatterActionTest.java` | `getId`, `getName` |
| `SetSaveActionsActionTest.java` | `getId`, `getName` |
| `ExecuteRunConfigActionTest.java` | `getId`, `getName` |
| `GitCheckoutActionTest.java` | `getId`, `getName` |
| `GitCloneActionTest.java` | `getId`, `getName` |
| `MavenUpdateProjectActionTest.java` | `getId`, `getName`, `validate_accepts` |

---

## Tests — New Files

### `StepLabelProviderTest.java`

Covers `StepLabelProvider.Config.getText()` and `StepLabelProvider.Name.getText()`.
Neither method needs a viewer or display context — they can be called directly.

```java
// Config_emptyConfig_returnsEmpty
new StepLabelProvider.Config().getText(stepWithConfig(Map.of())) → ""

// Config_singleEntry_returnsKeyEqualsValue
stepWithConfig(Map.of("cmd", "echo")) → "cmd=echo"

// Config_multipleEntries_sortedAlphabeticallyByKey
stepWithConfig(Map.of("z","1","a","2")) → "a=2, z=1"

// Config_longText_truncatedAt80WithEllipsis
step whose formatted text exceeds 80 chars → result.length() == 80 && result.endsWith("...")

// Name_customNameSet_returnsCustomName
step.setName("My Step") → "My Step"  (even if actionId not in registry)

// Name_noCustomName_unknownAction_returnsActionId
step = new Step("nonexistent.xyz"), no name set → "nonexistent.xyz"
```

### `WorkflowTest.java`

```java
// constructor_setsWorkflowId
new Workflow("wf-id","Name","Desc").getWorkflowId() == "wf-id"

// constructor_setsDisplayName
// constructor_setsDescription
// constructor_initializesEmptyStepList
new Workflow(...).getSteps().isEmpty()

// setSteps_replacesStepList
wf.setSteps(newList); wf.getSteps() == newList
```

### `StepTest.java`

```java
// constructor_setsActionId
new Step("my.action").getActionId() == "my.action"

// defaultStatus_isWhite
new Step("x").getStatus() == StepStatus.WHITE

// defaultProgress_isZero
new Step("x").getProgress() == 0

// setName_getNameRoundTrip
step.setName("foo"); step.getName() == "foo"

// setStatus_getStatusRoundTrip
step.setStatus(StepStatus.GREEN); step.getStatus() == StepStatus.GREEN

// setProgress_getProgressRoundTrip
step.setProgress(42); step.getProgress() == 42
```

### `MultiLineTextCellEditorTest.java`

Tests `MultiLineTextCellEditor` (extends `DialogCellEditor`) via Display.syncExec.
A temporary Shell is used as parent; disposed immediately after.

```java
// setValue_getValue_roundTrip
// Given: editor created on temporary Shell
// When:  editor.setValue("hello\nworld")
// Then:  editor.getValue() == "hello\nworld"

// setValue_null_treatedAsNull
// setValue(null); getValue() returns null or empty without throwing
```

### `ProjectComboBoxCellEditorTest.java`

Creates a workspace project in `@Before`, disposes in `@After`.
Editor is constructed in syncExec; the underlying `Combo` is accessed via
`(Combo) editor.getControl()`.

```java
// comboListsOpenWorkspaceProjects
// @Before: create + open project "TestProject_ComboEditor"
// editor = new ProjectComboBoxCellEditor(shell)
// ((Combo) editor.getControl()).getItems() contains "TestProject_ComboEditor"

// setValue_getValue_roundTrip
// editor.setValue("TestProject_ComboEditor")
// editor.getValue() == "TestProject_ComboEditor"

// setValue_customTextNotInList_stillRoundTrips
// editor.setValue("not-in-list")
// editor.getValue() == "not-in-list"
```

### `NewWorkflowDialogUITest.java`

```java
@BeforeClass: bot = new SWTWorkbenchBot(); open/show Automation view

// newWorkflowButton_opensDialogWithTitle
// toolbarButton("New Workflow").click() → bot.shell("New Workflow") visible

// dialog_okDisabledWhenNameBlank
// open dialog; OK button disabled when name field empty

// dialog_createWorkflow_appearsInPicker
// enter name "UITest-WF", click OK
// click "Open Workflow" → picker table contains "UITest-WF"
// @After: repo.delete("uitest-wf")

// dialog_cancel_doesNotCreateWorkflow
// open dialog, click Cancel → picking dialog no longer has "uitest-wf" row
```

### `StepManagementTest.java`

`@BeforeClass`: Creates a test workflow "step-mgmt-test-wf" (no steps), loads it in the view.
`@AfterClass`: Deletes the workflow.

Each test reopens the view fresh via `viewById(...).close()` + menu to ensure clean state.

```java
// addStep_addsRowToTable
// click "Add Step" → dialog opens
// select first row in picker → click OK
// table in Automation view has 1 row

// deleteStep_withStepSelected_removesRow
// add a step (reuse addStep logic), select table row 0, click "Delete Step"
// table has 0 rows

// moveStepUp_reordersSteps
// add step A, add step B (two rows: A then B)
// select row 1 (B), click "Move Step Up"
// row 0 is now B, row 1 is A

// moveStepDown_reordersSteps
// add step A, add step B
// select row 0 (A), click "Move Step Down"
// row 0 is now B, row 1 is A
```

### `RunWorkflowTest.java`

Uses a workflow containing one "refresh-all" step (always available, no external deps).

```java
@BeforeClass:
  - create workflow "run-workflow-test-wf" with Step("refresh-all")
  - open Automation view, load the workflow

// runWorkflow_setsStatusGreenOnSuccess
// click "Run Workflow" toolbar button
// wait up to 10 s for table row 0 status column to become GREEN
// assert table row 0 status shows green (via cell text or color marker via syncExec)

@AfterClass: delete workflow
```

Note: status is read via `Display.getDefault().syncExec` inspecting `table.widget.getItem(0)`.

---

## Tests — Modified Files

### `WorkflowRepositoryTest.java` — add 5 tests

```java
// delete_nonExistent_returnsFalse
assertFalse(repo().delete("never-saved"))

// save_blankId_throwsIllegalArgument
assertThrows(IllegalArgumentException.class, () -> repo().save(new Workflow("", "x", "")))

// save_idWithSlash_throwsIllegalArgument
assertThrows(IAE, () -> repo().save(new Workflow("a/b", "x", "")))

// load_nonExistent_throwsIOException
assertThrows(IOException.class, () -> repo().load("no-such-wf"))

// list_emptyDir_returnsEmpty
assertEquals(0, repo().list().size())
```

### Action test files — `getId` and `getName` additions

For each file below, add two tests following this pattern:
```java
@Test public void getId_returnsExpectedId() {
    assertEquals("<id>", new XxxAction().getId());
}
@Test public void getName_returnsExpectedName() {
    assertEquals("<name>", new XxxAction().getName());
}
```

| Test file | Expected `getId()` | Expected `getName()` |
|---|---|---|
| `ShellCommandActionTest` | `"shell-command"` | `"Shell Command"` |
| `WriteFileActionTest` | `"write-file"` | `"Write File"` |
| `SetMavenSettingsTest` | `"set-maven-settings"` | `"Set Maven Settings"` |
| `MavenRunWithProgressActionTest` | `"maven-run-with-progress"` | `"Maven Run with Progress"` |
| `SetActiveTargetPlatformActionTest` | *(already has getId)* | `"Set Active Target Platform"` |
| `ImportMavenProjectActionTest` | `"import-maven-project"` | `"Import Maven Project"` |
| `SetCodeFormatterActionTest` | `"set-code-formatter"` | `"Set Code Formatter"` |
| `SetSaveActionsActionTest` | `"set-save-actions"` | `"Set Save Actions"` |
| `ExecuteRunConfigActionTest` | `"execute-run-config"` | `"Execute Run Configuration"` |
| `GitCheckoutActionTest` | `"git-checkout"` | `"Git Checkout"` |
| `GitCloneActionTest` | `"git-clone"` | `"Git Clone"` |
| `MavenUpdateProjectActionTest` | `"maven-update-project"` | `"Maven Update Project"` |

`MavenUpdateProjectActionTest` also gets:
```java
// validate_acceptsNonBlankProjectName
// validate() with projectName="MyProject" returns no "projectName" error
// (M2E error may still appear if M2E not active; filter for "projectName" only)
List<String> errors = new MavenUpdateProjectAction().validate(Map.of("projectName", "MyProject"));
assertFalse(errors.stream().anyMatch(e -> e.contains("projectName")));
```

---

## Exclusions (untestable without mocking OSGi internals)

| Class | Reason |
|---|---|
| `Activator` | OSGi `BundleActivator` lifecycle; no testable logic |
| `BundledWorkflowInstaller` | Calls `Platform.getBundle()` to read bundled resources; no pure logic |
| `ShowAutomationViewHandler` | Eclipse `IHandler` that calls `IWorkbenchPage.showView()` |
| `StepAdapterFactory` | Eclipse `IAdapterFactory`; just delegates to `StepPropertySource` (already tested) |
| `AutomationPreferences.store()` | Delegates to `Activator.getDefault().getPreferenceStore()`; covered indirectly by `VariableSubstitutionTest` |
| `AutomationPreferenceInitializer` | Calls `getPreferenceStore().setDefault()`; trivial, covered indirectly by `PreferencePageTest` |
| `ProcessRunner` | Package-private class; not accessible cross-bundle; line coverage provided indirectly through integration tests of actions that call it |

---

## Spec Self-Review

- No TBDs or placeholders.
- All assertion values verified against source files.
- `MavenUpdateProjectAction.validate()` test accounts for M2E-not-active error (filters for "projectName" only).
- `RunWorkflowTest` uses syncExec to read table widget state to avoid relying on text rendering of the status column.
- New SWTBot tests use `@AfterClass` cleanup to delete test workflows, following the existing pattern in `AutomationViewTest`.
- No scope creep: `ProcessRunner`, `BundledWorkflowInstaller`, and other OSGi-bound classes are explicitly excluded with rationale.
