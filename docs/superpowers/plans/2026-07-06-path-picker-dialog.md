# Path Picker Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the raw OS file/directory dialog in `PathCellEditor` with a single integrated `PathPickerDialog` that combines a Browse button, an editable text field, and a live-updating suggestions table showing all Eclipse variable forms for the chosen path.

**Architecture:** `PathVariableSuggestions` (pure Java, no UI) computes ranked `Suggestion` objects from an absolute path and the Eclipse variable registry. `PathPickerDialog` (JFace `TitleAreaDialog`) owns the text field, Browse button, and suggestions `TableViewer`; it calls `PathVariableSuggestions` on every text change. `PathCellEditor.openDialogBox()` opens `PathPickerDialog` and drops the now-deleted `relativize()` method.

**Tech Stack:** Eclipse JFace (`TitleAreaDialog`, `TableViewer`), SWT, `org.eclipse.core.variables.IStringVariableManager`, `org.eclipse.core.resources.ResourcesPlugin`, JUnit 4.

---

## Files

| Action | Path |
|--------|------|
| Create | `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathVariableSuggestions.java` |
| Create | `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathPickerDialog.java` |
| Modify | `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathCellEditor.java` |
| Modify | `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java` |
| Create | `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathVariableSuggestionsTest.java` |
| Create | `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathPickerDialogTest.java` |
| Delete | `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathCellEditorTest.java` |

---

## Task 1: `PathVariableSuggestions` utility (TDD)

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathVariableSuggestionsTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathVariableSuggestions.java`

- [ ] **Step 1: Create the failing test file**

Create `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathVariableSuggestionsTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.example.automation.PathVariableSuggestions;
import com.example.automation.PathVariableSuggestions.Suggestion;

public class PathVariableSuggestionsTest {

    private static final String PROJECT_NAME = "TestProject_PathVarSugg";
    private IProject testProject;
    private IStringVariableManager mgr;

    @Before
    public void setUp() throws Exception {
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        testProject = ws.getRoot().getProject(PROJECT_NAME);
        if (!testProject.exists()) testProject.create(new NullProgressMonitor());
        if (!testProject.isOpen()) testProject.open(new NullProgressMonitor());
        mgr = VariablesPlugin.getDefault().getStringVariableManager();
    }

    @After
    public void tearDown() throws Exception {
        if (testProject != null && testProject.exists())
            testProject.delete(true, new NullProgressMonitor());
    }

    private String projectAbsPath() {
        return testProject.getLocation().toFile().getAbsolutePath();
    }

    private String wsAbsPath() {
        return ResourcesPlugin.getWorkspace().getRoot().getLocation()
            .toFile().getAbsolutePath();
    }

