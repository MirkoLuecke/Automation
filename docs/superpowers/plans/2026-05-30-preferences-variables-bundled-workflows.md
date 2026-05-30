# Preferences, Variable Substitution, Multi-line Shell, and Bundled Workflows — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a preference page (working dir + workflow storage), Eclipse variable substitution in all step configs, multi-line shell command editor, bundled sample workflows, and complete user README.

**Architecture:** `AutomationPreferences` (static facade over IPreferenceStore) is the shared foundation; `WorkflowRunner` resolves all config values via `IStringVariableManager` before execution and injects the resolved working dir into `IActionContext`; new `MultiLineTextCellEditor` replaces the single-line Properties View editor for the ShellCommandAction `command` field; `BundledWorkflowInstaller` copies JSON resources from the plugin JAR on first launch.

**Tech Stack:** Eclipse OSGi/Tycho 3.0.5, Java 17, SWT/JFace, JUnit 4, SWTBot, `org.eclipse.core.variables` (IStringVariableManager), `org.eclipse.core.runtime.preferences` (AbstractPreferenceInitializer), `org.eclipse.ui.preferencePages`, `AbstractUIPlugin.getPreferenceStore()`.

> **Compilation note:** Tasks may reference classes created in later tasks (e.g., Task 4 references `BundledWorkflowInstaller` created in Task 9). Individual task commits are not required to compile in isolation — the full build runs once in Task 10 when all files are present. Do not attempt `mvn verify` until Task 10.

---

## File Map

| File | Action |
|---|---|
| `com.example.automation.parent/pom.xml` | Bump to `1.1.0-SNAPSHOT` |
| `com.example.automation.parent/com.example.automation/pom.xml` | Bump to `1.1.0-SNAPSHOT` |
| `com.example.automation.parent/com.example.automation.feature/pom.xml` | Bump to `1.1.0-SNAPSHOT` |
| `com.example.automation.parent/com.example.automation.site/pom.xml` | Bump to `1.1.0-SNAPSHOT` |
| `com.example.automation.parent/com.example.automation.tests/pom.xml` | Bump to `1.1.0-SNAPSHOT` |
| `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF` | `1.1.0.qualifier`; add `org.eclipse.core.variables` |
| `com.example.automation.parent/com.example.automation.feature/feature.xml` | `1.1.0.qualifier` |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/preferences/AutomationPreferences.java` | **New** |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/preferences/AutomationPreferenceInitializer.java` | **New** |
| `com.example.automation.parent/com.example.automation/plugin.xml` | Add `preferencePages` + `preferences` extensions |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/preferences/AutomationPreferencePage.java` | **New** |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/IActionContext.java` | Add `getWorkingDirectory()` |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java` | Variable substitution + working dir injection |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java` | Construct repo from resolved preference |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/AutomationViewTest.java` | Use preference-resolved repo in helpers |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java` | `context.getWorkingDirectory()` fallback |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/MultiLineTextCellEditor.java` | **New** |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java` | Use `MultiLineTextCellEditor` for command |
| `com.example.automation.parent/com.example.automation/src/main/resources/workflows/refresh-workspace.json` | **New** |
| `com.example.automation.parent/com.example.automation/src/main/resources/workflows/echo-workspace-info.json` | **New** |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/BundledWorkflowInstaller.java` | **New** |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/Activator.java` | Call `installIfNeeded` on start |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PreferencePageTest.java` | **New** SWTBot |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/VariableSubstitutionTest.java` | **New** SWTBot |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MultiLineCommandTest.java` | **New** SWTBot |
| `README.md` | **New** — full user documentation |

---

## Task 1: Version bumps

**Files:**
- Modify: `com.example.automation.parent/pom.xml`
- Modify: `com.example.automation.parent/com.example.automation/pom.xml`
- Modify: `com.example.automation.parent/com.example.automation.feature/pom.xml`
- Modify: `com.example.automation.parent/com.example.automation.site/pom.xml`
- Modify: `com.example.automation.parent/com.example.automation.tests/pom.xml`
- Modify: `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`
- Modify: `com.example.automation.parent/com.example.automation.feature/feature.xml`

- [ ] **Step 1: Bump all pom.xml versions from `1.0.0-SNAPSHOT` to `1.1.0-SNAPSHOT`**

In `com.example.automation.parent/pom.xml`, change:
```xml
<version>1.0.0-SNAPSHOT</version>
```
to:
```xml
<version>1.1.0-SNAPSHOT</version>
```

In each of these four files, change the `<parent><version>` from `1.0.0-SNAPSHOT` to `1.1.0-SNAPSHOT`:
- `com.example.automation.parent/com.example.automation/pom.xml`
- `com.example.automation.parent/com.example.automation.feature/pom.xml`
- `com.example.automation.parent/com.example.automation.site/pom.xml`
- `com.example.automation.parent/com.example.automation.tests/pom.xml`

- [ ] **Step 2: Bump bundle version in MANIFEST.MF**

In `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`, change:
```
Bundle-Version: 1.0.0.qualifier
```
to:
```
Bundle-Version: 1.1.0.qualifier
```

- [ ] **Step 3: Bump feature version in feature.xml**

In `com.example.automation.parent/com.example.automation.feature/feature.xml`, change:
```xml
version="1.0.0.qualifier"
```
to:
```xml
version="1.1.0.qualifier"
```

- [ ] **Step 4: Commit**

```bash
git add com.example.automation.parent/pom.xml \
        com.example.automation.parent/com.example.automation/pom.xml \
        com.example.automation.parent/com.example.automation.feature/pom.xml \
        com.example.automation.parent/com.example.automation.site/pom.xml \
        com.example.automation.parent/com.example.automation.tests/pom.xml \
        com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF \
        com.example.automation.parent/com.example.automation.feature/feature.xml
