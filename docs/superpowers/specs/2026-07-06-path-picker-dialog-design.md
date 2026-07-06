# Path Picker Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the raw native OS file/directory dialog in `PathCellEditor` with a single integrated `PathPickerDialog` that lets users browse for a path AND immediately see, select, and switch between Eclipse variable substitution forms — solving both the "user doesn't know variables exist" and "user wants to change variable form" problems consistently across all path config fields.

**Architecture:** A new `PathPickerDialog` (JFace `TitleAreaDialog`) owns an editable text field, a Browse button, and a live-updating suggestions table. A new pure-Java `PathVariableSuggestions` utility computes the ranked suggestions from an absolute path and the Eclipse variable registry. `PathCellEditor.openDialogBox()` opens `PathPickerDialog` instead of the raw OS dialog. The existing `relativize()` method is removed since the dialog now handles variable selection explicitly.

**Tech Stack:** Eclipse JFace (`TitleAreaDialog`, `TableViewer`), SWT, `org.eclipse.core.variables.IStringVariableManager`, `org.eclipse.core.resources.ResourcesPlugin`, JUnit 4, SWTBot.

---

## Variable support

The dialog suggests variable forms derived from ALL registered Eclipse string variables:

1. **`${workspace_loc:/ProjectName}`** — one per open project, computed by iterating `ResourcesPlugin.getWorkspace().getRoot().getProjects()` and resolving each project's physical location. Ranked first (most specific).
2. **`${workspace_loc}`** — workspace root, always included if path is under workspace.
3. **All registered `IDynamicVariable` instances** with no required argument — resolved via `performStringSubstitution("${varName}")`, skipped on failure. Catches `${eclipse_home}` and any plugin-contributed path variables.
4. **All registered `IValueVariable` instances** — user-defined named variables; resolved the same way.
5. Context-dependent variables (e.g. `${project_loc}`) are included if they resolve successfully, labelled "(context-dependent)" in the description column.
6. **Absolute path** — always the last suggestion so the user can always fall back.

Conversion algorithm for every candidate: resolve the variable to an absolute path, check if the selected path starts with it, then produce `${variable}/relative/part` (or just `${variable}` if exact match).

---

## File structure

- **Create:** `com.example.automation/src/main/java/com/example/automation/PathVariableSuggestions.java`
  - Pure-Java utility, no SWT/JFace dependency. Computes `List<Suggestion>` from an absolute path + `IStringVariableManager` + open projects. Fully unit-testable.
- **Create:** `com.example.automation/src/main/java/com/example/automation/PathPickerDialog.java`
  - JFace `TitleAreaDialog`. Contains the text field, Browse button, and suggestions `TableViewer`. Calls `PathVariableSuggestions` to refresh suggestions whenever the text field changes.
- **Modify:** `com.example.automation/src/main/java/com/example/automation/PathCellEditor.java`
  - `openDialogBox()` opens `PathPickerDialog` instead of raw `DirectoryDialog`/`FileDialog`. Remove `relativize()` and its helper `resolveToFilterPath()` since the dialog handles both.
- **Create:** `com.example.automation.tests/src/main/java/com/example/automation/tests/PathVariableSuggestionsTest.java`
  - Unit tests for suggestion ranking and variable form construction.
- **Modify:** `com.example.automation.tests/src/main/java/com/example/automation/tests/PathCellEditorTest.java`
  - Remove tests that relied on `relativize()` directly; add tests for the new `PathPickerDialog`-based flow where needed.

---

## `PathVariableSuggestions`

