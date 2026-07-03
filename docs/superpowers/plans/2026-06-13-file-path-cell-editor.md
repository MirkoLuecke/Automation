# File Path Cell Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a file/directory picker cell editor for path-type config keys in the Properties view, storing paths as Eclipse path variables (`${workspace_loc:/Project}/...`), and resolve those variables in all nine action classes before using the path.

**Architecture:** A new `EclipseVariables` utility wraps `IStringVariableManager.performStringSubstitution()`. A new `PathCellEditor` extends `DialogCellEditor`, opens a native `FileDialog` or `DirectoryDialog`, and relativizes the result using Eclipse path variable prefixes. `StepPropertySource.createConfigDescriptor()` gets two new suffix-based branches dispatching by key name. All nine actions that use a path config key call `EclipseVariables.resolve()` before consuming the value.

**Tech Stack:** Java 17, Eclipse SWT/JFace, `org.eclipse.core.variables` (already in `Require-Bundle`), `org.eclipse.core.resources`, JUnit 4

---

### File Map

| Operation | File |
|-----------|------|
| Create | `com.example.automation/src/main/java/com/example/automation/EclipseVariables.java` |
| Create | `com.example.automation/src/main/java/com/example/automation/PathCellEditor.java` |
| Modify | `com.example.automation/src/main/java/com/example/automation/StepPropertySource.java` |
| Modify | `com.example.automation/src/main/java/com/example/automation/actions/SetMavenSettingsAction.java` |
| Modify | `com.example.automation/src/main/java/com/example/automation/actions/WriteFileAction.java` |
| Modify | `com.example.automation/src/main/java/com/example/automation/actions/SetCodeFormatterAction.java` |
| Modify | `com.example.automation/src/main/java/com/example/automation/actions/SetActiveTargetPlatformAction.java` |
| Modify | `com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java` |
| Modify | `com.example.automation/src/main/java/com/example/automation/actions/GitCheckoutAction.java` |
| Modify | `com.example.automation/src/main/java/com/example/automation/actions/GitCloneAction.java` |
| Modify | `com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java` |
| Modify | `com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java` |
| Create | `com.example.automation.tests/src/main/java/com/example/automation/tests/EclipseVariablesTest.java` |
| Create | `com.example.automation.tests/src/main/java/com/example/automation/tests/PathCellEditorTest.java` |
| Modify | `com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java` |

All paths below are relative to `com.example.automation.parent/`.

---

### Task 1: `EclipseVariables` utility

**Files:**
- Create: `com.example.automation/src/main/java/com/example/automation/EclipseVariables.java`
- Create: `com.example.automation.tests/src/main/java/com/example/automation/tests/EclipseVariablesTest.java`

**Background:**
`org.eclipse.core.variables` is already in `Require-Bundle` — no MANIFEST.MF changes needed. `VariablesPlugin.getDefault().getStringVariableManager().performStringSubstitution(value)` resolves `${workspace_loc}` to the workspace root path, `${workspace_loc:/ProjectName}` to the project's filesystem path, and passes plain strings through unchanged.

---

- [ ] **Step 1: Write the failing test**

Create `com.example.automation.tests/src/main/java/com/example/automation/tests/EclipseVariablesTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.Test;

import com.example.automation.EclipseVariables;

public class EclipseVariablesTest {

    @Test
    public void resolve_noVariables_returnsUnchanged() throws Exception {
        String path = "/absolute/path/to/file.xml";
        assertEquals(path, EclipseVariables.resolve(path));
    }

    @Test
    public void resolve_workspaceLoc_returnsAbsolutePath() throws Exception {
        String wsRoot = ResourcesPlugin.getWorkspace().getRoot()
            .getLocation().toOSString();
        assertEquals(wsRoot, EclipseVariables.resolve("${workspace_loc}"));
    }

    @Test
    public void resolve_workspaceLocSubpath_prependsWorkspaceRoot() throws Exception {
        String wsRoot = ResourcesPlugin.getWorkspace().getRoot()
            .getLocation().toOSString();
        String resolved = EclipseVariables.resolve("${workspace_loc}/sub/path");
        assertTrue("resolved path must start with workspace root",
            resolved.replace('\\', '/').startsWith(wsRoot.replace('\\', '/')));
    }
}
```

- [ ] **Step 2: Note that the test will not compile**

