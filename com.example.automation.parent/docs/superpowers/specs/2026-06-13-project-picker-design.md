# Project Picker ComboBox — Design Spec

**Goal:** Replace the plain text cell editor for `projectName` config keys in the Properties view with an editable ComboBox pre-populated with all projects in the Eclipse workspace.

---

## Scope

**In scope:**
- Any config key literally named `projectName` across all actions (convention-based; no `IAction` changes)
- All workspace projects included in the dropdown, regardless of open/closed state
- ComboBox remains editable: user may type a name not present in the workspace

**Out of scope:**
- Other config keys that happen to refer to projects (e.g. no pattern matching, no new `IAction` API)
- Filtering or grouping projects by type or state
- Refreshing the dropdown while the cell editor is open

---

## Architecture

Two file changes, zero interface changes.

### New: `ProjectComboBoxCellEditor`

**File:** `com.example.automation/src/main/java/com/example/automation/ProjectComboBoxCellEditor.java`

A `CellEditor` subclass that wraps an SWT `Combo` with `SWT.DROP_DOWN` style (editable).

Behaviour:
- `createControl(Composite parent)` — creates the `Combo`, queries `ResourcesPlugin.getWorkspace().getRoot().getProjects()`, sorts project names alphabetically, and sets them as the combo items.
- `doGetValue()` — returns `combo.getText()` (the currently typed or selected string).
- `doSetValue(Object value)` — calls `combo.setText((String) value)`.
- Focus and key handling mirrors `TextCellEditor`: focus loss fires `applyEditorValue()`; Escape fires `fireCancelEditor()`; Enter fires `applyEditorValue()`.

### Modified: `StepPropertySource.createConfigDescriptor()`

**File:** `com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`

Adds a `"projectName".equals(key)` branch before the existing `isMultiLineField` check:

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
```

---

## Testing

Tested via SWTBot in `com.example.automation.tests`:

- Add a workflow with a `refresh-project` step.
- Select the step in the Automation view; open the Properties view.
- Click the `projectName` cell to activate its editor.
- Assert that the activated widget is an SWT `Combo` (not a `Text`).

The branching logic in `StepPropertySource` mirrors the existing `isMultiLineField` path and requires no additional unit test.
