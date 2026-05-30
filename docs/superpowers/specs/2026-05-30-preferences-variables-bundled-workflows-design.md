# Preferences, Variable Substitution, Multi-line Shell, and Bundled Workflows — Design Spec

**Date:** 2026-05-30
**Status:** Approved

---

## Overview

Four targeted improvements delivered as one sub-project:

1. **`AutomationPreferences`** — a thin static facade over Eclipse's `IPreferenceStore` centralising two user-configurable settings: default working directory and workflow storage location (both stored as Eclipse variable expressions).
2. **Variable substitution** — `WorkflowRunner` resolves all step config values through `IStringVariableManager` before executing each action, so `${workspace_loc}`, `${env_var:NAME}`, etc. work in any config field of any action.
3. **Working directory injection** — `IActionContext` gains `getWorkingDirectory()` carrying the resolved preference value; `ShellCommandAction` uses it as a fallback when its `workingDir` config is blank.
4. **Multi-line shell command** — the `command` config field uses a `MultiLineTextCellEditor` (SWT.MULTI | SWT.V_SCROLL) so multi-line scripts can be typed directly in the Properties View.
5. **Bundled workflows** — sample workflow JSON files shipped inside the plugin JAR, auto-copied to the configured storage folder on first launch, re-deployable via a preference page button.
6. **User documentation** — `README.md` in the project root documenting all plugin features for end users.
7. **Version bump** — plugin and feature versions advance from `1.0.0` to `1.1.0` so Eclipse p2 allows over-the-top installation.

---

## Architecture

### `AutomationPreferences`

Static facade. All callers use this class — no direct `IPreferenceStore` access outside it.

```java
public class AutomationPreferences {
    public static final String KEY_DEFAULT_WORKING_DIR = "defaultWorkingDir";
    public static final String KEY_WORKFLOW_STORAGE    = "workflowStorage";
    public static final String KEY_WORKFLOWS_DEPLOYED  = "workflowsDeployed";

    public static final String DEFAULT_WORKING_DIR     = "${workspace_loc}/..";
    public static final String DEFAULT_WORKFLOW_STORAGE = "${workspace_loc}/../automation";

    public static IPreferenceStore store() {
        return Activator.getDefault().getPreferenceStore();
    }
    public static String  getDefaultWorkingDir()        { return store().getString(KEY_DEFAULT_WORKING_DIR); }
    public static String  getWorkflowStoragePath()      { return store().getString(KEY_WORKFLOW_STORAGE); }
    public static boolean isWorkflowsDeployed()         { return store().getBoolean(KEY_WORKFLOWS_DEPLOYED); }
    public static void    setWorkflowsDeployed(boolean v) { store().setValue(KEY_WORKFLOWS_DEPLOYED, v); }
}
```

### `AutomationPreferenceInitializer`

`AbstractPreferenceInitializer` subclass. Sets the three defaults on first access:

```java
store.setDefault(KEY_DEFAULT_WORKING_DIR,  DEFAULT_WORKING_DIR);
store.setDefault(KEY_WORKFLOW_STORAGE,     DEFAULT_WORKFLOW_STORAGE);
store.setDefault(KEY_WORKFLOWS_DEPLOYED,   false);
```

Registered via `org.eclipse.core.runtime.preferences` extension point in `plugin.xml`.

### `AutomationPreferencePage`

`FieldEditorPreferencePage` with `GRID` layout, registered under `org.eclipse.ui.preferencePages` as top-level page "Automation".

Fields:
- `StringFieldEditor` — label "Default working directory", key `KEY_DEFAULT_WORKING_DIR`
- `StringFieldEditor` — label "Workflow storage location", key `KEY_WORKFLOW_STORAGE`
- Plain `Button` — label "Deploy bundled workflows", created in `createFieldEditors()` via `getFieldEditorParent()`. Has tooltip:
  ```
  Copies the workflows bundled with this plugin into the configured
  workflow storage folder. Existing files with the same name are overwritten.
  Use this after changing the storage location.
  ```
  Selection listener resolves the current storage field value via `IStringVariableManager`, then calls `BundledWorkflowInstaller.install(resolvedPath)`. Shows result via `setMessage()` on the page: "N workflow(s) deployed to <path>." or an error message if the directory could not be created.

Both `StringFieldEditor` values are Eclipse variable expressions (e.g., `${workspace_loc}/..`). They are not resolved at edit time — resolution happens at use time.

### `BundledWorkflowInstaller`

One class, two public methods.