git commit -m "chore: bump version to 1.1.0"
```

---

## Task 2: AutomationPreferences + AutomationPreferenceInitializer

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/preferences/AutomationPreferences.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/preferences/AutomationPreferenceInitializer.java`

- [ ] **Step 1: Create `AutomationPreferences.java`**

```java
package com.example.automation.preferences;

import org.eclipse.jface.preference.IPreferenceStore;
import com.example.automation.Activator;

public class AutomationPreferences {

    public static final String KEY_DEFAULT_WORKING_DIR = "defaultWorkingDir";
    public static final String KEY_WORKFLOW_STORAGE    = "workflowStorage";
    public static final String KEY_WORKFLOWS_DEPLOYED  = "workflowsDeployed";

    public static final String DEFAULT_WORKING_DIR     = "${workspace_loc}/..";
    public static final String DEFAULT_WORKFLOW_STORAGE = "${workspace_loc}/../automation";

    public static IPreferenceStore store() {
        return Activator.getDefault().getPreferenceStore();
    }

    public static String  getDefaultWorkingDir()          { return store().getString(KEY_DEFAULT_WORKING_DIR); }
    public static String  getWorkflowStoragePath()        { return store().getString(KEY_WORKFLOW_STORAGE); }
    public static boolean isWorkflowsDeployed()           { return store().getBoolean(KEY_WORKFLOWS_DEPLOYED); }
    public static void    setWorkflowsDeployed(boolean v) { store().setValue(KEY_WORKFLOWS_DEPLOYED, v); }
}
```

- [ ] **Step 2: Create `AutomationPreferenceInitializer.java`**

```java
package com.example.automation.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import com.example.automation.Activator;

public class AutomationPreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault(AutomationPreferences.KEY_DEFAULT_WORKING_DIR,  AutomationPreferences.DEFAULT_WORKING_DIR);
        store.setDefault(AutomationPreferences.KEY_WORKFLOW_STORAGE,     AutomationPreferences.DEFAULT_WORKFLOW_STORAGE);
        store.setDefault(AutomationPreferences.KEY_WORKFLOWS_DEPLOYED,   false);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/preferences/
git commit -m "feat: add AutomationPreferences facade and default initializer"
```

---

## Task 3: plugin.xml preference extensions

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml`

- [ ] **Step 1: Add two extension blocks before the closing `</plugin>` tag**

Open `com.example.automation.parent/com.example.automation/plugin.xml`. Insert before `</plugin>`:

```xml
  <extension point="org.eclipse.ui.preferencePages">
    <page
        id="com.example.automation.preferences"
        name="Automation"
        class="com.example.automation.preferences.AutomationPreferencePage"/>
  </extension>

  <extension point="org.eclipse.core.runtime.preferences">
    <initializer
        class="com.example.automation.preferences.AutomationPreferenceInitializer"/>
  </extension>
```

- [ ] **Step 2: Commit**

```bash
git add com.example.automation.parent/com.example.automation/plugin.xml
git commit -m "feat: register preference page and initializer extension points"
```

---

## Task 4: AutomationPreferencePage

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/preferences/AutomationPreferencePage.java`

- [ ] **Step 1: Create `AutomationPreferencePage.java`**

```java
package com.example.automation.preferences;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.example.automation.Activator;
import com.example.automation.BundledWorkflowInstaller;

public class AutomationPreferencePage extends FieldEditorPreferencePage
        implements IWorkbenchPreferencePage {

    private StringFieldEditor storageEditor;

    public AutomationPreferencePage() {
        super(GRID);
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
    }

    @Override
    public void init(IWorkbench workbench) {}

    @Override
    protected void createFieldEditors() {
        addField(new StringFieldEditor(
            AutomationPreferences.KEY_DEFAULT_WORKING_DIR,
            "Default working directory:",
            getFieldEditorParent()));

        storageEditor = new StringFieldEditor(
            AutomationPreferences.KEY_WORKFLOW_STORAGE,
            "Workflow storage location:",
            getFieldEditorParent());
        addField(storageEditor);

        Button deployButton = new Button(getFieldEditorParent(), SWT.PUSH);
        deployButton.setText("Deploy bundled workflows");
        deployButton.setToolTipText(
            "Copies the workflows bundled with this plugin into the configured\n" +
            "workflow storage folder. Existing files with the same name are overwritten.\n" +
            "Use this after changing the storage location.");
        GridData gd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        gd.horizontalSpan = 2;
        deployButton.setLayoutData(gd);
        deployButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> onDeploy()));
    }

    private void onDeploy() {
        String raw = storageEditor.getStringValue();
        try {
            IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
            String resolved = svm.performStringSubstitution(raw);
            BundledWorkflowInstaller.install(resolved);
            setMessage("Bundled workflows deployed to: " + resolved);
            setErrorMessage(null);
        } catch (Exception ex) {
            setErrorMessage("Deploy failed: " + ex.getMessage());
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/preferences/AutomationPreferencePage.java
git commit -m "feat: add AutomationPreferencePage with Deploy button"
```

