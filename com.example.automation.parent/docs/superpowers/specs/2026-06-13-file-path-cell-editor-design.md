# File Path Cell Editor — Design Spec

**Goal:** Add a file/directory picker cell editor for path-type config keys in the Properties view, storing results as Eclipse path variables (`${workspace_loc:/Project}/...`), and resolve those variables in all actions before using the path.

---

## Scope

**In scope:**
- New `PathCellEditor` extending `DialogCellEditor` — opens `FileDialog` or `DirectoryDialog` based on mode
- New `EclipseVariables` utility — static `resolve(String)` wrapping `IStringVariableManager`
- `StepPropertySource.createConfigDescriptor()` — two new branches dispatching by key-name suffix
- Nine existing actions updated to call `EclipseVariables.resolve()` before using their path values

**Out of scope:**
- Custom path variable definitions
- Project-level (not workspace-level) preference storage
- Resolving variables anywhere other than the nine action classes listed below

---

## Key-Suffix Convention

`StepPropertySource.createConfigDescriptor()` dispatches by the lowercase suffix of the config key name (using `Locale.ROOT`):

| Suffix | Mode | Dialog |
|--------|------|--------|
| `file` or `path` | `FILE` | `FileDialog(SWT.OPEN)` |
| `dir` | `DIRECTORY` | `DirectoryDialog` |

Existing keys covered:

| Key | Action | Mode |
|-----|--------|------|
| `filePath` | `SetMavenSettingsAction`, `WriteFileAction`, `SetCodeFormatterAction` | FILE |
| `targetFile` | `SetActiveTargetPlatformAction` | FILE |
| `pomPath` | `ImportMavenProjectAction` | FILE |
| `repoDir` | `GitCheckoutAction` | DIRECTORY |
| `targetDir` | `GitCloneAction` | DIRECTORY |
| `workingDir` | `ShellCommandAction`, `MavenRunWithProgressAction` | DIRECTORY |

---

## Architecture

### New: `EclipseVariables`

**File:** `com.example.automation/src/main/java/com/example/automation/EclipseVariables.java`

Single utility class with one public static method:

```java
public static String resolve(String value) throws CoreException
```

Delegates to `VariablesPlugin.getDefault().getStringVariableManager().performStringSubstitution(value)`. If the value contains no variable references it passes through unchanged, so existing absolute-path configs continue to work without migration.

Requires `org.eclipse.core.variables` added to `Require-Bundle` in `META-INF/MANIFEST.MF`.

---

### New: `PathCellEditor`

**File:** `com.example.automation/src/main/java/com/example/automation/PathCellEditor.java`

Extends `org.eclipse.jface.viewers.DialogCellEditor`. Receives a `Step` and a `PathType` at construction.

```java
public enum PathType { FILE, DIRECTORY }
```