```java
public class BundledWorkflowInstaller {

    // Called from Activator.start() — skips silently if already deployed
    public static void installIfNeeded(String resolvedStoragePath) {
        if (AutomationPreferences.isWorkflowsDeployed()) return;
        install(resolvedStoragePath);
    }

    // Called from preference page button — always copies, overwrites
    public static void install(String resolvedStoragePath) {
        Bundle bundle = Activator.getDefault().getBundle();
        Enumeration<URL> entries = bundle.findEntries("workflows", "*.json", false);
        File dir = new File(resolvedStoragePath);
        dir.mkdirs();
        int count = 0;
        while (entries != null && entries.hasMoreElements()) {
            URL url = entries.nextElement();
            String name = new File(url.getPath()).getName();
            File dest = new File(dir, name);
            try (InputStream in = url.openStream()) {
                Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                count++;
            } catch (IOException e) {
                Activator.getDefault().getLog().warn("Failed to copy bundled workflow: " + name, e);
            }
        }
        AutomationPreferences.setWorkflowsDeployed(true);
    }
}
```

Bundled JSON files are placed at `src/main/resources/workflows/` in the plugin project. Tycho includes everything under `src/main/resources/` in the JAR automatically — no `build.properties` change required beyond confirming `.` is in `source..`.

`Activator.start()` resolves `AutomationPreferences.getWorkflowStoragePath()` via `IStringVariableManager` and calls `installIfNeeded()`. If variable resolution fails (workspace not yet initialised), it logs a warning at INFO level and skips — the user can deploy manually from the preference page.

### Variable substitution in `WorkflowRunner`

Before calling `action.execute(config, context)`, `WorkflowRunner` resolves all config values and the working directory:

```java
IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();

// 1. Substitute all config values
Map<String, String> resolved = new LinkedHashMap<>();
for (Map.Entry<String, String> e : step.getConfig().entrySet())
    resolved.put(e.getKey(), svm.performStringSubstitution(e.getValue()));

// 2. Resolve working directory from preference
String rawDir = AutomationPreferences.getDefaultWorkingDir();
String resolvedDir = svm.performStringSubstitution(rawDir);

ActionContext ctx = new ActionContext(console, resolvedDir);
action.execute(resolved, ctx);
```

Actions always receive fully-resolved strings. No action needs to know about substitution.

### `IActionContext`

One new method added to the interface:

```java
String getWorkingDirectory(); // resolved absolute path, never null
```

The existing `ActionContext` implementation class gains a `workingDirectory` constructor parameter and stores it.

### `ShellCommandAction` working directory fallback

After this change, `workingDir` in the resolved config is already a substituted string (or blank). The fallback chain:

```java
String workingDir = config.getOrDefault("workingDir", "").trim();
File dir = workingDir.isBlank()
    ? new File(context.getWorkingDirectory())
    : new File(workingDir);
```

The `ResourcesPlugin` fallback is removed — `context.getWorkingDirectory()` is always set by `WorkflowRunner`.

### `MultiLineTextCellEditor`

Extends `CellEditor`. Created with a `Composite` parent.

```java
@Override
protected Control createControl(Composite parent) {
    text = new Text(parent, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.BORDER);
    text.addKeyListener(KeyListener.keyPressedAdapter(e -> {
        if (e.keyCode == SWT.CR && (e.stateMask & SWT.MOD1) != 0)
            fireApplyEditorValue(); // Ctrl+Enter to commit
    }));
    return text;
}

@Override
protected Object doGetValue()          { return text.getText(); }
@Override
protected void   doSetValue(Object v)  { text.setText(v == null ? "" : (String) v); }
@Override
protected void   doSetFocus()          { text.selectAll(); text.setFocus(); }
```

Key bindings:
- **Enter** — inserts newline
- **Ctrl+Enter** — commits the value
- **Escape** — cancels (standard `CellEditor` behaviour)

`StepAdapterFactory` (or `StepPropertySource`) uses `MultiLineTextCellEditor` only for the `command` property descriptor of `ShellCommandAction` steps. All other properties continue to use `TextCellEditor`.

`StepPropertySource` (not `StepAdapterFactory`) is where the property descriptors are created. The `command` descriptor for steps whose action id is `"shell-command"` uses `MultiLineTextCellEditor` instead of the standard `TextCellEditor`.

The stored value in the step config is a plain `String` with embedded `\n` characters. Both `cmd.exe /c` and `sh -c` handle multi-line scripts passed as a single argument correctly.

