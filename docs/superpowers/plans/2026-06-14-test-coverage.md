# Test Coverage Increase to ~100% — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ~60 new unit and SWTBot tests across 8 new files and 13 modified files to bring coverage from ~50% to ~100% on all non-OSGi-infrastructure code.

**Architecture:** Three test layers — pure unit tests (no display), SWT-aware tests (`Display.syncExec` with temporary shells), and SWTBot UI tests running in the Eclipse test harness. All tests go in the existing `com.example.automation.tests` bundle. Pure unit tests run without a workbench; SWTBot tests require `useUIHarness=true` (already configured).

**Tech Stack:** JUnit 4 · SWTBot `SWTWorkbenchBot` · Eclipse OSGi test harness (Tycho Surefire `eclipse-test-plugin`)

**Root command to run all tests:**
```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am
```

**Command to run a single test class:**
```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am -Dtest=<ClassName>
```

All test files go in:
`com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/`

---

## Task 1: StepLabelProvider pure unit tests

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepLabelProviderTest.java`

`StepLabelProvider.Config.getText()` sorts entries by key, formats as `key=value`, truncates to 80 chars with `...`. `StepLabelProvider.Name.getText()` returns the custom name if set, otherwise falls back to the action display name from the registry, then to the raw actionId. Both can be called directly — no viewer or display needed.

- [ ] **Step 1: Create the test file**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import java.util.Map;
import org.junit.Test;
import com.example.automation.StepLabelProvider;
import com.example.automation.model.Step;

public class StepLabelProviderTest {

    @Test
    public void Config_emptyConfig_returnsEmpty() {
        assertEquals("", new StepLabelProvider.Config().getText(new Step("a")));
    }

    @Test
    public void Config_singleEntry_returnsKeyEqualsValue() {
        Step step = new Step("a");
        step.getConfig().put("cmd", "echo hi");
        assertEquals("cmd=echo hi", new StepLabelProvider.Config().getText(step));
    }

    @Test
    public void Config_multipleEntries_sortedAlphabeticallyByKey() {
        Step step = new Step("a");
        step.getConfig().put("z", "1");
        step.getConfig().put("a", "2");
        assertEquals("a=2, z=1", new StepLabelProvider.Config().getText(step));
    }

    @Test
    public void Config_longText_truncatedAt80WithEllipsis() {
        Step step = new Step("a");
        step.getConfig().put("key", "x".repeat(100));
        String result = new StepLabelProvider.Config().getText(step);
        assertEquals(80, result.length());
        assertTrue(result.endsWith("..."));
    }

    @Test
    public void Name_customNameSet_returnsCustomName() {
        Step step = new Step("nonexistent.xyz");
        step.setName("My Custom Step");
        assertEquals("My Custom Step", new StepLabelProvider.Name().getText(step));
    }

    @Test
    public void Name_noCustomName_unknownAction_returnsActionId() {
        Step step = new Step("nonexistent.action.xyz.abc.123");
        // No name set, action not in registry → falls back to actionId
        assertEquals("nonexistent.action.xyz.abc.123",
            new StepLabelProvider.Name().getText(step));
    }
}
```

- [ ] **Step 2: Run to verify all 6 tests pass**

```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am -Dtest=StepLabelProviderTest
```

Expected: BUILD SUCCESS, 6 tests pass.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepLabelProviderTest.java
git commit -m "test: add StepLabelProvider Config and Name unit tests"
```

---

## Task 2: Workflow and Step model unit tests

**Files:**
- Create: `.../tests/WorkflowTest.java`
- Create: `.../tests/StepTest.java`

`Workflow` and `Step` have only getters/setters with no logic. Tests verify the constructor sets all fields and that setters round-trip correctly.

- [ ] **Step 1: Create WorkflowTest.java**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;
import com.example.automation.model.Step;
import com.example.automation.model.Workflow;

public class WorkflowTest {

    @Test
    public void constructor_setsWorkflowId() {
        assertEquals("my-id", new Workflow("my-id", "Name", "Desc").getWorkflowId());
    }

    @Test
    public void constructor_setsDisplayName() {
        assertEquals("Name", new Workflow("id", "Name", "Desc").getDisplayName());
    }

    @Test
    public void constructor_setsDescription() {
        assertEquals("Desc", new Workflow("id", "Name", "Desc").getDescription());
    }

    @Test
    public void constructor_initializesEmptyStepList() {
        assertTrue(new Workflow("id", "Name", "Desc").getSteps().isEmpty());
    }

    @Test
    public void setSteps_replacesStepList() {
        Workflow wf = new Workflow("id", "Name", "Desc");
        List<Step> steps = List.of(new Step("a"));
        wf.setSteps(steps);
        assertSame(steps, wf.getSteps());
    }

    @Test
    public void setDisplayName_updatesValue() {
        Workflow wf = new Workflow("id", "Old", "Desc");
        wf.setDisplayName("New");
        assertEquals("New", wf.getDisplayName());
    }

    @Test
    public void setDescription_updatesValue() {
        Workflow wf = new Workflow("id", "Name", "Old");
        wf.setDescription("New");
        assertEquals("New", wf.getDescription());
    }
}
```