`EclipseVariables` does not exist yet. Expected — proceed to Step 3.

- [ ] **Step 3: Create `EclipseVariables.java`**

Create `com.example.automation/src/main/java/com/example/automation/EclipseVariables.java`:

```java
package com.example.automation;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.VariablesPlugin;

/**
 * Utility for resolving Eclipse string variable references (e.g. {@code ${workspace_loc}})
 * in configuration values before passing them to file I/O operations.
 */
public final class EclipseVariables {

    private EclipseVariables() {}

    /**
     * Resolves Eclipse string variables in {@code value} and returns the substituted result.
     * Values without variable references are returned unchanged. {@code null} or blank
     * values are returned as-is.
     *
     * @param value the string to resolve; may be null or blank
     * @return the resolved string
     * @throws CoreException if a referenced variable is undefined or resolution fails
     */
    public static String resolve(String value) throws CoreException {
        if (value == null || value.isBlank()) return value;
        return VariablesPlugin.getDefault().getStringVariableManager()
            .performStringSubstitution(value);
    }
}
```

- [ ] **Step 4: Verify the build compiles**

Run from `com.example.automation.parent/`:
```
mvn compile -pl com.example.automation -am -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/EclipseVariables.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/EclipseVariablesTest.java
git commit -m "feat: add EclipseVariables utility for resolving path variable references"
```

---

### Task 2: `PathCellEditor` and `StepPropertySource` changes

**Files:**
- Create: `com.example.automation/src/main/java/com/example/automation/PathCellEditor.java`
- Modify: `com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`
- Modify: `com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java`
- Create: `com.example.automation.tests/src/main/java/com/example/automation/tests/PathCellEditorTest.java`

**Background:**

`PathCellEditor` extends `org.eclipse.jface.viewers.DialogCellEditor`. That base class provides a `Label` + `"..."` button composite automatically — you only need to override `createContents(Composite)` (to return the label), `updateContents(Object)` (to refresh the label text), and `openDialogBox(Control)` (to open the dialog and return the new value).

Key design detail: the `DialogCellEditor(Composite parent)` constructor immediately calls `create(parent)`, which calls `createContents()`. That happens before `this.step` and `this.pathType` fields are assigned. This is fine because `createContents()` only creates a `Label` — it does not access `step` or `pathType`. Those fields are used only in `openDialogBox()`, which is called later when the user clicks `"..."`.

`relativize()` is `public static` so the test bundle can call it directly.

The key-suffix check in `StepPropertySource` must be inserted **before** the existing `isMultiLineField` check (it already comes before the final `TextPropertyDescriptor` fallback).

---

- [ ] **Step 1: Write the failing tests**

Create `com.example.automation.tests/src/main/java/com/example/automation/tests/PathCellEditorTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.example.automation.PathCellEditor;
import com.example.automation.model.Step;

public class PathCellEditorTest {

    private static final String PROJECT_NAME = "TestProject_PathCellEditor";
    private IProject testProject;

    @Before
    public void createProject() throws Exception {
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        testProject = ws.getRoot().getProject(PROJECT_NAME);
        if (!testProject.exists())
            testProject.create(new NullProgressMonitor());
        if (!testProject.isOpen())
            testProject.open(new NullProgressMonitor());
    }

    @After
    public void deleteProject() throws Exception {
        if (testProject != null && testProject.exists())
            testProject.delete(true, new NullProgressMonitor());
    }

    private Step stepWith(String projectName) {
        Step step = new Step("test");
        if (projectName != null) step.getConfig().put("projectName", projectName);
        return step;
    }

    @Test
    public void relativize_underProject_usesWorkspaceLocProjectVar() {
        String projectPath = testProject.getLocation().toOSString();
        String filePath = projectPath + File.separator + "src" + File.separator + "formatter.xml";

        String result = PathCellEditor.relativize(filePath, stepWith(PROJECT_NAME));

        assertEquals("${workspace_loc:/" + PROJECT_NAME + "}/src/formatter.xml", result);
    }

    @Test
    public void relativize_underWorkspace_usesWorkspaceLoc() {
        String wsPath = ResourcesPlugin.getWorkspace().getRoot()
            .getLocation().toOSString();
        String filePath = wsPath + File.separator + "OtherProject"
            + File.separator + "settings.xml";

        String result = PathCellEditor.relativize(filePath, stepWith(PROJECT_NAME));

        assertEquals("${workspace_loc}/OtherProject/settings.xml", result);
    }

    @Test
    public void relativize_outsideWorkspace_returnsAbsolute() {
        String wsPath = ResourcesPlugin.getWorkspace().getRoot()
            .getLocation().toOSString();
        // Go one level above the workspace so the path is definitely outside it
        java.nio.file.Path outside = java.nio.file.Paths.get(wsPath)
            .getParent();
        if (outside == null) return; // workspace at root — skip
        String filePath = outside.resolve("outside_file.xml").toString();

        String result = PathCellEditor.relativize(filePath, stepWith(PROJECT_NAME));

        assertEquals(filePath, result);
    }

    @Test
    public void relativize_noProjectName_usesWorkspaceLoc() {
        String wsPath = ResourcesPlugin.getWorkspace().getRoot()
            .getLocation().toOSString();
        String filePath = wsPath + File.separator + "sub" + File.separator + "file.xml";

        String result = PathCellEditor.relativize(filePath, stepWith(null));

        assertEquals("${workspace_loc}/sub/file.xml", result);
    }
}
```

