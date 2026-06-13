# Project Picker ComboBox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain text cell editor for any config key named `projectName` in the Properties view with an editable ComboBox pre-populated with all Eclipse workspace projects.

**Architecture:** A new `ProjectComboBoxCellEditor` class (mirroring `MultiLineTextCellEditor`) wraps an SWT `Combo` with `SWT.DROP_DOWN` style and queries workspace projects from `ResourcesPlugin` on creation. `StepPropertySource.createConfigDescriptor()` gets a `"projectName".equals(key)` branch, exactly like the existing `isMultiLineField` branch.

**Tech Stack:** Java 17, Eclipse SWT (`org.eclipse.swt`), JFace Viewers (`org.eclipse.jface`), Eclipse Resources (`org.eclipse.core.resources`), JUnit 4

---

### Task 1: Project picker combo-box cell editor

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ProjectComboBoxCellEditor.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java` (lines 97–112: `createConfigDescriptor`)
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java` (add one test)

**Background for the implementer:**

`StepPropertySource` is an `IPropertySource` that drives the Eclipse Properties view for a selected `Step`. Its `createConfigDescriptor(String key)` method decides which cell editor to use for each config key. Today it has two paths:
- Keys matching `isMultiLineField(actionId, key)` → anonymous `PropertyDescriptor` subclass that returns a `MultiLineTextCellEditor`
- Everything else → `TextPropertyDescriptor`

You will add a third path: `"projectName".equals(key)` → anonymous `PropertyDescriptor` subclass that returns a `ProjectComboBoxCellEditor`.

`ProjectComboBoxCellEditor` extends `CellEditor` (from `org.eclipse.jface.viewers`) and wraps an SWT `Combo` widget with `SWT.DROP_DOWN` style, which makes the combo editable (user can type a value not in the list). The combo items are populated on creation by querying `ResourcesPlugin.getWorkspace().getRoot().getProjects()`.

The test in `StepPropertySourceTest` is a plain JUnit test (no SWTBot) that verifies the descriptor returned for a `projectName` key is not a `TextPropertyDescriptor`. It runs inside the Eclipse test harness (which provides a `Display`), so it has access to Eclipse APIs.

---

- [ ] **Step 1: Write the failing test**

Add this test to `StepPropertySourceTest.java`. The existing `stub()` and `src()` helpers are already defined in the class — just add the test method.

```java
@Test
public void projectName_usesCustomDescriptor() {
    IAction refreshProject = stub("refresh-project", Map.of("projectName", ""));
    Step step = new Step("refresh-project");
    ActionRegistry reg = new ActionRegistry(List.of(refreshProject));
    boolean[] saved = {false};

    IPropertyDescriptor[] descs = src(step, reg, saved).getPropertyDescriptors();

    IPropertyDescriptor projectNameDesc = null;
    for (IPropertyDescriptor d : descs) {
        if ("projectName".equals(d.getId())) {
            projectNameDesc = d;
            break;
        }
    }
    assertNotNull("projectName descriptor must exist", projectNameDesc);
    assertFalse("projectName must not use TextPropertyDescriptor",
        projectNameDesc instanceof TextPropertyDescriptor);
}
```

- [ ] **Step 2: Verify the test currently fails**

The test will fail to compile until `ProjectComboBoxCellEditor` exists. That is expected — proceed to Step 3.

- [ ] **Step 3: Create `ProjectComboBoxCellEditor`**

Create the file at:
`com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ProjectComboBoxCellEditor.java`

```java
package com.example.automation;

import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * Editable combo-box cell editor pre-populated with all Eclipse workspace
 * project names, sorted alphabetically. The user may also type a name that
 * is not in the list.
 */
public class ProjectComboBoxCellEditor extends CellEditor {

    private Combo combo;

    public ProjectComboBoxCellEditor(Composite parent) {
        super(parent);
    }

    @Override
    protected Control createControl(Composite parent) {
        combo = new Combo(parent, SWT.DROP_DOWN);
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        String[] names = Arrays.stream(projects)
            .map(IProject::getName)
            .sorted(Comparator.naturalOrder())
            .toArray(String[]::new);
        combo.setItems(names);

        combo.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                ProjectComboBoxCellEditor.this.focusLost();
            }
        });
        combo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                keyReleaseOccured(e);
            }
        });
        return combo;
    }

    @Override
    protected Object doGetValue() {
        return combo.getText();
    }

    @Override
    protected void doSetValue(Object value) {
        combo.setText(value instanceof String s ? s : "");
    }

    @Override
    protected void doSetFocus() {
        combo.setFocus();
    }
}
```

- [ ] **Step 4: Add the `projectName` branch to `StepPropertySource.createConfigDescriptor()`**

The current `createConfigDescriptor` method is at lines 97–112 of `StepPropertySource.java`. Replace it with:

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

- [ ] **Step 5: Verify the build compiles**

Run from `com.example.automation.parent/`:

```
mvn compile -pl com.example.automation -am -q
```

Expected: BUILD SUCCESS (no compilation errors).

- [ ] **Step 6: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ProjectComboBoxCellEditor.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java
git commit -m "feat: add project picker combo-box for projectName config keys"
```