- [ ] **Step 2: Create StepTest.java**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;

public class StepTest {

    @Test
    public void constructor_setsActionId() {
        assertEquals("my.action", new Step("my.action").getActionId());
    }

    @Test
    public void defaultStatus_isWhite() {
        assertEquals(StepStatus.WHITE, new Step("a").getStatus());
    }

    @Test
    public void defaultProgress_isZero() {
        assertEquals(0, new Step("a").getProgress());
    }

    @Test
    public void defaultConfig_isEmptyMap() {
        assertNotNull(new Step("a").getConfig());
        assertTrue(new Step("a").getConfig().isEmpty());
    }

    @Test
    public void setName_getNameRoundTrip() {
        Step step = new Step("a");
        step.setName("My Step");
        assertEquals("My Step", step.getName());
    }

    @Test
    public void setStatus_getStatusRoundTrip() {
        Step step = new Step("a");
        step.setStatus(StepStatus.GREEN);
        assertEquals(StepStatus.GREEN, step.getStatus());
    }

    @Test
    public void setProgress_getProgressRoundTrip() {
        Step step = new Step("a");
        step.setProgress(42);
        assertEquals(42, step.getProgress());
    }

    @Test
    public void setConfig_replacesConfig() {
        Step step = new Step("a");
        Map<String, String> cfg = new HashMap<>();
        cfg.put("key", "val");
        step.setConfig(cfg);
        assertSame(cfg, step.getConfig());
    }
}
```

- [ ] **Step 3: Run both new test classes**

```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am -Dtest=WorkflowTest+StepTest
```

Expected: BUILD SUCCESS, 15 tests pass.

- [ ] **Step 4: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepTest.java
git commit -m "test: add Workflow and Step model unit tests"
```

---