---

## Task 5: IActionContext + WorkflowRunner variable substitution

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/IActionContext.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java`
- Modify: `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`

- [ ] **Step 1: Add `getWorkingDirectory()` to `IActionContext`**

Replace the entire content of `IActionContext.java`:

```java
package com.example.automation.api;

import java.io.OutputStream;

public interface IActionContext {
    void setProgress(int percent);
    boolean isCancelled();
    OutputStream getOutputStream();
    OutputStream getErrorStream();
    String getWorkingDirectory();
}
```

- [ ] **Step 2: Add `org.eclipse.core.variables` to MANIFEST.MF**

In `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`, append `,\n org.eclipse.core.variables` to the `Require-Bundle` list. The result must be:

```
Require-Bundle: org.eclipse.ui,
 org.eclipse.ui.views,
 org.eclipse.core.runtime,
 org.eclipse.core.commands,
 org.eclipse.jface,
 org.eclipse.swt,
 org.eclipse.ui.console,
 org.eclipse.core.resources,
 org.eclipse.debug.core,
 org.eclipse.debug.ui,
 org.eclipse.m2e.core,
 org.eclipse.core.variables
```

- [ ] **Step 3: Replace `WorkflowRunner.java` with the variable-substituting version**

```java
package com.example.automation;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.preferences.AutomationPreferences;

public class WorkflowRunner {

    private final List<Step> steps;
    private final ActionRegistry registry;
    private final Consumer<Runnable> uiRunner;
    private final Runnable refresh;
    private final Runnable onDone;
    private final OutputStream stdout;
    private final OutputStream stderr;

    private volatile boolean cancelled = false;

    public WorkflowRunner(List<Step> steps, ActionRegistry registry,
                          Consumer<Runnable> uiRunner, Runnable refresh, Runnable onDone,
                          OutputStream stdout, OutputStream stderr) {
        this.steps    = steps;
        this.registry = registry;
        this.uiRunner = uiRunner;
        this.refresh  = refresh;
        this.onDone   = onDone;
        this.stdout   = Objects.requireNonNull(stdout, "stdout");
        this.stderr   = Objects.requireNonNull(stderr, "stderr");
    }

    public Thread start() {
        Thread t = new Thread(this::execute, "WorkflowRunner");
        t.setDaemon(true);
        t.start();
        return t;
    }

    public void cancel() {
        cancelled = true;
    }