    @Test
    public void compute_pathUnderProject_returnsProjectVariableFirst() {
        String path = projectAbsPath() + File.separator + "src" + File.separator + "main";
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            path, new IProject[]{testProject}, mgr);
        assertFalse("Expected at least one suggestion", suggestions.isEmpty());
        assertEquals("${workspace_loc:/" + PROJECT_NAME + "}/src/main",
            suggestions.get(0).variableForm);
    }

    @Test
    public void compute_pathExactlyProject_returnsVariableWithNoSuffix() {
        String path = projectAbsPath();
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            path, new IProject[]{testProject}, mgr);
        assertFalse(suggestions.isEmpty());
        assertEquals("${workspace_loc:/" + PROJECT_NAME + "}",
            suggestions.get(0).variableForm);
    }

    @Test
    public void compute_pathUnderWorkspace_notUnderProject_returnsWorkspaceLocVar() {
        String path = wsAbsPath() + File.separator + "SomeFolder" + File.separator + "f.xml";
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            path, new IProject[0], mgr);
        assertTrue("Expected ${workspace_loc} suggestion",
            suggestions.stream().anyMatch(
                s -> s.variableForm.equals("${workspace_loc}/SomeFolder/f.xml")));
    }

    @Test
    public void compute_pathOutsideWorkspace_noWorkspaceLocSuggestion() {
        java.nio.file.Path outside = java.nio.file.Paths.get(wsAbsPath()).getParent();
        if (outside == null) return; // workspace is at filesystem root, skip
        String path = outside.resolve("outside.xml").toString();
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            path, new IProject[0], mgr);
        assertTrue("No workspace_loc suggestion expected for path outside workspace",
            suggestions.stream().noneMatch(
                s -> s.variableForm.startsWith("${workspace_loc}")));
    }

    @Test
    public void compute_absoluteAlwaysLast() {
        String path = projectAbsPath() + File.separator + "build.xml";
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            path, new IProject[]{testProject}, mgr);
        assertTrue(suggestions.size() >= 2);
        Suggestion last = suggestions.get(suggestions.size() - 1);
        assertFalse("Last suggestion must not be a variable form",
            last.variableForm.contains("${"));
    }

    @Test
    public void compute_emptyPath_returnsEmptyList() {
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            "", new IProject[0], mgr);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    public void compute_nullPath_returnsEmptyList() {
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            null, new IProject[0], mgr);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    public void compute_closedProject_notIncluded() {
        // closed projects are excluded from suggestions
        try {
            testProject.close(new NullProgressMonitor());
            String path = projectAbsPath() + File.separator + "pom.xml";
            List<Suggestion> suggestions = PathVariableSuggestions.compute(
                path, new IProject[]{testProject}, mgr);
            assertTrue("Closed project must not produce a suggestion",
                suggestions.stream().noneMatch(
                    s -> s.variableForm.contains(PROJECT_NAME)));
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
cd com.example.automation.parent
mvn clean verify -B 2>&1 | grep -E "PathVariableSugg|ERROR|FAIL"
```

Expected: compilation error — `PathVariableSuggestions` does not exist yet.

- [ ] **Step 3: Create `PathVariableSuggestions.java`**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathVariableSuggestions.java`:

```java
package com.example.automation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

public final class PathVariableSuggestions {

    public static final class Suggestion {
        public final String variableForm;
        public final String resolvedPath;
        public final String description;

        public Suggestion(String variableForm, String resolvedPath, String description) {
            this.variableForm = variableForm;
            this.resolvedPath = resolvedPath;
            this.description = description;
        }
    }

    private PathVariableSuggestions() {}

    /** Convenience overload that uses the live Eclipse registry and all open projects. */
    public static List<Suggestion> compute(String absolutePath) {
        IStringVariableManager mgr = VariablesPlugin.getDefault().getStringVariableManager();
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        return compute(absolutePath, projects, mgr);
    }

    /**
     * Computes ranked variable-form suggestions for {@code absolutePath}.
     * Order: per-project workspace matches (most-specific first), workspace root,
     * other registered Eclipse variables (alphabetical), absolute path last.
     */
    public static List<Suggestion> compute(
            String absolutePath, IProject[] projects, IStringVariableManager mgr) {
        if (absolutePath == null || absolutePath.isBlank()) return List.of();

        Path target = Paths.get(absolutePath).toAbsolutePath().normalize();
        List<Suggestion> result = new ArrayList<>();

        // 1. Per-project ${workspace_loc:/Name} — most specific
        List<Suggestion> projectSuggestions = new ArrayList<>();
        for (IProject project : projects) {
            if (!project.isOpen()) continue;
            org.eclipse.core.runtime.IPath loc = project.getLocation();
            if (loc == null) continue;
            Path projectPath = loc.toFile().toPath().toAbsolutePath().normalize();
            String varForm = buildVariableForm(
                "${workspace_loc:/" + project.getName() + "}", projectPath, target);
            if (varForm != null)
                projectSuggestions.add(new Suggestion(
                    varForm, target.toString(), "project " + project.getName()));
        }
        // Sort by variable form length descending: longer = more specific prefix
        projectSuggestions.sort((a, b) -> b.variableForm.length() - a.variableForm.length());
        result.addAll(projectSuggestions);

        // 2. ${workspace_loc} — workspace root
        try {
            String wsResolved = mgr.performStringSubstitution("${workspace_loc}", false);
            if (wsResolved != null && !wsResolved.isBlank()) {
                Path wsPath = Paths.get(wsResolved).toAbsolutePath().normalize();
                String varForm = buildVariableForm("${workspace_loc}", wsPath, target);
                if (varForm != null)
                    result.add(new Suggestion(varForm, target.toString(), "workspace root"));
            }
        } catch (Exception ignored) {}

        // 3. All other registered dynamic variables (no argument, skipping workspace_loc)
        for (var dynVar : mgr.getDynamicVariables()) {
            String name = dynVar.getName();
            if ("workspace_loc".equals(name)) continue;
            try {
                String resolved = mgr.performStringSubstitution("${" + name + "}", false);
                if (resolved == null || resolved.isBlank()) continue;
                Path varPath = Paths.get(resolved).toAbsolutePath().normalize();
                String varForm = buildVariableForm("${" + name + "}", varPath, target);
                if (varForm != null)
                    result.add(new Suggestion(varForm, target.toString(), name));
            } catch (Exception ignored) {}
        }

        // 4. User-defined value variables
        for (var valVar : mgr.getValueVariables()) {
            try {
                String resolved = mgr.performStringSubstitution(
                    "${" + valVar.getName() + "}", false);
                if (resolved == null || resolved.isBlank()) continue;
                Path varPath = Paths.get(resolved).toAbsolutePath().normalize();
                String varForm = buildVariableForm("${" + valVar.getName() + "}", varPath, target);
                if (varForm != null)
                    result.add(new Suggestion(varForm, target.toString(), valVar.getName()));
            } catch (Exception ignored) {}
        }

        // 5. Absolute path — always last
        result.add(new Suggestion(target.toString(), target.toString(), "absolute path"));

        return result;
    }

    /** Returns null if {@code target} does not start with {@code base}. */
    static String buildVariableForm(String variableExpr, Path base, Path target) {
        if (!target.startsWith(base)) return null;
        if (target.equals(base)) return variableExpr;
        Path relative = base.relativize(target);
        return variableExpr + "/" + relative.toString().replace('\\', '/');
    }
}
```

- [ ] **Step 4: Run tests and verify they pass**

```
cd com.example.automation.parent
mvn clean verify -B 2>&1 | grep -E "PathVariableSugg|Tests run|BUILD"
```

Expected: `PathVariableSuggestionsTest` — 8 tests, 0 failures. BUILD SUCCESS.

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathVariableSuggestions.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathVariableSuggestionsTest.java
git commit -m "feat: add PathVariableSuggestions utility for Eclipse variable path suggestions"
```

---

## Task 2: `PathPickerDialog`

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathPickerDialog.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathPickerDialogTest.java`

- [ ] **Step 1: Create the test file first**

Create `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathPickerDialogTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.Test;

import com.example.automation.PathCellEditor.PathType;
import com.example.automation.PathPickerDialog;

public class PathPickerDialogTest {

    @Test
    public void dialog_opensWithoutError() {
        boolean[] created = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            PathPickerDialog d = new PathPickerDialog(shell, "", PathType.DIRECTORY);
            d.setBlockOnOpen(false);
            d.open();
            created[0] = d.getShell() != null && !d.getShell().isDisposed();
            d.close();
            shell.dispose();
        });
        assertTrue("Dialog shell must be created", created[0]);
    }

    @Test
    public void dialog_cancelYieldsNullResult() {
        String[] result = {"not-null"};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            PathPickerDialog d = new PathPickerDialog(shell, "/some/path", PathType.DIRECTORY);
            d.setBlockOnOpen(false);
            d.open();
            d.close(); // close without pressing OK — result stays null
            result[0] = d.getResult();
            shell.dispose();
        });
        assertNull("Closing without OK must yield null result", result[0]);
    }

    @Test
    public void dialog_opensWithVariableValueWithoutError() {
        boolean[] created = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            // Initial value contains a variable — must not throw during suggestions refresh
            PathPickerDialog d = new PathPickerDialog(
                shell, "${workspace_loc}/myrepo", PathType.DIRECTORY);
            d.setBlockOnOpen(false);
            d.open();
            created[0] = d.getShell() != null && !d.getShell().isDisposed();
            d.close();
            shell.dispose();
        });
        assertTrue("Dialog must open cleanly even with variable initial value", created[0]);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
cd com.example.automation.parent
mvn clean verify -B 2>&1 | grep -E "PathPickerDialog|ERROR|FAIL"
```

Expected: compilation error — `PathPickerDialog` does not exist yet.

- [ ] **Step 3: Create `PathPickerDialog.java`**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathPickerDialog.java`:

```java
package com.example.automation;

import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.example.automation.PathCellEditor.PathType;
import com.example.automation.PathVariableSuggestions.Suggestion;

public class PathPickerDialog extends TitleAreaDialog {

    private final String initialValue;
    private final PathType pathType;
    private Text textField;
    private TableViewer suggestionsViewer;
    private String result;

    public PathPickerDialog(Shell parent, String initialValue, PathType pathType) {
        super(parent);
        this.initialValue = initialValue != null ? initialValue : "";
        this.pathType = pathType;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    public void create() {
        super.create();
        setTitle("Select Path");
        setMessage("Browse for a path or type it directly. "
            + "Click a suggestion to use a variable form.");
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Select Path");
    }

    @Override
    protected boolean isResizable() { return true; }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);

        // Row: text field + Browse button
        Composite row = new Composite(area, SWT.NONE);
        GridLayout rowLayout = new GridLayout(2, false);
        rowLayout.marginWidth = 5;
        rowLayout.marginHeight = 5;
        row.setLayout(rowLayout);
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        textField = new Text(row, SWT.BORDER | SWT.SINGLE);
        textField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        textField.setText(initialValue);

        Button browseButton = new Button(row, SWT.PUSH);
        browseButton.setText("Browse…");
        browseButton.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                String picked = openNativeDialog();
                if (picked != null) textField.setText(picked);
            }
        });

        // Suggestions table
        suggestionsViewer = new TableViewer(area,
            SWT.FULL_SELECTION | SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        GridData tableGd = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableGd.heightHint = 150;
        suggestionsViewer.getTable().setLayoutData(tableGd);
        suggestionsViewer.getTable().setHeaderVisible(true);
        suggestionsViewer.getTable().setLinesVisible(true);
        suggestionsViewer.setContentProvider(ArrayContentProvider.getInstance());

        TableViewerColumn varCol = new TableViewerColumn(suggestionsViewer, SWT.NONE);
        varCol.getColumn().setText("Variable Form");
        varCol.getColumn().setWidth(370);
        varCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return ((Suggestion) element).variableForm;
            }
        });

        TableViewerColumn descCol = new TableViewerColumn(suggestionsViewer, SWT.NONE);
        descCol.getColumn().setText("Description");
        descCol.getColumn().setWidth(160);
        descCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return ((Suggestion) element).description;
            }
        });

        suggestionsViewer.addSelectionChangedListener(e -> {
            IStructuredSelection sel = suggestionsViewer.getStructuredSelection();
            if (!sel.isEmpty())
                textField.setText(((Suggestion) sel.getFirstElement()).variableForm);
        });

        textField.addModifyListener(ev -> refreshSuggestions(textField.getText()));

        refreshSuggestions(initialValue);
        return area;
    }

    private void refreshSuggestions(String text) {
        String absolute = resolveToAbsolute(text);
        List<Suggestion> suggestions = absolute != null
            ? PathVariableSuggestions.compute(absolute)
            : List.of();
        suggestionsViewer.setInput(suggestions);
    }

    private String resolveToAbsolute(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String resolved = EclipseVariables.resolve(value);
            return Paths.get(resolved).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            try {
                java.io.File f = new java.io.File(value);
                return f.isAbsolute() ? f.getAbsolutePath() : null;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String openNativeDialog() {
        String filterPath = resolveToAbsolute(textField.getText());
        if (pathType == PathType.FILE) {
            if (filterPath != null) {
                java.io.File f = new java.io.File(filterPath);
                if (f.isFile()) filterPath = f.getParent();
            }
            FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
            if (filterPath != null) dialog.setFilterPath(filterPath);
            return dialog.open();
        } else {
            DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.NONE);
            if (filterPath != null) dialog.setFilterPath(filterPath);
            return dialog.open();
        }
    }

    @Override
    protected void okPressed() {
        result = textField.getText();
        super.okPressed();
    }

    /** Returns the path value entered by the user, or {@code null} if cancelled. */
    public String getResult() { return result; }
}
```

- [ ] **Step 4: Run tests and verify they pass**

```
cd com.example.automation.parent
mvn clean verify -B 2>&1 | grep -E "PathPickerDialog|Tests run|BUILD"
```

Expected: `PathPickerDialogTest` — 3 tests, 0 failures. BUILD SUCCESS.

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathPickerDialog.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathPickerDialogTest.java
git commit -m "feat: add PathPickerDialog with live variable suggestions table"
```