Add these two tests to the existing `StepPropertySourceTest.java` class (inside the class body, after the last existing test method):

```java
@Test
public void filePath_usesPathCellEditor() {
    IAction action = stub("set-code-formatter", Map.of("filePath", ""));
    Step step = new Step("set-code-formatter");
    ActionRegistry reg = new ActionRegistry(List.of(action));
    boolean[] saved = {false};

    IPropertyDescriptor[] descs = src(step, reg, saved).getPropertyDescriptors();

    IPropertyDescriptor filePathDesc = null;
    for (IPropertyDescriptor d : descs) {
        if ("filePath".equals(d.getId())) { filePathDesc = d; break; }
    }
    assertNotNull("filePath descriptor must exist", filePathDesc);
    assertFalse("filePath must not use TextPropertyDescriptor",
        filePathDesc instanceof TextPropertyDescriptor);
}

@Test
public void workingDir_usesPathCellEditor() {
    IAction action = stub("shell-command", Map.of("command", "", "workingDir", ""));
    Step step = new Step("shell-command");
    ActionRegistry reg = new ActionRegistry(List.of(action));
    boolean[] saved = {false};

    IPropertyDescriptor[] descs = src(step, reg, saved).getPropertyDescriptors();

    IPropertyDescriptor workingDirDesc = null;
    for (IPropertyDescriptor d : descs) {
        if ("workingDir".equals(d.getId())) { workingDirDesc = d; break; }
    }
    assertNotNull("workingDir descriptor must exist", workingDirDesc);
    assertFalse("workingDir must not use TextPropertyDescriptor",
        workingDirDesc instanceof TextPropertyDescriptor);
}
```

- [ ] **Step 2: Note that the tests will not compile**

`PathCellEditor` does not exist yet. Expected — proceed to Step 3.

- [ ] **Step 3: Create `PathCellEditor.java`**

Create `com.example.automation/src/main/java/com/example/automation/PathCellEditor.java`:

```java
package com.example.automation;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.DialogCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;

import com.example.automation.model.Step;

/**
 * Cell editor for file or directory path config keys. Opens a native OS dialog
 * and stores the chosen path as an Eclipse path variable reference so that paths
 * inside the workspace are portable across machines.
 *
 * <ul>
 *   <li>Under the step's project → {@code ${workspace_loc:/ProjectName}/relative/path}</li>
 *   <li>Under workspace root → {@code ${workspace_loc}/relative/path}</li>
 *   <li>Outside workspace → absolute path unchanged</li>
 * </ul>
 */
public class PathCellEditor extends DialogCellEditor {

    public enum PathType { FILE, DIRECTORY }

    private final Step step;
    private final PathType pathType;
    private Label label;

    public PathCellEditor(Composite parent, Step step, PathType pathType) {
        super(parent);
        this.step     = step;
        this.pathType = pathType;
    }

    @Override
    protected Control createContents(Composite cell) {
        label = new Label(cell, SWT.LEFT);
        return label;
    }

    @Override
    protected void updateContents(Object value) {
        label.setText(value instanceof String s ? s : "");
    }

    @Override
    protected Object openDialogBox(Control cellEditorWindow) {
        String current    = (String) getValue();
        String filterPath = resolveToFilterPath(current);

        String result;
        if (pathType == PathType.FILE) {
            FileDialog dialog = new FileDialog(cellEditorWindow.getShell(), SWT.OPEN);
            if (filterPath != null) dialog.setFilterPath(filterPath);
            result = dialog.open();
        } else {
            DirectoryDialog dialog = new DirectoryDialog(cellEditorWindow.getShell(), SWT.NONE);
            if (filterPath != null) dialog.setFilterPath(filterPath);
            result = dialog.open();
        }

        if (result == null) return getValue();
        return relativize(result, step);
    }

    private String resolveToFilterPath(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String resolved = EclipseVariables.resolve(value);
            if (pathType == PathType.FILE) {
                java.io.File f = new java.io.File(resolved);
                String parent = f.getParent();
                return parent != null ? parent : resolved;
            }
            return resolved;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Relativizes {@code absolutePath} against the step's project or the workspace root,
     * prefixing the result with the appropriate Eclipse path variable.
     * Public to allow direct testing from the test bundle.
     */
    public static String relativize(String absolutePath, Step step) {
        Path selected = Paths.get(absolutePath);
        String projectName = step.getConfig().get("projectName");

        if (projectName != null && !projectName.isBlank()) {
            IProject project = ResourcesPlugin.getWorkspace().getRoot()
                .getProject(projectName);
            if (project.exists()) {
                IPath projectLoc = project.getLocation();
                if (projectLoc != null) {
                    Path projectPath = projectLoc.toFile().toPath();
                    try {
                        Path rel = projectPath.relativize(selected);
                        String relStr = rel.toString().replace('\\', '/');
                        if (!relStr.startsWith("../") && !relStr.equals("..")) {
                            return "${workspace_loc:/" + projectName + "}/" + relStr;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        IPath wsLoc = ResourcesPlugin.getWorkspace().getRoot().getLocation();
        if (wsLoc != null) {
            Path wsPath = wsLoc.toFile().toPath();
            try {
                Path rel = wsPath.relativize(selected);
                String relStr = rel.toString().replace('\\', '/');
                if (!relStr.startsWith("../") && !relStr.equals("..")) {
                    return "${workspace_loc}/" + relStr;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        return absolutePath;
    }
}
```

- [ ] **Step 4: Add two new branches to `StepPropertySource.createConfigDescriptor()`**

The current `createConfigDescriptor` method spans lines 97–115 of
`com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`.

Replace it with the following (adds `isFileField`/`isDirField` branches before the `isMultiLineField` check, and adds two new private helper methods):

```java
private PropertyDescriptor createConfigDescriptor(String key) {
    if ("projectName".equals(key)) {
        return new PropertyDescriptor(key, key) {
            @Override
            public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                return new ProjectComboBoxCellEditor(parent);
            }
        };
    }
    if (isFileField(key)) {
        return new PropertyDescriptor(key, key) {
            @Override
            public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                return new PathCellEditor(parent, step, PathCellEditor.PathType.FILE);
            }
        };
    }
    if (isDirField(key)) {
        return new PropertyDescriptor(key, key) {
            @Override
            public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                return new PathCellEditor(parent, step, PathCellEditor.PathType.DIRECTORY);
            }
        };
    }
    if (isMultiLineField(step.getActionId(), key)) {
        return new PropertyDescriptor(key, key) {
            @Override
            public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                return new MultiLineTextCellEditor(parent);
            }
        };
    }
    return new TextPropertyDescriptor(key, key);
}

private static boolean isFileField(String key) {
    String lower = key.toLowerCase(java.util.Locale.ROOT);
    return lower.endsWith("file") || lower.endsWith("path");
}

private static boolean isDirField(String key) {
    return key.toLowerCase(java.util.Locale.ROOT).endsWith("dir");
}
```

Also add `import java.util.Locale;` to the import block at the top of `StepPropertySource.java` (if not already present — check before adding).

- [ ] **Step 5: Verify the build compiles**

Run from `com.example.automation.parent/`:
```
mvn compile -pl com.example.automation -am -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathCellEditor.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathCellEditorTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java
git commit -m "feat: add PathCellEditor for file/directory path config keys"
```

---

### Task 3: Add `EclipseVariables.resolve()` to all nine actions