### `WorkflowRepository` storage path

`AutomationView.loadWorkflows()` and `AutomationView.saveWorkflow()` resolve the storage preference at call time:

```java
String raw = AutomationPreferences.getWorkflowStoragePath();
String resolved = VariablesPlugin.getDefault()
    .getStringVariableManager().performStringSubstitution(raw);
WorkflowRepository repo = new WorkflowRepository(new File(resolved));
```

No persistent `WorkflowRepository` instance. Fresh construction per call means preference changes take effect immediately without change-listener wiring. The existing two-argument constructor is unchanged.

---

## `plugin.xml` additions

```xml
<extension point="org.eclipse.ui.preferencePages">
  <page id="com.example.automation.preferences"
        name="Automation"
        class="com.example.automation.preferences.AutomationPreferencePage"/>
</extension>

<extension point="org.eclipse.core.runtime.preferences">
  <initializer
      class="com.example.automation.preferences.AutomationPreferenceInitializer"/>
</extension>
```

---

## `MANIFEST.MF` changes

- `Bundle-Version: 1.1.0.qualifier`
- Add `org.eclipse.core.variables` to `Require-Bundle`

---

## Version bumps

| File | Old | New |
|---|---|---|
| `com.example.automation/META-INF/MANIFEST.MF` | `1.0.0.qualifier` | `1.1.0.qualifier` |
| `com.example.automation.feature/feature.xml` | `1.0.0.qualifier` | `1.1.0.qualifier` |
| `com.example.automation.parent/pom.xml` | `1.0.0-SNAPSHOT` | `1.1.0-SNAPSHOT` |
| `com.example.automation.parent/com.example.automation/pom.xml` | `1.0.0-SNAPSHOT` | `1.1.0-SNAPSHOT` |
| `com.example.automation.parent/com.example.automation.feature/pom.xml` | `1.0.0-SNAPSHOT` | `1.1.0-SNAPSHOT` |
| `com.example.automation.parent/com.example.automation.tests/pom.xml` | `1.0.0-SNAPSHOT` | `1.1.0-SNAPSHOT` |
| `com.example.automation.parent/com.example.automation.updatesite/pom.xml` | `1.0.0-SNAPSHOT` | `1.1.0-SNAPSHOT` |

---

## `README.md` — User Documentation

Top-level file at `README.md`. Chapters:

1. **Overview** — what the plugin does, requirements (Eclipse 2023-06+, Java 17)
2. **Installation** — install from update site; verify via Window > Show View > Automation
3. **Concepts** — Workflow, Step, Action type; JSON storage format (one file per workflow)
4. **The Automation View** — toolbar buttons (New Workflow, Open Workflow, Add Step, Delete Step, Move Up, Move Down, Run Workflow, Run Selected Steps, Stop); header area (name + description); step table columns
5. **Managing workflows** — creating, opening, running; step table selection
6. **Editing step configuration** — Properties View; Ctrl+Enter to commit multi-line command
7. **Action reference** — one subsection per action type:

   | Action | Config fields |
   |---|---|
   | Shell Command | `command` (multi-line, required), `workingDir` (optional — defaults to preference) |
   | Git Clone | `url` (required), `targetDir` (required), `branch` (optional) |
   | Git Checkout | `repoDir` (required), `branch` (required) |
   | Maven Run with Progress | `configName` (required — name of an existing Eclipse launch configuration) |
   | Maven Update Project | `projectName` (required — project name as shown in Package Explorer) |
   | Import Maven Project | `pomPath` (required — path to `pom.xml` or its parent directory) |
   | Execute Run Configuration | `configName` (required), `mode` (optional, default `run`) |
   | Refresh Project | `projectName` (required) |
   | Refresh All | _(no configuration)_ |

8. **Eclipse variable substitution** — variables can be used in any config field of any action; resolved at execution time. Reference table:

   | Variable | Resolves to |
   |---|---|
   | `${workspace_loc}` | Absolute path of the current Eclipse workspace |
   | `${workspace_loc:/ProjectName}` | Absolute path of a named project in the workspace |
   | `${project_loc}` | Absolute path of the currently selected project |
   | `${project_name}` | Name of the currently selected project |
   | `${resource_loc}` | Absolute path of the currently selected resource |
   | `${resource_name}` | Name of the currently selected resource |
   | `${container_loc}` | Absolute path of the parent container of the selected resource |
   | `${env_var:NAME}` | Value of the OS environment variable `NAME` |
   | `${system_property:NAME}` | Value of the Java system property `NAME` |
   | `${string_prompt:message}` | Prompts the user to enter a string at run time |
   | `${build_type}` | Current build type (`incremental`, `full`, etc.) |

   Full list available via **Run > String Substitution** in Eclipse.