    private void execute() {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String workingDir = resolveWorkingDir(svm);

        try {
            for (Step step : steps) {
                if (cancelled) break;

                step.setStatus(StepStatus.YELLOW);
                step.setProgress(0);
                uiRunner.accept(refresh);

                IAction action = registry.getAction(step.getActionId());
                if (action == null) {
                    step.setStatus(StepStatus.RED);
                    uiRunner.accept(refresh);
                    break;
                }

                try {
                    Map<String, String> resolved = resolveConfig(step.getConfig(), svm);
                    action.execute(resolved, new ActionContextImpl(step, workingDir));
                    if (!cancelled) {
                        step.setStatus(StepStatus.GREEN);
                        uiRunner.accept(refresh);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    step.setStatus(StepStatus.RED);
                    uiRunner.accept(refresh);
                    break;
                }
            }
        } finally {
            closeQuietly(stdout);
            closeQuietly(stderr);
            uiRunner.accept(onDone);
        }
    }

    private String resolveWorkingDir(IStringVariableManager svm) {
        try {
            return svm.performStringSubstitution(AutomationPreferences.getDefaultWorkingDir());
        } catch (CoreException e) {
            return System.getProperty("user.home");
        }
    }

    private Map<String, String> resolveConfig(Map<String, String> config, IStringVariableManager svm) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String value = entry.getValue();
            try {
                value = svm.performStringSubstitution(value);
            } catch (CoreException e) {
                // leave unresolved; action will receive the literal string
            }
            resolved.put(entry.getKey(), value);
        }
        return resolved;
    }

    private static void closeQuietly(OutputStream s) {
        try { s.close(); } catch (IOException ignored) {}
    }

    private class ActionContextImpl implements IActionContext {

        private final Step step;
        private final String workingDirectory;

        ActionContextImpl(Step step, String workingDirectory) {
            this.step = step;
            this.workingDirectory = workingDirectory;
        }

        @Override
        public void setProgress(int percent) {
            step.setProgress(percent);
            uiRunner.accept(refresh);
        }

        @Override public boolean isCancelled()          { return cancelled; }
        @Override public OutputStream getOutputStream() { return stdout; }
        @Override public OutputStream getErrorStream()  { return stderr; }
        @Override public String getWorkingDirectory()   { return workingDirectory; }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/IActionContext.java \
        com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java \
        com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF
git commit -m "feat: add variable substitution and working dir injection to WorkflowRunner"
```

---

## Task 6: AutomationView storage path + fix existing tests

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java`
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/AutomationViewTest.java`

**Context:** `AutomationView` currently uses `new WorkflowRepository()` which stores in `~/automation/`. After this change it resolves the preference (`${workspace_loc}/../automation` by default) and constructs `WorkflowRepository` with the resolved path. The existing `AutomationViewTest` tests that create workflows via `new WorkflowRepository()` must be updated to use the same resolved path, or the view won't find those workflows.

- [ ] **Step 1: Add imports and `repository()` helper to `AutomationView`**

Add these imports to `AutomationView.java` (after the existing import block):

```java
import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

import com.example.automation.preferences.AutomationPreferences;
```

Add this private helper method (e.g., below `save()`):

```java
private WorkflowRepository repository() throws Exception {
    IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
    String resolved = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
    return new WorkflowRepository(new File(resolved));
}
```

- [ ] **Step 2: Update `loadWorkflows()` to use `repository()`**

Replace:
```java
private void loadWorkflows() {
    try {
        workflows = new WorkflowRepository().list();
    } catch (IOException e) {
```
with:
```java
private void loadWorkflows() {
    try {
        workflows = repository().list();
    } catch (Exception e) {
```

- [ ] **Step 3: Update `save()` to use `repository()`**

Replace:
```java
private void save() {
    if (currentWorkflow == null) return;
    try {
        new WorkflowRepository().save(currentWorkflow);
    } catch (Exception e) {
```
with:
```java
private void save() {
    if (currentWorkflow == null) return;
    try {
        repository().save(currentWorkflow);
    } catch (Exception e) {
```

- [ ] **Step 4: Update `saveNew()` to use `repository()`**

Replace:
```java
private void saveNew(Workflow wf) {
    try {
        new WorkflowRepository().save(wf);
    } catch (Exception e) {
```
with:
```java
private void saveNew(Workflow wf) {
    try {
        repository().save(wf);
    } catch (Exception e) {
```

- [ ] **Step 5: Update `AutomationViewTest` to use the preference-resolved repo**

The two tests that call `new WorkflowRepository()` must use the preference-resolved path so their test workflows end up in the same location the view reads from.

Add these imports to `AutomationViewTest.java`:
```java
import java.io.File;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import com.example.automation.preferences.AutomationPreferences;
```

Add this private static helper method inside `AutomationViewTest`:
```java
private static WorkflowRepository testRepo() throws Exception {
    IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
    String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
    return new WorkflowRepository(new File(path));
}
```

In `selectingWorkflowShowsSteps()`, replace `new WorkflowRepository()` with `testRepo()`:
```java
WorkflowRepository repo = testRepo();
```
(The method already throws `IOException`; change the throws clause to `throws Exception`.)

In `multipleStepsCanBeSelected()`, replace `new WorkflowRepository()` with `testRepo()`:
```java
WorkflowRepository repo = testRepo();
```
(Same throws clause change.)

- [ ] **Step 6: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java \
        com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/AutomationViewTest.java
git commit -m "feat: resolve workflow storage path from preference at load/save time"
```

---

## Task 7: ShellCommandAction working directory fallback

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java`

- [ ] **Step 1: Replace `execute()` body to use `context.getWorkingDirectory()`**

Replace the entire `ShellCommandAction.java`:

```java
package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class ShellCommandAction implements IAction {

    @Override public String getId()          { return "shell-command"; }
    @Override public String getName()        { return "Shell Command"; }
    @Override public String getDescription() { return "Executes a shell command."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("command", "", "workingDir", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("command", "").isBlank())
            errors.add("command must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String command = config.get("command");
        if (command == null || command.isBlank())
            throw new IllegalArgumentException("command must not be blank");

        String workingDir = config.getOrDefault("workingDir", "").trim();

        List<String> cmd = System.getProperty("os.name").toLowerCase().contains("win")
            ? List.of("cmd.exe", "/c", command)
            : List.of("sh", "-c", command);

        File dir = workingDir.isBlank()
            ? new File(context.getWorkingDirectory())
            : new File(workingDir);

        ProcessRunner.run(cmd, dir, context);
    }
}
```

Note: the `ResourcesPlugin` import is removed — the fallback now comes from `context.getWorkingDirectory()` which `WorkflowRunner` resolves from the preference.

- [ ] **Step 2: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java
git commit -m "feat: use context.getWorkingDirectory() fallback in ShellCommandAction"
```

---

## Task 8: MultiLineTextCellEditor + StepPropertySource

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/MultiLineTextCellEditor.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`

- [ ] **Step 1: Create `MultiLineTextCellEditor.java`**

```java
package com.example.automation;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

public class MultiLineTextCellEditor extends CellEditor {

    private Text text;

    public MultiLineTextCellEditor(Composite parent) {
        super(parent);
    }

    @Override
    protected Control createControl(Composite parent) {
        text = new Text(parent, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.BORDER);
        text.addKeyListener(KeyListener.keyPressedAdapter(e -> {
            if (e.keyCode == SWT.CR && (e.stateMask & SWT.MOD1) != 0) {
                fireApplyEditorValue();
                deactivate();
            }
        }));
        return text;
    }

    @Override
    protected Object doGetValue() {
        return text == null ? "" : text.getText();
    }

    @Override
    protected void doSetValue(Object value) {
        if (text != null) text.setText(value == null ? "" : (String) value);
    }

    @Override
    protected void doSetFocus() {
        if (text != null) { text.selectAll(); text.setFocus(); }
    }
}
```

- [ ] **Step 2: Update `StepPropertySource` to use `MultiLineTextCellEditor` for the command property**

Add this import to `StepPropertySource.java`:
```java
import org.eclipse.swt.widgets.Composite;
```

Replace the `getPropertyDescriptors()` method body and add the private helper:

```java
@Override
public IPropertyDescriptor[] getPropertyDescriptors() {
    List<IPropertyDescriptor> list = new ArrayList<>();
    PropertyDescriptor actionDesc = new PropertyDescriptor(PROP_ACTION, "Action");
    actionDesc.setCategory("Step");
    list.add(actionDesc);
    for (String key : configKeys()) {
        list.add(createConfigDescriptor(key));
    }
    return list.toArray(new IPropertyDescriptor[0]);
}

private IPropertyDescriptor createConfigDescriptor(String key) {
    if ("shell-command".equals(step.getActionId()) && "command".equals(key)) {
        PropertyDescriptor d = new PropertyDescriptor(key, key) {
            @Override
            public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                return new MultiLineTextCellEditor(parent);
            }
        };
        d.setCategory("Config");
        return d;
    }
    TextPropertyDescriptor d = new TextPropertyDescriptor(key, key);
    d.setCategory("Config");
    return d;
}
```

- [ ] **Step 3: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/MultiLineTextCellEditor.java \
        com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java
git commit -m "feat: add multi-line cell editor for shell command property"
```

---

## Task 9: BundledWorkflowInstaller + sample workflows + Activator

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/resources/workflows/refresh-workspace.json`
- Create: `com.example.automation.parent/com.example.automation/src/main/resources/workflows/echo-workspace-info.json`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/BundledWorkflowInstaller.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/Activator.java`

**Context:** Files in `src/main/resources/` are copied to `target/classes/` by Maven. Since `bin.includes = .` in `build.properties` includes the build output directory, these files end up in the bundle JAR at `workflows/*.json`. `Bundle.findEntries("workflows", "*.json", false)` finds them at runtime.

- [ ] **Step 1: Create the `src/main/resources/workflows/` directory and first sample workflow**

Create `com.example.automation.parent/com.example.automation/src/main/resources/workflows/refresh-workspace.json`:

```json
{
  "workflowId": "refresh-workspace",
  "displayName": "Refresh Workspace",
  "description": "Refreshes all projects in the Eclipse workspace.",
  "steps": [
    {
      "actionId": "refresh-all",
      "config": {}
    }
  ]
}
```

- [ ] **Step 2: Create the second sample workflow**

Create `com.example.automation.parent/com.example.automation/src/main/resources/workflows/echo-workspace-info.json`:

```json
{
  "workflowId": "echo-workspace-info",
  "displayName": "Echo Workspace Info",
  "description": "Demonstrates Eclipse variable substitution: prints the workspace location to the Automation console.",
  "steps": [
    {
      "actionId": "shell-command",
      "config": {
        "command": "echo Workspace: ${workspace_loc}",
        "workingDir": "${workspace_loc}/.."
      }
    }
  ]
}
```

- [ ] **Step 3: Create `BundledWorkflowInstaller.java`**

```java
package com.example.automation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;

import org.osgi.framework.Bundle;

import com.example.automation.preferences.AutomationPreferences;

public class BundledWorkflowInstaller {

    public static void installIfNeeded(String resolvedStoragePath) {
        if (AutomationPreferences.isWorkflowsDeployed()) return;
        install(resolvedStoragePath);
    }

    public static void install(String resolvedStoragePath) {
        Bundle bundle = Activator.getDefault().getBundle();
        Enumeration<URL> entries = bundle.findEntries("workflows", "*.json", false);
        File dir = new File(resolvedStoragePath);
        dir.mkdirs();
        while (entries != null && entries.hasMoreElements()) {
            URL url = entries.nextElement();
            String name = new File(url.getPath()).getName();
            File dest = new File(dir, name);
            try (InputStream in = url.openStream()) {
                Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Activator.getDefault().getLog().warn("Failed to copy bundled workflow: " + name, e);
            }
        }
        AutomationPreferences.setWorkflowsDeployed(true);
    }
}
```

- [ ] **Step 4: Update `Activator.start()` to deploy bundled workflows on first launch**

Replace the entire `Activator.java`:

```java
package com.example.automation;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.example.automation.preferences.AutomationPreferences;

public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.example.automation";

    private static Activator plugin;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        try {
            IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
            String resolved = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
            BundledWorkflowInstaller.installIfNeeded(resolved);
        } catch (Exception e) {
            getLog().info("Bundled workflow deployment skipped: " + e.getMessage());
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return plugin;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/resources/ \
        com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/BundledWorkflowInstaller.java \
        com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/Activator.java
git commit -m "feat: add bundled workflows and auto-deploy on first launch"
```

---

## Task 10: SWTBot tests

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PreferencePageTest.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/VariableSubstitutionTest.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MultiLineCommandTest.java`

- [ ] **Step 1: Write `PreferencePageTest.java`**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.junit.*;

public class PreferencePageTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = new SWTWorkbenchBot();
    }

    @After
    public void closePreferencesIfOpen() {
        try {
            bot.shell("Preferences").bot().button("Cancel").click();
        } catch (Exception ignored) {}
    }

    @Test
    public void preferencePageHasBothFields() {
        openAutomationPreferences();
        SWTBotShell shell = bot.shell("Preferences");
        assertNotNull(shell.bot().textWithLabel("Default working directory:"));
        assertNotNull(shell.bot().textWithLabel("Workflow storage location:"));
    }

    @Test
    public void deployButtonHasTooltip() {
        openAutomationPreferences();
        SWTBotShell shell = bot.shell("Preferences");
        String tooltip = shell.bot().button("Deploy bundled workflows").getToolTipText();
        assertTrue("Tooltip should mention copying workflows",
            tooltip.contains("Copies the workflows"));
    }

    @Test
    public void defaultWorkingDirContainsWorkspaceVar() {
        openAutomationPreferences();
        SWTBotShell shell = bot.shell("Preferences");
        String value = shell.bot().textWithLabel("Default working directory:").getText();
        assertTrue("Default should contain ${workspace_loc}", value.contains("${workspace_loc}"));
    }

    private void openAutomationPreferences() {
        bot.menu("Window").menu("Preferences").click();
        bot.shell("Preferences").bot().tree().getTreeItem("Automation").select();
    }
}
```

- [ ] **Step 2: Write `VariableSubstitutionTest.java`**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.File;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.junit.*;
import com.example.automation.preferences.AutomationPreferences;

public class VariableSubstitutionTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = new SWTWorkbenchBot();
    }

    @Test
    public void workspaceLocVariableResolvesToExistingPath() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String resolved = svm.performStringSubstitution("${workspace_loc}");
        assertNotNull(resolved);
        assertFalse("Variable should resolve to a real path, not the literal expression",
            resolved.contains("${"));
        assertTrue("Resolved workspace path should exist on disk", new File(resolved).exists());
    }

    @Test
    public void defaultWorkingDirResolvesToExistingPath() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String raw = AutomationPreferences.getDefaultWorkingDir();
        String resolved = svm.performStringSubstitution(raw);
        assertFalse("Resolved working dir should not contain ${", resolved.contains("${"));
        File resolvedFile = new File(resolved).getCanonicalFile();
        assertTrue("Default working dir should exist on disk: " + resolvedFile, resolvedFile.exists());
    }

    @Test
    public void workflowStoragePathResolvesToString() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String raw = AutomationPreferences.getWorkflowStoragePath();
        String resolved = svm.performStringSubstitution(raw);
        assertFalse("Resolved storage path should not contain ${", resolved.contains("${"));
        assertFalse("Resolved storage path should not be blank", resolved.isBlank());
    }
}
```

- [ ] **Step 3: Write `MultiLineCommandTest.java`**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.File;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.junit.*;
import com.example.automation.model.*;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

public class MultiLineCommandTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = new SWTWorkbenchBot();
    }

    @Test
    public void multiLineCommandRoundTripsWithNewlines() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String storagePath = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        WorkflowRepository repo = new WorkflowRepository(new File(storagePath));

        Workflow wf = new Workflow("multi-line-test", "Multi Line Test", "");
        Step step = new Step("shell-command");
        step.getConfig().put("command", "echo line1\necho line2");
        step.getConfig().put("workingDir", "");
        wf.getSteps().add(step);
        repo.save(wf);

        try {
            Workflow loaded = repo.load("multi-line-test");
            String command = loaded.getSteps().get(0).getConfig().get("command");
            assertTrue("Saved command should contain a newline character", command.contains("\n"));
            assertEquals("echo line1\necho line2", command);
        } finally {
            repo.delete("multi-line-test");
        }
    }
}
```

- [ ] **Step 4: Run all tests**

```bash
cd com.example.automation.parent
mvn verify -Dtycho.surefire.timeout=120
```

Expected: 77 tests pass, 0 failures.

If tests fail due to `AutomationViewTest` path mismatch (the view loads from the preference path but test data was created in `~/automation/`), verify that Task 6 Step 5 was applied correctly — the `testRepo()` helper must be used in both `selectingWorkflowShowsSteps()` and `multipleStepsCanBeSelected()`.

- [ ] **Step 5: Commit**

```bash
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PreferencePageTest.java \
        com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/VariableSubstitutionTest.java \
        com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MultiLineCommandTest.java
git commit -m "test: add SWTBot tests for preference page, variable substitution, and multi-line command"
```

---

## Task 11: README.md

**Files:**
- Create: `README.md` (project root)

- [ ] **Step 1: Write `README.md` with full user documentation**

Create `README.md` at the project root with the following content:

```markdown
# Automation Plugin for Eclipse

An Eclipse plugin that lets you define, manage, and run multi-step automation workflows from within the IDE. Workflows are sequences of steps; each step is an action (shell command, git operation, Maven build, etc.) with its own configuration.

**Requirements:** Eclipse 2023-06 or later, Java 17.

---

## Installation

1. Open Eclipse and go to **Help > Install New Software…**
2. Click **Add…** and enter the URL of the update site.
3. Select **Automation** from the feature list and click **Next > Finish**.
4. Restart Eclipse when prompted.

After installation, open the view via **Project > Automation** or **Window > Show View > Other… > Automation**.

---

## Concepts

| Term | Meaning |
|---|---|
| **Workflow** | A named, ordered list of steps. Stored as a JSON file in the workflow storage folder. |
| **Step** | A single unit of work: one action type plus its configuration values. |
| **Action** | A built-in capability (Shell Command, Git Clone, Maven Run, etc.) registered with the plugin. |

Workflow JSON files are plain text. You can open, read, and edit them in any text editor.

---

## The Automation View

Open via **Project > Automation**. The view has three areas:

**Header** — Shows the name (bold) and description of the currently open workflow. Displays "(no workflows)" when none exist.

**Toolbar** — Icon buttons (hover for tooltip):

| Button | Tooltip | Action |
|---|---|---|
| New Workflow | New Workflow | Opens a dialog to create a new workflow with a name and description. |
| Open Workflow | Open Workflow | Opens a picker dialog to select an existing workflow. |
| Add Step | Add Step | Opens a dialog showing all available action types; adds the selected action as a new step. |
| Delete Step | Delete Step | Removes the selected step(s). |
| Move Step Up | Move Step Up | Moves the selected step one position up. |
| Move Step Down | Move Step Down | Moves the selected step one position down. |
| Run Workflow | Run Workflow | Runs all steps in order. |
| Run Selected Steps | Run Selected Steps | Runs only the selected steps, in workflow order. |
| Stop | Stop | Cancels the currently running workflow. |

**Step Table** — Three columns:

| Column | Content |
|---|---|
| Status | Colored indicator: white (not run), yellow (running), green (success), red (failed). |
| Name | The action type name (e.g., "Shell Command"). |
| Config | A summary of the step's configuration values. |

---

## Managing Workflows

**Creating a workflow:** Click **New Workflow**. Enter a name and optional description. The workflow ID is derived from the name automatically. The new workflow is saved immediately.

**Opening a workflow:** Click **Open Workflow**. A dialog lists all available workflows with their names and descriptions. Select one and click OK (or double-click).

**Adding a step:** Click **Add Step**. A dialog lists all registered action types with their descriptions. Select one and click OK (or double-click). The step is appended to the end of the current workflow.

**Reordering steps:** Select a single step and use **Move Step Up** or **Move Step Down**.

**Deleting a step:** Select one or more steps and click **Delete Step**.

**Running a workflow:** Click **Run Workflow** to run all steps. Click **Run Selected Steps** to run only the selected rows (in workflow order, regardless of selection order). Output appears in the **Automation** console.

---

## Editing Step Configuration

Select a step in the table. Open the Properties View via **Window > Show View > Properties**.

The Properties View shows two sections:

- **Step** — The action type ID (read-only).
- **Config** — One row per configuration field. Click a value cell to edit it. Press Enter or click elsewhere to confirm.

**Special behaviour for Shell Command:** The `command` field uses a multi-line text editor (five rows tall, with a vertical scrollbar). Press **Ctrl+Enter** to commit the value. The Enter key alone inserts a newline inside the command.

---

## Action Reference

### Shell Command

Executes a shell command. On Windows: `cmd.exe /c <command>`. On Linux/macOS: `sh -c <command>`.

| Field | Required | Description |
|---|---|---|
| `command` | Yes | The command to run. May be multi-line (separate commands with newlines). Eclipse variables are supported. |
| `workingDir` | No | Working directory for the command. If blank, uses the **Default working directory** from the preference page. Eclipse variables are supported. |

### Git Clone

Clones a remote git repository to a local directory.

| Field | Required | Description |
|---|---|---|
| `url` | Yes | Repository URL (HTTPS or SSH). |
| `targetDir` | Yes | Local directory to clone into. Eclipse variables are supported. |
| `branch` | No | Branch to check out. If blank, the remote's default branch is used. |

### Git Checkout

Checks out a branch in an existing local repository.

| Field | Required | Description |
|---|---|---|
| `repoDir` | Yes | Path to the local repository. Eclipse variables are supported. |
| `branch` | Yes | Branch name to check out. |

### Maven Run with Progress

Launches an existing Eclipse Maven launch configuration and monitors its output for build progress.

| Field | Required | Description |
|---|---|---|
| `configName` | Yes | Name of an Eclipse launch configuration (as shown in **Run > Run Configurations…**). |

Requires M2E (Maven Integration for Eclipse) to be installed.

### Maven Update Project

Runs **Maven > Update Project** on a workspace project (equivalent to right-clicking and choosing Maven > Update Project).

| Field | Required | Description |
|---|---|---|
| `projectName` | Yes | Project name as shown in the Package Explorer. |

Requires M2E.

### Import Maven Project

Imports an existing Maven project from disk into the Eclipse workspace.

| Field | Required | Description |
|---|---|---|
| `pomPath` | Yes | Path to `pom.xml` or its parent directory. Eclipse variables are supported. |

Requires M2E.

### Execute Run Configuration

Executes any existing Eclipse launch configuration by name.

| Field | Required | Description |
|---|---|---|
| `configName` | Yes | Name of the launch configuration. |
| `mode` | No | Launch mode: `run` (default), `debug`, or `profile`. |

### Refresh Project

Refreshes a single project in the Eclipse workspace (same as pressing F5 on the project).

| Field | Required | Description |
|---|---|---|
| `projectName` | Yes | Project name as shown in the Package Explorer. |

### Refresh All

Refreshes all projects in the Eclipse workspace. No configuration fields.

---

## Eclipse Variable Substitution

Any configuration field of any action can contain Eclipse string substitution variables. Variables are resolved at **execution time** — the stored workflow always contains the raw expression, never the resolved value. This makes workflows portable across machines and workspaces.

**Syntax:** `${variable_name}` or `${variable_name:argument}`

**Commonly useful variables:**

| Variable | Resolves to |
|---|---|
| `${workspace_loc}` | Absolute path of the current Eclipse workspace directory |
| `${workspace_loc:/ProjectName}` | Absolute path of the project named `ProjectName` in the workspace |
| `${project_loc}` | Absolute path of the currently selected project in the workbench |
| `${project_name}` | Name of the currently selected project |
| `${resource_loc}` | Absolute path of the currently selected resource (file or folder) |
| `${resource_name}` | File name of the currently selected resource |
| `${container_loc}` | Absolute path of the parent folder of the selected resource |
| `${env_var:NAME}` | Value of the OS environment variable `NAME` |
| `${system_property:NAME}` | Value of the Java system property `NAME` (e.g., `${system_property:user.home}`) |
| `${string_prompt:message}` | Prompts the user to type a value at run time; shows `message` as the prompt |
| `${build_type}` | Current build type (`incremental`, `full`, `auto`, or `none`) |

**Example — clone a repo next to the workspace:**
```
targetDir = ${workspace_loc}/../my-project
```

**Example — run a command with a project-relative working directory:**
```
command   = mvn clean install
workingDir = ${workspace_loc:/my-project}
```

**Full variable list:** In Eclipse, go to **Run > String Substitution…** to see all variables available on your installation, including any contributed by other plugins.

---

## Preference Page

Open via **Window > Preferences > Automation**.

| Setting | Default | Description |
|---|---|---|
| Default working directory | `${workspace_loc}/..` | The working directory used by Shell Command steps when their `workingDir` field is blank. Also used as the base for resolving relative paths in other actions that support `context.getWorkingDirectory()`. |
| Workflow storage location | `${workspace_loc}/../automation` | The directory where workflow JSON files are stored and loaded from. |

Both fields accept Eclipse variable expressions. Changes take effect immediately on the next workflow load or save — no restart required.

**Deploy bundled workflows button:** Copies the workflows that were shipped with this plugin installation into the configured storage folder. Existing files with the same name are overwritten. Use this button after changing the storage location to repopulate it with the bundled workflows. Hover the button for a full description.

---

## Bundled Workflows

The plugin ships with sample workflows that are automatically copied to your workflow storage folder the first time Eclipse starts after installation.

| Workflow | Description |
|---|---|
| Refresh Workspace | Runs **Refresh All** — useful as the final step of any multi-step workflow to sync the workspace after file system changes. |
| Echo Workspace Info | Runs a Shell Command that prints `${workspace_loc}` to the Automation console, demonstrating variable substitution. |

If automatic deployment fails (e.g., the storage directory is not writable at startup), open **Window > Preferences > Automation** and click **Deploy bundled workflows**.

---

## Sharing Workflows Between Users

Workflows are plain JSON files stored in the workflow storage folder (see **Window > Preferences > Automation** for the exact path on your machine).

**To share a workflow:**

1. Open your workflow storage folder in a file explorer.
2. Copy the `.json` file(s) for the workflow(s) you want to share.
3. Send the file(s) to your colleague (email, shared drive, git repository, etc.).

**To receive a shared workflow:**

1. Place the received `.json` file in your own workflow storage folder.
2. In Eclipse, close and reopen the Automation view (or click **Open Workflow** — it re-reads the folder each time).
3. The shared workflow appears in the picker.

> **Tip:** Workflows that use `${workspace_loc}` or `${project_loc:/ProjectName}` are inherently portable — the variables resolve to each user's own paths at run time, so no path editing is needed after sharing.

---

## Using Workflows from Another Workspace

You can point the Automation view at any folder on disk — including a folder that belongs to a different Eclipse workspace.

1. Go to **Window > Preferences > Automation**.
2. Change **Workflow storage location** to the path of the other workspace's automation folder. You can use a relative expression such as:
   ```
   ${workspace_loc}/../other-workspace/automation
   ```
   or an absolute path such as:
   ```
   /home/alice/other-workspace/automation
   ```
3. Click **Apply and Close**.
4. Reload the Automation view (close and reopen, or click **Open Workflow**).

The view now reads workflows from that folder. Any saves or new workflows you create will also go there.

> **Note:** If you change the storage location and want the bundled sample workflows in the new location, click **Deploy bundled workflows** on the preference page.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add complete user documentation README"
```

---

## Final verification

- [ ] Run the full test suite one last time to confirm all 77 tests pass:

```bash
cd com.example.automation.parent
mvn verify -Dtycho.surefire.timeout=120
```

Expected output: `BUILD SUCCESS`, 77 tests, 0 failures, 0 errors.