**Files:** The nine action classes listed in the File Map above.

**Background:**

For each action, call `EclipseVariables.resolve()` on the path value *after* the blank/null check and *before* using the value in file I/O (i.e., before passing it to `new File(...)` or using it in a command list). This ensures that plain absolute-path configs stored before this change continue to work unchanged, while new configs using `${workspace_loc:...}` are resolved correctly.

Each change requires adding `import com.example.automation.EclipseVariables;` to the action's imports (if not already present). The `EclipseVariables.resolve()` method throws `CoreException` which extends `Exception`, so the existing `throws Exception` on `execute()` already covers it.

---

- [ ] **Step 1: Update `SetCodeFormatterAction`**

In `com.example.automation/src/main/java/com/example/automation/actions/SetCodeFormatterAction.java`, add the import:
```java
import com.example.automation.EclipseVariables;
```

In `execute()`, replace:
```java
String filePath = config.getOrDefault("filePath", "");
if (filePath.isBlank()) throw new IllegalArgumentException("filePath must not be blank");

context.setProgress(0);

File file = new File(filePath);
```
with:
```java
String filePath = config.getOrDefault("filePath", "");
if (filePath.isBlank()) throw new IllegalArgumentException("filePath must not be blank");

context.setProgress(0);

filePath = EclipseVariables.resolve(filePath);
File file = new File(filePath);
```

- [ ] **Step 2: Update `SetMavenSettingsAction`**

In `com.example.automation/src/main/java/com/example/automation/actions/SetMavenSettingsAction.java`, add the import:
```java
import com.example.automation.EclipseVariables;
```

In `execute()`, replace:
```java
String filePath = config.getOrDefault("filePath", "");
if (filePath.isBlank()) throw new IllegalArgumentException("filePath must not be blank");
context.setProgress(0);
MavenPlugin.getMavenConfiguration().setUserSettingsFile(filePath);
context.getStdout().println("Maven user settings set to: "
    + Path.of(filePath).toAbsolutePath());
```
with:
```java
String filePath = config.getOrDefault("filePath", "");
if (filePath.isBlank()) throw new IllegalArgumentException("filePath must not be blank");
context.setProgress(0);
filePath = EclipseVariables.resolve(filePath);
MavenPlugin.getMavenConfiguration().setUserSettingsFile(filePath);
context.getStdout().println("Maven user settings set to: "
    + Path.of(filePath).toAbsolutePath());
```

- [ ] **Step 3: Update `WriteFileAction`**

In `com.example.automation/src/main/java/com/example/automation/actions/WriteFileAction.java`, add the import:
```java
import com.example.automation.EclipseVariables;
```

In `execute()`, replace:
```java
String filePath = config.getOrDefault("filePath", "");
String content  = config.getOrDefault("content", "");
context.setProgress(0);
writeFile(filePath, content);
context.getStdout().println("Written: " + Path.of(filePath).toAbsolutePath());
```
with:
```java
String filePath = config.getOrDefault("filePath", "");
String content  = config.getOrDefault("content", "");
context.setProgress(0);
filePath = EclipseVariables.resolve(filePath);
writeFile(filePath, content);
context.getStdout().println("Written: " + Path.of(filePath).toAbsolutePath());
```

- [ ] **Step 4: Update `SetActiveTargetPlatformAction`**

In `com.example.automation/src/main/java/com/example/automation/actions/SetActiveTargetPlatformAction.java`, add the import:
```java
import com.example.automation.EclipseVariables;
```

In `execute()`, replace:
```java
String targetPath = config.get("targetFile");
if (targetPath == null || targetPath.isBlank())
    throw new IllegalArgumentException("targetFile must not be blank");

File targetFile = new File(targetPath);
```
with:
```java
String targetPath = config.get("targetFile");
if (targetPath == null || targetPath.isBlank())
    throw new IllegalArgumentException("targetFile must not be blank");
targetPath = EclipseVariables.resolve(targetPath);
File targetFile = new File(targetPath);
```

- [ ] **Step 5: Update `ImportMavenProjectAction`**

In `com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java`, add the import:
```java
import com.example.automation.EclipseVariables;
```