**`openDialogBox(Control cellEditorWindow)`:**
1. Resolves the current stored value to an absolute path via `EclipseVariables.resolve()` (for the dialog's initial filter path).
2. Opens `FileDialog(SWT.OPEN)` or `DirectoryDialog` depending on `PathType`.
3. If the user cancels, returns the existing value unchanged.
4. Passes the selected absolute path to `relativize()` and returns the result.

**`relativize(String absolutePath)` (package-private static — testable without Display):**

Uses `step.getConfig().get("projectName")` read at call time (live, not captured at construction).

1. If `projectName` is set and the path is under that project's location:
   → `${workspace_loc:/ProjectName}/relative/path`
2. Else if the path is under the workspace root:
   → `${workspace_loc}/relative/path`
3. Else:
   → the absolute path unchanged

Path separators are normalized to `/` in stored values.

**`createContents(Composite cell)`:** Returns a `Label` showing the current value.

**`updateContents(Object value)`:** Sets the label text.

---

### Modified: `StepPropertySource`

**File:** `com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`

`createConfigDescriptor(String key)` gains two new branches inserted before the existing `isMultiLineField` check:

```java
if (isFileField(key)) {
    return new PropertyDescriptor(key, key) {
        @Override public CellEditor createPropertyEditor(Composite parent) {
            return new PathCellEditor(parent, step, PathCellEditor.PathType.FILE);
        }
    };
}
if (isDirField(key)) {
    return new PropertyDescriptor(key, key) {
        @Override public CellEditor createPropertyEditor(Composite parent) {
            return new PathCellEditor(parent, step, PathCellEditor.PathType.DIRECTORY);
        }
    };
}
```

```java
private static boolean isFileField(String key) {
    String lower = key.toLowerCase(Locale.ROOT);
    return lower.endsWith("file") || lower.endsWith("path");
}

private static boolean isDirField(String key) {
    return key.toLowerCase(Locale.ROOT).endsWith("dir");
}
```

---

### Modified: Nine Action Classes

Each action that reads a path config key wraps the raw value with `EclipseVariables.resolve()` before passing it to `new File(...)` or using it as a path string. No other logic changes.

| Action | Key | Change |
|--------|-----|--------|
| `SetMavenSettingsAction` | `filePath` | Wrap with `EclipseVariables.resolve()` |
| `WriteFileAction` | `filePath` | Wrap with `EclipseVariables.resolve()` |
| `SetCodeFormatterAction` | `filePath` | Wrap with `EclipseVariables.resolve()` |
| `SetActiveTargetPlatformAction` | `targetFile` | Wrap with `EclipseVariables.resolve()` |
| `ImportMavenProjectAction` | `pomPath` | Wrap with `EclipseVariables.resolve()` |
| `GitCheckoutAction` | `repoDir` | Wrap with `EclipseVariables.resolve()` |
| `GitCloneAction` | `targetDir` | Wrap with `EclipseVariables.resolve()` |
| `ShellCommandAction` | `workingDir` | Wrap with `EclipseVariables.resolve()` |
| `MavenRunWithProgressAction` | `workingDir` | Wrap with `EclipseVariables.resolve()` |

---

## Variable Storage Format

| Situation | Stored value example |
|-----------|---------------------|
| Path under project "MyProject" | `${workspace_loc:/MyProject}/src/formatter.xml` |
| Path under workspace root | `${workspace_loc}/OtherProject/config/settings.xml` |
| Path outside workspace | `/absolute/path/to/file.xml` |

`${workspace_loc:/ProjectName}` is resolved by `IStringVariableManager` to the project's absolute filesystem location. `${workspace_loc}` resolves to the workspace root.

---

## Bundle Dependency

Add to `com.example.automation/META-INF/MANIFEST.MF` `Require-Bundle`:

```
org.eclipse.core.variables,
```

---

## Testing

### `EclipseVariablesTest`

**File:** `com.example.automation.tests/src/main/java/com/example/automation/tests/EclipseVariablesTest.java`

Runs in Eclipse harness (requires `IStringVariableManager`).

| Test | Description |
|------|-------------|
| `resolve_noVariables_returnsUnchanged` | Plain absolute path passes through unchanged |
| `resolve_workspaceLoc_returnsAbsolutePath` | `${workspace_loc}` resolves to workspace root |
| `resolve_workspaceLocWithProject_returnsProjectPath` | `${workspace_loc:/ProjectName}` resolves to project location |

### `PathCellEditorTest`

**File:** `com.example.automation.tests/src/main/java/com/example/automation/tests/PathCellEditorTest.java`

Tests the package-private static `relativize()` method directly — no Display required.

| Test | Description |
|------|-------------|
| `relativize_underProject_usesWorkspaceLocProjectVar` | Path under project → `${workspace_loc:/MyProject}/sub/path` |
| `relativize_underWorkspace_usesWorkspaceLoc` | Path under workspace, outside project → `${workspace_loc}/sub/path` |
| `relativize_outsideWorkspace_returnsAbsolute` | Path outside workspace → original absolute path |
| `relativize_noProjectName_usesWorkspaceLoc` | Step has no `projectName` → workspace-relative |

### `StepPropertySourceTest`

Two new tests added to the existing test class:

| Test | Description |
|------|-------------|
| `filePath_usesPathCellEditor` | Key `filePath` → descriptor is not a `TextPropertyDescriptor` |
| `workingDir_usesPathCellEditor` | Key `workingDir` → descriptor is not a `TextPropertyDescriptor` |