---

## Task 3: Wire `PathCellEditor` to `PathPickerDialog`, clean up `StepPropertySource` and tests

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathCellEditor.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`
- Delete: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathCellEditorTest.java`

- [ ] **Step 1: Replace `PathCellEditor.java` entirely**

Overwrite `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathCellEditor.java` with:

```java
package com.example.automation;

import org.eclipse.jface.viewers.DialogCellEditor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

/**
 * Cell editor for file or directory path config keys. Opens {@link PathPickerDialog}
 * which lets the user browse for a path and optionally apply an Eclipse variable form
 * (e.g. {@code ${workspace_loc:/ProjectName}/relative/path}).
 */
public class PathCellEditor extends DialogCellEditor {

    public enum PathType { FILE, DIRECTORY }

    private final PathType pathType;
    private Label label;

    public PathCellEditor(Composite parent, PathType pathType) {
        super(parent);
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
        PathPickerDialog dialog = new PathPickerDialog(
            cellEditorWindow.getShell(), (String) getValue(), pathType);
        if (dialog.open() == Window.OK) return dialog.getResult();
        return null;
    }
}
```

- [ ] **Step 2: Update `StepPropertySource.java` to remove the `step` argument from `PathCellEditor` calls**

In `StepPropertySource.java`, find the two `PathCellEditor` instantiation sites (around lines 161 and 169) and change both `new PathCellEditor(parent, step, PathCellEditor.PathType.DIRECTORY)` and `new PathCellEditor(parent, step, PathCellEditor.PathType.FILE)` to remove the `step` argument:

```java
// Before (line ~162):
return new PathCellEditor(parent, step, PathCellEditor.PathType.DIRECTORY);

// After:
return new PathCellEditor(parent, PathCellEditor.PathType.DIRECTORY);
```

```java
// Before (line ~169):
return new PathCellEditor(parent, step, PathCellEditor.PathType.FILE);

// After:
return new PathCellEditor(parent, PathCellEditor.PathType.FILE);
```

- [ ] **Step 3: Delete `PathCellEditorTest.java`**

The 4 `relativize_*` tests in this file tested `PathCellEditor.relativize()` which no longer exists. The equivalent coverage is now in `PathVariableSuggestionsTest`. Delete the file:

```
git rm "com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/PathCellEditorTest.java"
```

- [ ] **Step 4: Run the full test suite**

```
cd com.example.automation.parent
mvn clean verify -B 2>&1 | tail -20
```

Expected: `Tests run: N, Failures: 0, Errors: 0` (total count increases by the new tests minus the 4 deleted ones). BUILD SUCCESS.

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/PathCellEditor.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java
git commit -m "feat: wire PathCellEditor to PathPickerDialog, remove relativize()"
```

---

## Task 4: Version bump 1.9.0 → 1.10.0

**Files:** 9 version files listed below.

- [ ] **Step 1: Bump all version strings**

Run from the repo root (`automation-plugin/`):

```powershell
@(
  'com.example.automation.parent\pom.xml',
  'com.example.automation.parent\com.example.automation\pom.xml',
  'com.example.automation.parent\com.example.automation.tests\pom.xml',
  'com.example.automation.parent\com.example.automation.feature\pom.xml',
  'com.example.automation.parent\com.example.automation.site\pom.xml',
  'com.example.automation.parent\com.example.automation.coverage\pom.xml',
  'com.example.automation.parent\com.example.automation\META-INF\MANIFEST.MF',
  'com.example.automation.parent\com.example.automation.tests\META-INF\MANIFEST.MF',
  'com.example.automation.parent\com.example.automation.feature\feature.xml'
) | ForEach-Object {
  $content = Get-Content $_ -Raw
  $updated = $content -replace '1\.9\.0', '1.10.0'
  if ($content -ne $updated) {
    Set-Content $_ $updated -NoNewline
    Write-Host "Updated: $_"
  }
}
```

Expected output: all 9 files reported as "Updated".

- [ ] **Step 2: Verify version strings**

```
grep -r "1\.9\.0" com.example.automation.parent --include="pom.xml" --include="MANIFEST.MF" --include="feature.xml"
```

Expected: no output (all replaced).

- [ ] **Step 3: Run the full test suite**

```
cd com.example.automation.parent
mvn clean verify -B 2>&1 | tail -15
```

Expected: BUILD SUCCESS, `1.10.0-SNAPSHOT` in the reactor summary.

- [ ] **Step 4: Commit and push**

```
git add com.example.automation.parent/pom.xml
git add com.example.automation.parent/com.example.automation/pom.xml
git add com.example.automation.parent/com.example.automation.tests/pom.xml
git add com.example.automation.parent/com.example.automation.feature/pom.xml
git add com.example.automation.parent/com.example.automation.site/pom.xml
git add com.example.automation.parent/com.example.automation.coverage/pom.xml
git add com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF
git add com.example.automation.parent/com.example.automation.tests/META-INF/MANIFEST.MF
git add com.example.automation.parent/com.example.automation.feature/feature.xml
git add com.example.automation.parent/local-repo/p2/automation-plugin/
git commit -m "chore: bump version to 1.10.0 for PathPickerDialog feature"
git push
```