In `execute()`, replace:
```java
String pomPath = config.get("pomPath");
if (pomPath == null || pomPath.isBlank())
    throw new IllegalArgumentException("pomPath must not be blank");

File pomFile = new File(pomPath);
```
with:
```java
String pomPath = config.get("pomPath");
if (pomPath == null || pomPath.isBlank())
    throw new IllegalArgumentException("pomPath must not be blank");
pomPath = EclipseVariables.resolve(pomPath);
File pomFile = new File(pomPath);
```

- [ ] **Step 6: Update `GitCheckoutAction`**

In `com.example.automation/src/main/java/com/example/automation/actions/GitCheckoutAction.java`, add the import:
```java
import com.example.automation.EclipseVariables;
```

In `execute()`, replace:
```java
String repoDir = config.get("repoDir");
String branch  = config.get("branch");

if (repoDir == null || repoDir.isBlank())
    throw new IllegalArgumentException("repoDir must not be blank");
if (branch == null || branch.isBlank())
    throw new IllegalArgumentException("branch must not be blank");

ProcessRunner.run(
    List.of("git", "-C", repoDir, "checkout", branch),
    null, context);
```
with:
```java
String repoDir = config.get("repoDir");
String branch  = config.get("branch");

if (repoDir == null || repoDir.isBlank())
    throw new IllegalArgumentException("repoDir must not be blank");
if (branch == null || branch.isBlank())
    throw new IllegalArgumentException("branch must not be blank");
repoDir = EclipseVariables.resolve(repoDir);
ProcessRunner.run(
    List.of("git", "-C", repoDir, "checkout", branch),
    null, context);
```

- [ ] **Step 7: Update `GitCloneAction`**

In `com.example.automation/src/main/java/com/example/automation/actions/GitCloneAction.java`, add the import:
```java
import com.example.automation.EclipseVariables;
```

In `execute()`, replace:
```java
String url       = config.get("url");
String targetDir = config.get("targetDir");
String branch    = config.getOrDefault("branch", "");

if (url == null || url.isBlank())
    throw new IllegalArgumentException("url must not be blank");
if (targetDir == null || targetDir.isBlank())
    throw new IllegalArgumentException("targetDir must not be blank");

List<String> cmd = new ArrayList<>(List.of("git", "clone"));
```
with:
```java
String url       = config.get("url");
String targetDir = config.get("targetDir");
String branch    = config.getOrDefault("branch", "");

if (url == null || url.isBlank())
    throw new IllegalArgumentException("url must not be blank");
if (targetDir == null || targetDir.isBlank())
    throw new IllegalArgumentException("targetDir must not be blank");
targetDir = EclipseVariables.resolve(targetDir);
List<String> cmd = new ArrayList<>(List.of("git", "clone"));
```

- [ ] **Step 8: Update `ShellCommandAction`**

In `com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java`, add the import:
```java
import com.example.automation.EclipseVariables;
```

In `execute()`, replace:
```java
String workingDir = config.getOrDefault("workingDir", "");

List<String> cmd = buildCommand(command);

File dir = workingDir.isBlank()
    ? new File(context.getWorkingDirectory())
    : new File(workingDir);
```
with:
```java
String workingDir = config.getOrDefault("workingDir", "");

List<String> cmd = buildCommand(command);

File dir = workingDir.isBlank()
    ? new File(context.getWorkingDirectory())
    : new File(EclipseVariables.resolve(workingDir));
```

- [ ] **Step 9: Update `MavenRunWithProgressAction`**

In `com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java`, add the import:
```java
import com.example.automation.EclipseVariables;
```

In `execute()`, replace:
```java
String workingDir = config.getOrDefault("workingDir", "");
File dir = workingDir.isBlank()
    ? new File(context.getWorkingDirectory())
    : new File(workingDir);
```
with:
```java
String workingDir = config.getOrDefault("workingDir", "");
File dir = workingDir.isBlank()
    ? new File(context.getWorkingDirectory())
    : new File(EclipseVariables.resolve(workingDir));
```

- [ ] **Step 10: Verify the build compiles**

Run from `com.example.automation.parent/`:
```
mvn compile -pl com.example.automation -am -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 11: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetCodeFormatterAction.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetMavenSettingsAction.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/WriteFileAction.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetActiveTargetPlatformAction.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/GitCheckoutAction.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/GitCloneAction.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java
git commit -m "feat: resolve Eclipse path variables in all path-type action config keys"
```