9. **Preference page** — Window > Preferences > Automation; "Default working directory" (default: `${workspace_loc}/..`); "Workflow storage location" (default: `${workspace_loc}/../automation`); "Deploy bundled workflows" button with tooltip.
10. **Bundled workflows** — workflows shipped with the plugin; auto-deployed on first launch; re-deploy via button; what each bundled workflow does.
11. **Sharing workflows between users** — step-by-step: locate the storage folder (see preference page); copy the JSON files to a colleague; colleague drops them into their own storage folder (or uses a shared network path via the preference).
12. **Using workflows from another workspace** — change "Workflow storage location" in the preference page to point at the other workspace's automation folder (e.g., `${workspace_loc}/../other-workspace/automation`); the plugin reads from wherever the preference points.

---

## Testing Strategy

**SWTBot integration tests** (3 new, in `com.example.automation.tests`):

1. `PreferencePageTest` — open Window > Preferences > Automation; assert both string fields are present; assert "Deploy bundled workflows" button exists; assert tooltip text contains "Copies the workflows".
2. `VariableSubstitutionTest` — create a step with `${workspace_loc}` as the `workingDir` config value; run the workflow; assert console output does not contain the literal string `${workspace_loc}` (i.e., substitution occurred).
3. `MultiLineCommandTest` — add a ShellCommandAction step; open Properties View; activate the `command` cell editor; enter a two-line value via `typeText` with a newline; commit with Ctrl+Enter; assert the stored config value contains `\n`.

**Expected test count:** 74 existing + 3 new = **77 tests**.

---

## File Structure

| File | Change |
|---|---|
| `README.md` | **New** — full user documentation |
| `com.example.automation/META-INF/MANIFEST.MF` | Version `1.1.0.qualifier`; add `org.eclipse.core.variables` |
| `com.example.automation/plugin.xml` | Add `preferencePages` + `preferences` extension points |
| `com.example.automation/src/main/java/…/preferences/AutomationPreferences.java` | **New** |
| `com.example.automation/src/main/java/…/preferences/AutomationPreferenceInitializer.java` | **New** |
| `com.example.automation/src/main/java/…/preferences/AutomationPreferencePage.java` | **New** |
| `com.example.automation/src/main/java/…/BundledWorkflowInstaller.java` | **New** |
| `com.example.automation/src/main/java/…/ui/MultiLineTextCellEditor.java` | **New** |
| `com.example.automation/src/main/resources/workflows/*.json` | **New** — bundled sample workflows |
| `com.example.automation/src/main/java/…/api/IActionContext.java` | Add `getWorkingDirectory()` |
| `com.example.automation/src/main/java/…/WorkflowRunner.java` | Variable substitution + working dir injection |
| `com.example.automation/src/main/java/…/actions/ShellCommandAction.java` | Use `context.getWorkingDirectory()` fallback; remove `ResourcesPlugin` import |
| `com.example.automation/src/main/java/…/AutomationView.java` | Construct `WorkflowRepository` with resolved storage preference path on each load/save |
| `com.example.automation/src/main/java/…/Activator.java` | Call `BundledWorkflowInstaller.installIfNeeded()` on start |
| `com.example.automation/src/main/java/…/StepPropertySource.java` | Use `MultiLineTextCellEditor` for `command` property descriptor of ShellCommandAction steps |
| `com.example.automation.feature/feature.xml` | Version `1.1.0.qualifier` |
| `com.example.automation.parent/pom.xml` + module poms | Version `1.1.0-SNAPSHOT` |
| `com.example.automation.tests/src/…/PreferencePageTest.java` | **New** — SWTBot |
| `com.example.automation.tests/src/…/VariableSubstitutionTest.java` | **New** — SWTBot |
| `com.example.automation.tests/src/…/MultiLineCommandTest.java` | **New** — SWTBot |

---

## What Is NOT in Scope

- Variable resolution preview in the Properties View (show the resolved value next to the expression)
- Per-step working directory override via a separate field (the `workingDir` config key on ShellCommandAction already serves this)
- Syntax validation of Eclipse variable expressions in the preference page fields
- Workflow import/export via UI (sharing is manual file copy)
- Filtering or categorisation of bundled vs. user workflows in the Automation View