```java
public final class PathVariableSuggestions {

    public static class Suggestion {
        public final String variableForm;   // e.g. "${workspace_loc}/myrepo"
        public final String resolvedPath;   // e.g. "/home/user/workspace/myrepo"
        public final String description;    // e.g. "workspace root" or "project MyProject"
    }

    /**
     * Computes ranked suggestions for {@code absolutePath}.
     * @param absolutePath  the fully-resolved absolute path to find substitutions for
     * @param projects      open workspace projects (from ResourcesPlugin or injected for tests)
     * @param mgr           the Eclipse string variable manager (injected for tests)
     * @return ranked list: per-project workspace matches, workspace root, other variables,
     *         absolute path last
     */
    public static List<Suggestion> compute(
            String absolutePath,
            IProject[] projects,
            IStringVariableManager mgr) { ... }
}
```

---

## `PathPickerDialog`

Constructor: `PathPickerDialog(Shell parent, String currentValue, PathType pathType)`

- `createDialogArea()`: builds text field + Browse button row, then suggestions `TableViewer` (columns: Variable Form, Description) below.
- Text field `ModifyListener`: debounce not needed — Eclipse properties view is not performance-sensitive; refresh suggestions on every change.
- Suggestions refresh: resolve text field value to absolute (via `EclipseVariables.resolve()`), call `PathVariableSuggestions.compute()`, set as table input.
- Browse button: opens native `DirectoryDialog` or `FileDialog` depending on `PathType`, fills text field with chosen path (absolute), which triggers the suggestions refresh.
- Row click: copies `suggestion.variableForm` into the text field.
- `getResult()`: returns the text field value at the time OK was pressed, or `null` if cancelled.

---

## `PathCellEditor` changes

`openDialogBox()` before:
```java
DirectoryDialog dialog = new DirectoryDialog(cellEditorWindow.getShell(), SWT.NONE);
if (filterPath != null) dialog.setFilterPath(filterPath);
result = dialog.open();
// ...
return relativize(result, step);
```

`openDialogBox()` after:
```java
PathPickerDialog dialog = new PathPickerDialog(
    cellEditorWindow.getShell(), (String) getValue(), pathType);
if (dialog.open() == Window.OK) return dialog.getResult();
return null;
```

`relativize()` and `resolveToFilterPath()` are deleted. The initial filter path for the native browse dialog is computed inside `PathPickerDialog` using the same logic (resolve current value → use as filter path).

---

## Tests

### `PathVariableSuggestionsTest` (unit, no display required)

- `compute_pathUnderProject_returnsProjectVariableFirst` — path under a project's location produces `${workspace_loc:/Name}/relative` as first suggestion
- `compute_pathUnderWorkspaceRoot_returnsWorkspaceVariable` — path directly under workspace root (not under any project) produces `${workspace_loc}/relative`
- `compute_exactMatchProject_returnsVariableWithNoSuffix` — path exactly equal to project location produces `${workspace_loc:/Name}` with no trailing slash
- `compute_pathOutsideWorkspace_returnsOnlyAbsolute` — no workspace/project match → only absolute suggestion
- `compute_multipleProjectMatches_mostSpecificFirst` — nested projects: inner project match ranked before outer
- `compute_absoluteAlwaysLast` — absolute path is always the final suggestion regardless of other matches
- `compute_userValueVariable_includedIfPrefixMatches` — `IValueVariable` whose resolved value is a prefix of the path appears in suggestions
- `compute_dynamicVariable_includedIfPrefixMatches` — `IDynamicVariable` (no arg) whose resolved value is a prefix appears in suggestions
- `compute_resolutionFailure_variableSkipped` — a variable that throws on resolution is silently skipped

### `PathCellEditorTest` updates

The 4 existing `relativize_*` tests (`relativize_underProject_usesWorkspaceLocProjectVar`, `relativize_underWorkspace_usesWorkspaceLoc`, `relativize_outsideWorkspace_returnsAbsolute`, `relativize_noProjectName_usesWorkspaceLoc`) are deleted because `relativize()` no longer exists. Their coverage is replaced by the `PathVariableSuggestionsTest` cases above.

---

## Version bump

Bump minor version from 1.9.0 → 1.10.0 across all 9 version files after implementation.