## Task 3: WorkflowRepository edge case tests

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRepositoryTest.java`

Add 5 tests after the existing `deleteRemovesFile` test. The `requireValidId` method throws `IllegalArgumentException` for blank IDs and IDs containing `/`, `\`, or `..`. `delete` returns `false` for non-existent files. `load` throws `IOException` for missing files.

- [ ] **Step 1: Add the 5 tests to WorkflowRepositoryTest.java**

Add these methods inside the `WorkflowRepositoryTest` class, after the existing `deleteRemovesFile` method:

```java
    @Test
    public void delete_nonExistent_returnsFalse() {
        assertFalse(repo().delete("never-saved-workflow"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void save_blankId_throwsIllegalArgument() throws Exception {
        repo().save(new Workflow("", "Name", "desc"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void save_idWithSlash_throwsIllegalArgument() throws Exception {
        repo().save(new Workflow("a/b", "Name", "desc"));
    }

    @Test(expected = java.io.IOException.class)
    public void load_nonExistent_throwsIOException() throws Exception {
        repo().load("no-such-workflow-xyz");
    }

    @Test
    public void list_emptyDir_returnsEmpty() throws Exception {
        assertTrue(repo().list().isEmpty());
    }
```

Note: `java.io.IOException` must be imported or used fully-qualified. Check the existing imports in the file — if `IOException` is not already imported, add `import java.io.IOException;` at the top.

- [ ] **Step 2: Run WorkflowRepositoryTest to verify all tests pass (old + new)**

```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am -Dtest=WorkflowRepositoryTest
```

Expected: BUILD SUCCESS, 12 tests pass.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRepositoryTest.java
git commit -m "test: add WorkflowRepository edge case tests"
```

---

## Task 4: Action getId and getName tests (all 12 files)

**Files (all modify):**
- `ShellCommandActionTest.java`
- `WriteFileActionTest.java`
- `SetMavenSettingsTest.java`
- `MavenRunWithProgressActionTest.java`
- `SetActiveTargetPlatformActionTest.java` (only getName — getId already present)
- `ImportMavenProjectActionTest.java`
- `SetCodeFormatterActionTest.java`
- `SetSaveActionsActionTest.java`
- `ExecuteRunConfigActionTest.java`
- `GitCheckoutActionTest.java`
- `GitCloneActionTest.java`
- `MavenUpdateProjectActionTest.java`

Add two tests to the bottom of each class. The pattern is identical for every file.

- [ ] **Step 1: Add to ShellCommandActionTest.java**

```java
    @Test
    public void getId_returnsShellCommand() {
        assertEquals("shell-command", new ShellCommandAction().getId());
    }

    @Test
    public void getName_returnsShellCommand() {
        assertEquals("Shell Command", new ShellCommandAction().getName());
    }
```

- [ ] **Step 2: Add to WriteFileActionTest.java**

```java
    @Test
    public void getId_returnsWriteFile() {
        assertEquals("write-file", new WriteFileAction().getId());
    }

    @Test
    public void getName_returnsWriteFile() {
        assertEquals("Write File", new WriteFileAction().getName());
    }
```

- [ ] **Step 3: Add to SetMavenSettingsTest.java**

```java
    @Test
    public void getId_returnsSetMavenSettings() {
        assertEquals("set-maven-settings", new SetMavenSettingsAction().getId());
    }

    @Test
    public void getName_returnsSetMavenSettings() {
        assertEquals("Set Maven Settings", new SetMavenSettingsAction().getName());
    }
```

- [ ] **Step 4: Add to MavenRunWithProgressActionTest.java**

```java
    @Test
    public void getId_returnsMavenRunWithProgress() {
        assertEquals("maven-run-with-progress", new MavenRunWithProgressAction().getId());
    }

    @Test
    public void getName_returnsMavenRunWithProgress() {
        assertEquals("Maven Run with Progress", new MavenRunWithProgressAction().getName());
    }
```

- [ ] **Step 5: Add to SetActiveTargetPlatformActionTest.java** (getName only)

```java
    @Test
    public void getName_returnsSetActiveTargetPlatform() {
        assertEquals("Set Active Target Platform", new SetActiveTargetPlatformAction().getName());
    }
```

- [ ] **Step 6: Add to ImportMavenProjectActionTest.java**

```java
    @Test
    public void getId_returnsImportMavenProject() {
        assertEquals("import-maven-project", new ImportMavenProjectAction().getId());
    }

    @Test
    public void getName_returnsImportMavenProject() {
        assertEquals("Import Maven Project", new ImportMavenProjectAction().getName());
    }
```

- [ ] **Step 7: Add to SetCodeFormatterActionTest.java**

```java
    @Test
    public void getId_returnsSetCodeFormatter() {
        assertEquals("set-code-formatter", new SetCodeFormatterAction().getId());
    }

    @Test
    public void getName_returnsSetCodeFormatter() {
        assertEquals("Set Code Formatter", new SetCodeFormatterAction().getName());
    }
```

- [ ] **Step 8: Add to SetSaveActionsActionTest.java**

```java
    @Test
    public void getId_returnsSetSaveActions() {
        assertEquals("set-save-actions", new SetSaveActionsAction().getId());
    }

    @Test
    public void getName_returnsSetSaveActions() {
        assertEquals("Set Save Actions", new SetSaveActionsAction().getName());
    }
```

- [ ] **Step 9: Add to ExecuteRunConfigActionTest.java**

```java
    @Test
    public void getId_returnsExecuteRunConfig() {
        assertEquals("execute-run-config", new ExecuteRunConfigAction().getId());
    }

    @Test
    public void getName_returnsExecuteRunConfig() {
        assertEquals("Execute Run Configuration", new ExecuteRunConfigAction().getName());
    }
```

- [ ] **Step 10: Add to GitCheckoutActionTest.java**

```java
    @Test
    public void getId_returnsGitCheckout() {
        assertEquals("git-checkout", new GitCheckoutAction().getId());
    }

    @Test
    public void getName_returnsGitCheckout() {
        assertEquals("Git Checkout", new GitCheckoutAction().getName());
    }
```

- [ ] **Step 11: Add to GitCloneActionTest.java**

```java
    @Test
    public void getId_returnsGitClone() {
        assertEquals("git-clone", new GitCloneAction().getId());
    }

    @Test
    public void getName_returnsGitClone() {
        assertEquals("Git Clone", new GitCloneAction().getName());
    }
```

- [ ] **Step 12: Add to MavenUpdateProjectActionTest.java** (getId, getName, plus validate_accepts)

```java
    @Test
    public void getId_returnsMavenUpdateProject() {
        assertEquals("maven-update-project", new MavenUpdateProjectAction().getId());
    }

    @Test
    public void getName_returnsMavenUpdateProject() {
        assertEquals("Maven Update Project", new MavenUpdateProjectAction().getName());
    }

    @Test
    public void validate_acceptsNonBlankProjectName() {
        // M2E may or may not be active; only check there is no "projectName" error
        boolean projectNameError = new MavenUpdateProjectAction()
            .validate(java.util.Map.of("projectName", "MyProject"))
            .stream().anyMatch(e -> e.contains("projectName"));
        assertFalse(projectNameError);
    }
```

- [ ] **Step 13: Run all 12 modified test files**

```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am -Dtest=ShellCommandActionTest+WriteFileActionTest+SetMavenSettingsTest+MavenRunWithProgressActionTest+SetActiveTargetPlatformActionTest+ImportMavenProjectActionTest+SetCodeFormatterActionTest+SetSaveActionsActionTest+ExecuteRunConfigActionTest+GitCheckoutActionTest+GitCloneActionTest+MavenUpdateProjectActionTest
```

Expected: BUILD SUCCESS.

- [ ] **Step 14: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ShellCommandActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WriteFileActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetMavenSettingsTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetActiveTargetPlatformActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ImportMavenProjectActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetCodeFormatterActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetSaveActionsActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ExecuteRunConfigActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitCheckoutActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitCloneActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenUpdateProjectActionTest.java
git commit -m "test: add getId/getName tests for all action classes"
```

---

## Task 5: MultiLineTextCellEditor SWT-aware test

**Files:**
- Create: `.../tests/MultiLineTextCellEditorTest.java`

`MultiLineTextCellEditor extends DialogCellEditor`. `DialogCellEditor` stores the value in a `value` field, accessible via `getValue()`/`setValue()`. The editor requires a parent `Composite` to construct (it creates an SWT button widget). Use `Display.getDefault().syncExec()` with a temporary `Shell` as parent — this works because the test harness runs with `useUIHarness=true` so a Display is always available.

- [ ] **Step 1: Create MultiLineTextCellEditorTest.java**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.Test;
import com.example.automation.MultiLineTextCellEditor;

public class MultiLineTextCellEditorTest {

    @Test
    public void setValue_getValue_roundTrip() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            MultiLineTextCellEditor editor = new MultiLineTextCellEditor(shell);
            editor.setValue("hello\nworld");
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals("hello\nworld", result[0]);
    }

    @Test
    public void setValue_emptyString_roundTrips() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            MultiLineTextCellEditor editor = new MultiLineTextCellEditor(shell);
            editor.setValue("");
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals("", result[0]);
    }

    @Test
    public void setValue_multiLineWithTabs_roundTrips() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            MultiLineTextCellEditor editor = new MultiLineTextCellEditor(shell);
            editor.setValue("line1\n\tline2\n\tline3");
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals("line1\n\tline2\n\tline3", result[0]);
    }
}
```

- [ ] **Step 2: Run the test**

```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am -Dtest=MultiLineTextCellEditorTest
```

Expected: BUILD SUCCESS, 3 tests pass.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MultiLineTextCellEditorTest.java
git commit -m "test: add MultiLineTextCellEditor SWT value-storage tests"
```

---

## Task 6: ProjectComboBoxCellEditor SWT-aware test

**Files:**
- Create: `.../tests/ProjectComboBoxCellEditorTest.java`

`ProjectComboBoxCellEditor extends CellEditor`. Its `createControl()` creates a `Combo` pre-populated with workspace projects. `CellEditor.getControl()` returns that `Combo`. Tests create a workspace project in `@Before`, dispose it in `@After`, and construct the editor inside `syncExec`.

- [ ] **Step 1: Create ProjectComboBoxCellEditorTest.java**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.example.automation.ProjectComboBoxCellEditor;

public class ProjectComboBoxCellEditorTest {

    private static final String PROJECT_NAME = "TestProject_ComboEditor";
    private IProject project;

    @Before
    public void createProject() throws Exception {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
        if (!project.exists()) project.create(new NullProgressMonitor());
        if (!project.isOpen()) project.open(new NullProgressMonitor());
    }

    @After
    public void deleteProject() throws Exception {
        if (project != null && project.exists())
            project.delete(true, new NullProgressMonitor());
    }

    @Test
    public void comboListsOpenWorkspaceProjects() {
        boolean[] found = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            ProjectComboBoxCellEditor editor = new ProjectComboBoxCellEditor(shell);
            found[0] = Arrays.asList(((Combo) editor.getControl()).getItems())
                             .contains(PROJECT_NAME);
            shell.dispose();
        });
        assertTrue("Open workspace project must appear in combo", found[0]);
    }

    @Test
    public void setValue_getValue_roundTrip() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            ProjectComboBoxCellEditor editor = new ProjectComboBoxCellEditor(shell);
            editor.setValue(PROJECT_NAME);
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals(PROJECT_NAME, result[0]);
    }

    @Test
    public void setValue_textNotInList_stillRoundTrips() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            ProjectComboBoxCellEditor editor = new ProjectComboBoxCellEditor(shell);
            editor.setValue("custom-text-not-in-list");
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals("custom-text-not-in-list", result[0]);
    }
}
```

- [ ] **Step 2: Run the test**

```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am -Dtest=ProjectComboBoxCellEditorTest
```

Expected: BUILD SUCCESS, 3 tests pass.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ProjectComboBoxCellEditorTest.java
git commit -m "test: add ProjectComboBoxCellEditor SWT tests"
```

---

## Task 7: New Workflow Dialog SWTBot test

**Files:**
- Create: `.../tests/NewWorkflowDialogUITest.java`

The "New Workflow" toolbar button opens a `TitleAreaDialog` with shell title "New Workflow". Inside it are two `Text` widgets preceded by `Label`s reading "Name:" and "Description:". OK is disabled while the name field is blank. On OK, the dialog calls `NewWorkflowDialog.deriveId(name, existingIds)` to create the workflow ID (lowercased, non-alphanumeric → hyphens).

- [ ] **Step 1: Create NewWorkflowDialogUITest.java**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

public class NewWorkflowDialogUITest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));
        try {
            bot.viewById("com.example.automation.view").show();
        } catch (WidgetNotFoundException e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @After
    public void closeDialogIfOpen() {
        try { bot.shell("New Workflow").bot().button("Cancel").click(); } catch (Exception ignored) {}
    }

    private void openFreshView() {
        try { bot.viewById("com.example.automation.view").close(); } catch (Exception ignored) {}
        bot.menu("Project").menu("Automation").click();
    }

    @Test
    public void newWorkflowButton_opensDialogWithTitle() {
        openFreshView();
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("New Workflow").click();
        assertNotNull("Shell 'New Workflow' must open", bot.shell("New Workflow"));
    }

    @Test
    public void dialog_okDisabledWhenNameBlank() {
        openFreshView();
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("New Workflow").click();
        assertFalse("OK must be disabled when name is empty",
            bot.shell("New Workflow").bot().button("OK").isEnabled());
    }

    @Test
    public void dialog_createWorkflow_appearsInPicker() throws Exception {
        openFreshView();
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("New Workflow").click();
        bot.shell("New Workflow").bot().textWithLabel("Name:").setText("UI Test Workflow");
        bot.shell("New Workflow").bot().button("OK").click();

        // The derived ID for "UI Test Workflow" is "ui-test-workflow"
        try {
            bot.viewById("com.example.automation.view").bot()
               .toolbarButtonWithTooltip("Open Workflow").click();
            SWTBotTable picker = bot.shell("Open Workflow").bot().table();
            boolean found = false;
            for (int i = 0; i < picker.rowCount(); i++) {
                if ("UI Test Workflow".equals(picker.cell(i, 0))) { found = true; break; }
            }
            bot.button("Cancel").click();
            assertTrue("Newly created workflow must appear in picker", found);
        } finally {
            repo.delete("ui-test-workflow");
        }
    }

    @Test
    public void dialog_cancel_doesNotCreateWorkflow() {
        openFreshView();
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("New Workflow").click();
        bot.shell("New Workflow").bot().textWithLabel("Name:").setText("Cancel Test Workflow");
        bot.shell("New Workflow").bot().button("Cancel").click();

        bot.viewById("com.example.automation.view").bot()
           .toolbarButtonWithTooltip("Open Workflow").click();
        SWTBotTable picker = bot.shell("Open Workflow").bot().table();
        boolean found = false;
        for (int i = 0; i < picker.rowCount(); i++) {
            if ("Cancel Test Workflow".equals(picker.cell(i, 0))) { found = true; break; }
        }
        bot.button("Cancel").click();
        assertFalse("Cancelled workflow must NOT appear in picker", found);
    }
}
```

- [ ] **Step 2: Run the test**

```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am -Dtest=NewWorkflowDialogUITest
```

Expected: BUILD SUCCESS, 4 tests pass.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/NewWorkflowDialogUITest.java
git commit -m "test: add SWTBot tests for New Workflow dialog"
```

---

## Task 8: Step management SWTBot test

**Files:**
- Create: `.../tests/StepManagementTest.java`

Tests that the Add Step, Delete Step, Move Step Up, and Move Step Down toolbar buttons work correctly. Each test resets to a fresh view with a known workflow state.

Two test fixtures are created in `@BeforeClass`:
- `step-mgmt-empty`: no steps (for add/delete tests)
- `step-mgmt-two-steps`: pre-loaded with two steps (`refresh-all` then `refresh-project`) for move tests

The Name column (index 1) shows the action's display name: "Refresh All" and "Refresh Project". Both actions are always registered in the test environment via the extension point.

- [ ] **Step 1: Create StepManagementTest.java**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.model.Step;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

public class StepManagementTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;

    private static final String WF_EMPTY      = "step-mgmt-empty";
    private static final String WF_TWO_STEPS  = "step-mgmt-two-steps";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        repo.save(new Workflow(WF_EMPTY, "Step Mgmt Empty", ""));

        Workflow twoStep = new Workflow(WF_TWO_STEPS, "Step Mgmt Two Steps", "");
        twoStep.getSteps().add(new Step("refresh-all"));
        twoStep.getSteps().add(new Step("refresh-project"));
        repo.save(twoStep);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF_EMPTY);
        repo.delete(WF_TWO_STEPS);
    }

    private void loadWorkflow(String displayName) {
        try { bot.viewById("com.example.automation.view").close(); } catch (Exception ignored) {}
        bot.menu("Project").menu("Automation").click();
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Open Workflow").click();
        SWTBotTable picker = bot.shell("Open Workflow").bot().table();
        for (int i = 0; i < picker.rowCount(); i++) {
            if (displayName.equals(picker.cell(i, 0))) { picker.click(i, 0); break; }
        }
        bot.button("OK").click();
    }

    @Test
    public void addStep_addsRowToTable() {
        loadWorkflow("Step Mgmt Empty");
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Add Step").click();
        // Select the first available action and confirm
        bot.shell("Add Step").bot().table().click(0, 0);
        bot.button("OK").click();
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals("Table must have 1 row after adding a step", 1, table.rowCount());
    }

    @Test
    public void deleteStep_withStepSelected_removesRow() {
        loadWorkflow("Step Mgmt Empty");
        // Add a step first
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Add Step").click();
        bot.shell("Add Step").bot().table().click(0, 0);
        bot.button("OK").click();

        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(1, table.rowCount());

        table.click(0, 1); // select the step
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Delete Step").click();

        assertEquals("Table must be empty after deleting the only step", 0, table.rowCount());
    }

    @Test
    public void moveStepUp_reordersSteps() {
        loadWorkflow("Step Mgmt Two Steps");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(2, table.rowCount());

        String firstName  = table.cell(0, 1); // "Refresh All"
        String secondName = table.cell(1, 1); // "Refresh Project"

        table.click(1, 1); // select row 1
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Move Step Up").click();

        assertEquals("Row 0 must be what was row 1", secondName, table.cell(0, 1));
        assertEquals("Row 1 must be what was row 0", firstName,  table.cell(1, 1));
    }

    @Test
    public void moveStepDown_reordersSteps() {
        loadWorkflow("Step Mgmt Two Steps");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(2, table.rowCount());

        String firstName  = table.cell(0, 1);
        String secondName = table.cell(1, 1);

        table.click(0, 1); // select row 0
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Move Step Down").click();

        assertEquals("Row 0 must be what was row 1", secondName, table.cell(0, 1));
        assertEquals("Row 1 must be what was row 0", firstName,  table.cell(1, 1));
    }
}
```

- [ ] **Step 2: Run the test**

```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am -Dtest=StepManagementTest
```

Expected: BUILD SUCCESS, 4 tests pass.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepManagementTest.java
git commit -m "test: add SWTBot step management tests (add, delete, move)"
```

---

## Task 9: Run Workflow SWTBot test

**Files:**
- Create: `.../tests/RunWorkflowTest.java`

Tests that clicking "Run Workflow" for a workflow containing a single `refresh-all` step results in that step's status being set to `GREEN`. `RefreshAllAction` has no external dependencies and always succeeds.

Status is verified by reading the `Step` model object from the table item's data via `Display.syncExec` — the `TableViewer` sets each item's data to the corresponding `Step` object, so `table.getItem(0).getData()` returns the `Step` instance the `WorkflowRunner` updated.

The test waits up to 10 seconds polling every 200 ms for the status to become GREEN.

- [ ] **Step 1: Create RunWorkflowTest.java**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

public class RunWorkflowTest {

    private static SWTWorkbenchBot bot;
    private static final String WF_ID = "run-workflow-test-wf";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        WorkflowRepository repo = new WorkflowRepository(new File(path));

        Workflow wf = new Workflow(WF_ID, "Run Workflow Test", "");
        wf.getSteps().add(new com.example.automation.model.Step("refresh-all"));
        repo.save(wf);

        // Open the view and load the workflow
        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Open Workflow").click();
        SWTBotTable picker = bot.shell("Open Workflow").bot().table();
        for (int i = 0; i < picker.rowCount(); i++) {
            if ("Run Workflow Test".equals(picker.cell(i, 0))) { picker.click(i, 0); break; }
        }
        bot.button("OK").click();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        new WorkflowRepository(new File(path)).delete(WF_ID);
    }

    @Test
    public void runWorkflow_withRefreshAllStep_completesWithGreenStatus() throws Exception {
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Run Workflow").click();

        // Poll up to 10 s for the step to reach GREEN status
        StepStatus[] status = {null};
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Display.getDefault().syncExec(() -> {
                org.eclipse.swt.widgets.Table t =
                    bot.viewById("com.example.automation.view").bot().table().widget;
                if (t.getItemCount() > 0) {
                    Object data = t.getItem(0).getData();
                    if (data instanceof Step) status[0] = ((Step) data).getStatus();
                }
            });
            if (StepStatus.GREEN == status[0]) break;
            Thread.sleep(200);
        }

        assertEquals("Step must reach GREEN status within 10 s", StepStatus.GREEN, status[0]);
    }
}
```

- [ ] **Step 2: Run the test**

```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am -Dtest=RunWorkflowTest
```

Expected: BUILD SUCCESS, 1 test passes.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/RunWorkflowTest.java
git commit -m "test: add SWTBot run-workflow test verifying GREEN step status"
```

---

## Task 10: Final full-suite verification

- [ ] **Step 1: Run the complete test suite**

```
mvn verify -pl com.example.automation.parent/com.example.automation.tests -am
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Confirm coverage is at or near 100% for non-excluded classes**

Classes intentionally excluded (no tests added, no coverage expected):
- `Activator`, `BundledWorkflowInstaller`, `ShowAutomationViewHandler`, `StepAdapterFactory` — OSGi infrastructure, no testable logic
- `AutomationPreferencePage`, `AutomationPreferenceInitializer` — already covered indirectly by `PreferencePageTest` / `VariableSubstitutionTest`
- `ProcessRunner` — package-private, covered indirectly by action integration tests

- [ ] **Step 3: Push to remote**

```
git push
```
