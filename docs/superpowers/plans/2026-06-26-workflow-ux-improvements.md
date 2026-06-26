# Workflow UX Improvements — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bold step rendering, step name editing, duplicate step, multi-select move, workspace-parent dir defaults, a fixed project combobox, and a version bump to 1.2.0 to enable over-the-air updates.

**Architecture:** All changes are in `com.example.automation` (main plugin bundle). A new `StepOperations` utility class holds pure-logic helpers (`isContiguous`, `deepCopy`, `workspaceParent`, `isDirField`, `isFileField`) so they can be unit-tested without a running Eclipse UI. `StepPropertySource` gains two new "Step" category properties (name, bold). `AutomationView` gains a Duplicate button and multi-select block-move behaviour. `ProjectComboBoxCellEditor` refreshes its list on every focus so it is never stale.

**Tech Stack:** Java 17 · Eclipse OSGi (Tycho 3.0.5) · JUnit 4 · SWTBot · Gson (already bundled)

**Build command (run from repo root):**
```
cd com.example.automation.parent && mvn clean verify
```

**Key paths (all relative to repo root `com.example.automation.parent/`):**
- Main source: `com.example.automation/src/main/java/com/example/automation/`
- Test source: `com.example.automation.tests/src/main/java/com/example/automation/tests/`

---

### Task 1: Version bump 1.1.0 → 1.2.0

**Files:**
- Modify: `com.example.automation.parent/pom.xml` line 10
- Modify: `com.example.automation/pom.xml` line 11
- Modify: `com.example.automation.feature/pom.xml` line 11
- Modify: `com.example.automation.site/pom.xml` line 11
- Modify: `com.example.automation.tests/pom.xml` line 11
- Modify: `com.example.automation.coverage/pom.xml` line 11
- Modify: `com.example.automation/META-INF/MANIFEST.MF` line 5
- Modify: `com.example.automation.tests/META-INF/MANIFEST.MF` line 5
- Modify: `com.example.automation.feature/feature.xml` line 5

- [ ] **Step 1: Bump parent pom.xml**

In `com.example.automation.parent/pom.xml` change:
```xml
<version>1.1.0-SNAPSHOT</version>
```
to:
```xml
<version>1.2.0-SNAPSHOT</version>
```

- [ ] **Step 2: Bump all child pom.xml parent references**

In each of these 5 files, change the `<parent><version>` from `1.1.0-SNAPSHOT` to `1.2.0-SNAPSHOT`:
- `com.example.automation/pom.xml`
- `com.example.automation.feature/pom.xml`
- `com.example.automation.site/pom.xml`
- `com.example.automation.tests/pom.xml`
- `com.example.automation.coverage/pom.xml`

Each file has this block; change only the `<version>` inside `<parent>`:
```xml
<parent>
  <groupId>com.example.automation</groupId>
  <artifactId>com.example.automation.parent</artifactId>
  <version>1.2.0-SNAPSHOT</version>   <!-- changed from 1.1.0-SNAPSHOT -->
  <relativePath>../pom.xml</relativePath>
</parent>
```

- [ ] **Step 3: Bump plugin MANIFEST.MF**

In `com.example.automation/META-INF/MANIFEST.MF` change:
```
Bundle-Version: 1.1.0.qualifier
```
to:
```
Bundle-Version: 1.2.0.qualifier
```

- [ ] **Step 4: Bump test bundle MANIFEST.MF**

In `com.example.automation.tests/META-INF/MANIFEST.MF` change:
```
Bundle-Version: 1.1.0.qualifier
```
to:
```
Bundle-Version: 1.2.0.qualifier
```

- [ ] **Step 5: Bump feature.xml**

In `com.example.automation.feature/feature.xml` change line 5:
```xml
version="1.1.0.qualifier">
```
to:
```xml
version="1.2.0.qualifier">
```

- [ ] **Step 6: Verify build passes**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```
git add com.example.automation.parent/pom.xml \
        com.example.automation.parent/com.example.automation/pom.xml \
        com.example.automation.parent/com.example.automation.feature/pom.xml \
        com.example.automation.parent/com.example.automation.site/pom.xml \
        com.example.automation.parent/com.example.automation.tests/pom.xml \
        com.example.automation.parent/com.example.automation.coverage/pom.xml \
        com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF \
        com.example.automation.parent/com.example.automation.tests/META-INF/MANIFEST.MF \
        com.example.automation.parent/com.example.automation.feature/feature.xml
git commit -m "chore: bump version to 1.2.0"
```

---

### Task 2: Add `bold` field to `Step` model

**Files:**
- Modify: `com.example.automation/src/main/java/com/example/automation/model/Step.java`
- Modify: `com.example.automation.tests/src/main/java/com/example/automation/tests/StepTest.java`

- [ ] **Step 1: Write failing tests**

Add to `StepTest.java`:
```java
import com.google.gson.Gson;

@Test
public void bold_defaultsFalse() {
    assertFalse(new Step("a").isBold());
}

@Test
public void bold_setterGetterRoundTrip() {
    Step step = new Step("a");
    step.setBold(true);
    assertTrue(step.isBold());
    step.setBold(false);
    assertFalse(step.isBold());
}

@Test
public void bold_gsonRoundTrip_true() {
    Step step = new Step("a");
    step.setBold(true);
    String json = new Gson().toJson(step);
    Step loaded = new Gson().fromJson(json, Step.class);
    assertTrue(loaded.isBold());
}

@Test
public void bold_gsonRoundTrip_missingFieldDefaultsFalse() {
    // Simulates loading an old workflow JSON that has no "bold" field
    String json = "{\"actionId\":\"a\"}";
    Step loaded = new Gson().fromJson(json, Step.class);
    assertFalse("missing bold field must default to false", loaded.isBold());
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: compilation failure — `isBold()` and `setBold()` don't exist yet.

- [ ] **Step 3: Add bold field to Step.java**

After the `name` field (line 14), add:
```java
private boolean bold;
```

After `setName(String name)` getter/setter block, add:
```java
/** @return whether this step should be displayed in bold in the workflow table */
public boolean isBold() { return bold; }
public void setBold(boolean bold) { this.bold = bold; }
```

- [ ] **Step 4: Run tests to verify they pass**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/Step.java \
        com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepTest.java
git commit -m "feat: add bold field to Step model"
```

---

### Task 3: Extract `StepOperations` helper class

**Files:**
- Create: `com.example.automation/src/main/java/com/example/automation/StepOperations.java`
- Create: `com.example.automation.tests/src/main/java/com/example/automation/tests/StepOperationsTest.java`
- Modify: `com.example.automation/src/main/java/com/example/automation/StepPropertySource.java` (replace private isDirField/isFileField calls with StepOperations)

- [ ] **Step 1: Write failing tests**

Create `StepOperationsTest.java`:
```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

import com.example.automation.StepOperations;
import com.example.automation.model.Step;

public class StepOperationsTest {

    // isContiguous

    @Test
    public void isContiguous_emptyArray_returnsFalse() {
        assertFalse(StepOperations.isContiguous(new int[]{}));
    }

    @Test
    public void isContiguous_singleElement_returnsTrue() {
        assertTrue(StepOperations.isContiguous(new int[]{3}));
    }

    @Test
    public void isContiguous_consecutiveIndices_returnsTrue() {
        assertTrue(StepOperations.isContiguous(new int[]{2, 3, 4}));
    }

    @Test
    public void isContiguous_gapInIndices_returnsFalse() {
        assertFalse(StepOperations.isContiguous(new int[]{1, 3, 4}));
    }

    @Test
    public void isContiguous_twoElements_consecutive_returnsTrue() {
        assertTrue(StepOperations.isContiguous(new int[]{5, 6}));
    }

    @Test
    public void isContiguous_twoElements_nonConsecutive_returnsFalse() {
        assertFalse(StepOperations.isContiguous(new int[]{5, 7}));
    }

    // deepCopy

    @Test
    public void deepCopy_copiesAllFields() {
        Step src = new Step("my-action");
        src.setName("My Step");
        src.setBold(true);
        src.getConfig().put("key", "value");

        Step copy = StepOperations.deepCopy(src);

        assertEquals("my-action", copy.getActionId());
        assertEquals("My Step", copy.getName());
        assertTrue(copy.isBold());
        assertEquals("value", copy.getConfig().get("key"));
    }

    @Test
    public void deepCopy_configIsIndependent() {
        Step src = new Step("my-action");
        src.getConfig().put("key", "original");

        Step copy = StepOperations.deepCopy(src);
        copy.getConfig().put("key", "modified");

        assertEquals("original", src.getConfig().get("key"));
    }

    @Test
    public void deepCopy_nullNameAndFalse() {
        Step src = new Step("x");
        Step copy = StepOperations.deepCopy(src);
        assertNull(copy.getName());
        assertFalse(copy.isBold());
    }

    // isDirField / isFileField

    @Test
    public void isDirField_keyEndingWithDir_returnsTrue() {
        assertTrue(StepOperations.isDirField("workingDir"));
        assertTrue(StepOperations.isDirField("repoDir"));
        assertTrue(StepOperations.isDirField("targetDir"));
    }

    @Test
    public void isDirField_otherKey_returnsFalse() {
        assertFalse(StepOperations.isDirField("filePath"));
        assertFalse(StepOperations.isDirField("url"));
    }

    @Test
    public void isFileField_keyEndingWithFileOrPath_returnsTrue() {
        assertTrue(StepOperations.isFileField("filePath"));
        assertTrue(StepOperations.isFileField("settingsFile"));
        assertTrue(StepOperations.isFileField("pomPath"));
    }

    @Test
    public void isFileField_otherKey_returnsFalse() {
        assertFalse(StepOperations.isFileField("workingDir"));
        assertFalse(StepOperations.isFileField("goals"));
    }
}
```

- [ ] **Step 2: Run to verify compilation fails**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: compilation failure — `StepOperations` doesn't exist yet.

- [ ] **Step 3: Create StepOperations.java**

```java
package com.example.automation;

import java.util.HashMap;

import org.eclipse.core.resources.ResourcesPlugin;

import com.example.automation.model.Step;

/**
 * Pure-logic helpers for step operations. Static methods only; no Eclipse UI
 * dependency except {@link #workspaceParent()} which requires a running workspace.
 */
public final class StepOperations {

    private StepOperations() {}

    /**
     * Returns true when {@code sortedIndices} forms a contiguous block
     * (e.g. [2,3,4]) with no gaps. An empty array returns false.
     */
    public static boolean isContiguous(int[] sortedIndices) {
        if (sortedIndices.length == 0) return false;
        return sortedIndices[sortedIndices.length - 1] - sortedIndices[0]
               == sortedIndices.length - 1;
    }

    /** Deep-copies a step: same actionId/name/bold, independent config map. */
    public static Step deepCopy(Step src) {
        Step copy = new Step(src.getActionId());
        copy.setName(src.getName());
        copy.setBold(src.isBold());
        copy.setConfig(new HashMap<>(src.getConfig()));
        return copy;
    }

    /**
     * Returns the absolute path of the directory that contains the Eclipse
     * workspace root, or {@code null} if the workspace location is unavailable.
     */
    public static String workspaceParent() {
        org.eclipse.core.runtime.IPath loc =
            ResourcesPlugin.getWorkspace().getRoot().getLocation();
        if (loc == null) return null;
        java.io.File parent = loc.toFile().getParentFile();
        return parent != null ? parent.getAbsolutePath() : null;
    }

    /** Returns true when {@code key} represents a directory config field. */
    public static boolean isDirField(String key) {
        return key.toLowerCase(java.util.Locale.ROOT).endsWith("dir");
    }

    /** Returns true when {@code key} represents a file or path config field. */
    public static boolean isFileField(String key) {
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith("file") || lower.endsWith("path");
    }
}
```

- [ ] **Step 4: Update StepPropertySource to use StepOperations**

In `StepPropertySource.java`, replace the two private static methods and their usages:

Remove these private methods at the bottom of the class:
```java
private static boolean isFileField(String key) {
    String lower = key.toLowerCase(java.util.Locale.ROOT);
    return lower.endsWith("file") || lower.endsWith("path");
}

private static boolean isDirField(String key) {
    return key.toLowerCase(java.util.Locale.ROOT).endsWith("dir");
}
```

Replace all three call sites in `createConfigDescriptor()`:
```java
// was: if (isDirField(key))   → now:
if (StepOperations.isDirField(key)) {
// was: if (isFileField(key))  → now:
if (StepOperations.isFileField(key)) {
```

(The `isMultiLineField` method stays as-is — it checks specific action+key combos.)

- [ ] **Step 5: Run tests to verify they pass**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepOperations.java \
        com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java \
        com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepOperationsTest.java
git commit -m "feat: extract StepOperations helper (isContiguous, deepCopy, workspaceParent, isDirField, isFileField)"
```

---

### Task 4: Add `name` and `bold` properties to `StepPropertySource`; workspace-parent reset

**Files:**
- Modify: `com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`
- Modify: `com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java`

- [ ] **Step 1: Update existing StepPropertySourceTest to expect the new descriptors**

`actionProperty_isReadOnly` currently asserts `assertEquals(1, descs.length)`.
After adding name + bold, there are now 3 "Step" descriptors for a step with no config:
```java
@Test
public void actionProperty_isReadOnly() {
    Step step = new Step("my.action");
    ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
    boolean[] saved = {false};

    StepPropertySource s = src(step, reg, saved);
    IPropertyDescriptor[] descs = s.getPropertyDescriptors();

    // action + name + bold = 3 (no config keys for this action)
    assertEquals(3, descs.length);
    // Find action descriptor
    IPropertyDescriptor actionDesc = null;
    for (IPropertyDescriptor d : descs) {
        if ("action".equals(d.getId())) { actionDesc = d; break; }
    }
    assertNotNull(actionDesc);
    assertFalse("action descriptor must not be a TextPropertyDescriptor",
        actionDesc instanceof TextPropertyDescriptor);
    assertEquals("my.action", s.getPropertyValue("action"));
    assertFalse(saved[0]);
}
```

Also update `unknownAction_fallsBackToExistingConfig` (currently checks `descs.length == 2`):
```java
@Test
public void unknownAction_fallsBackToExistingConfig() {
    Step step = new Step("unknown");
    step.getConfig().put("foo", "bar");
    ActionRegistry reg = new ActionRegistry(List.of());
    boolean[] saved = {false};

    IPropertyDescriptor[] descs = src(step, reg, saved).getPropertyDescriptors();

    // action + name + bold + "foo" = 4
    assertEquals(4, descs.length);
    boolean foundFoo = false;
    for (IPropertyDescriptor d : descs) {
        if ("foo".equals(d.getId())) {
            assertTrue(d instanceof TextPropertyDescriptor);
            foundFoo = true;
        }
    }
    assertTrue("foo key must appear as an editable TextPropertyDescriptor", foundFoo);
}
```

- [ ] **Step 2: Add new tests for name and bold properties**

Add to `StepPropertySourceTest.java`:
```java
import org.eclipse.ui.views.properties.ComboBoxPropertyDescriptor;

@Test
public void nameProperty_isTextDescriptor_inStepCategory() {
    Step step = new Step("my.action");
    ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
    boolean[] saved = {false};
    StepPropertySource s = src(step, reg, saved);

    IPropertyDescriptor nameDesc = null;
    for (IPropertyDescriptor d : s.getPropertyDescriptors()) {
        if ("name".equals(d.getId())) { nameDesc = d; break; }
    }
    assertNotNull("name property must exist", nameDesc);
    assertTrue("name must use TextPropertyDescriptor", nameDesc instanceof TextPropertyDescriptor);
}

@Test
public void nameProperty_setAndGet() {
    Step step = new Step("my.action");
    ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
    boolean[] saved = {false};
    StepPropertySource s = src(step, reg, saved);

    s.setPropertyValue("name", "My Custom Name");

    assertEquals("My Custom Name", step.getName());
    assertTrue(saved[0]);
    assertEquals("My Custom Name", s.getPropertyValue("name"));
}

@Test
public void nameProperty_setBlank_setsNull() {
    Step step = new Step("my.action");
    step.setName("Existing");
    ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
    boolean[] saved = {false};
    StepPropertySource s = src(step, reg, saved);

    s.setPropertyValue("name", "   ");

    assertNull("blank name must reset to null", step.getName());
    assertTrue(saved[0]);
}

@Test
public void nameProperty_reset_setsNull() {
    Step step = new Step("my.action");
    step.setName("Custom");
    ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
    boolean[] saved = {false};
    StepPropertySource s = src(step, reg, saved);

    s.resetPropertyValue("name");

    assertNull(step.getName());
    assertTrue(saved[0]);
}

@Test
public void boldProperty_isComboDescriptor_defaultNo() {
    Step step = new Step("my.action");
    ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
    boolean[] saved = {false};
    StepPropertySource s = src(step, reg, saved);

    IPropertyDescriptor boldDesc = null;
    for (IPropertyDescriptor d : s.getPropertyDescriptors()) {
        if ("bold".equals(d.getId())) { boldDesc = d; break; }
    }
    assertNotNull("bold property must exist", boldDesc);
    assertTrue("bold must use ComboBoxPropertyDescriptor",
        boldDesc instanceof ComboBoxPropertyDescriptor);
    assertEquals(0, s.getPropertyValue("bold")); // 0 = No
}

@Test
public void boldProperty_setYes_setsTrue() {
    Step step = new Step("my.action");
    ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
    boolean[] saved = {false};
    StepPropertySource s = src(step, reg, saved);

    s.setPropertyValue("bold", 1); // 1 = Yes

    assertTrue(step.isBold());
    assertTrue(saved[0]);
    assertEquals(1, s.getPropertyValue("bold"));
}

@Test
public void boldProperty_setNo_setsFalse() {
    Step step = new Step("my.action");
    step.setBold(true);
    ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
    boolean[] saved = {false};
    StepPropertySource s = src(step, reg, saved);

    s.setPropertyValue("bold", 0); // 0 = No

    assertFalse(step.isBold());
    assertTrue(saved[0]);
}

@Test
public void boldProperty_reset_setsFalse() {
    Step step = new Step("my.action");
    step.setBold(true);
    ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
    boolean[] saved = {false};
    StepPropertySource s = src(step, reg, saved);

    s.resetPropertyValue("bold");

    assertFalse(step.isBold());
    assertTrue(saved[0]);
}

@Test
public void resetDirField_withBlankDefault_usesWorkspaceParent() {
    // workingDir has a blank default in ShellCommandAction — reset should give workspace parent
    IAction action = stub("shell-command", Map.of("command", "", "workingDir", ""));
    Step step = new Step("shell-command");
    step.getConfig().put("workingDir", "/some/old/path");
    ActionRegistry reg = new ActionRegistry(List.of(action));
    boolean[] saved = {false};
    StepPropertySource s = src(step, reg, saved);

    s.resetPropertyValue("workingDir");

    String wsParent = com.example.automation.StepOperations.workspaceParent();
    if (wsParent != null) {
        assertEquals("reset must apply workspace parent for blank-default dir field",
            wsParent, step.getConfig().get("workingDir"));
    }
    assertTrue(saved[0]);
}
```

- [ ] **Step 3: Run tests to verify they fail**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: failures in `StepPropertySourceTest` (name/bold properties don't exist yet, descriptor count assertions fail).

- [ ] **Step 4: Implement changes in StepPropertySource.java**

Replace the complete file content:

```java
package com.example.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.views.properties.ComboBoxPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.PropertyDescriptor;
import org.eclipse.ui.views.properties.TextPropertyDescriptor;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.model.Step;

public class StepPropertySource implements IPropertySource {

    private static final String PROP_ACTION = "action";
    private static final String PROP_NAME   = "name";
    private static final String PROP_BOLD   = "bold";

    private final Step step;
    private final ActionRegistry registry;
    private final Runnable save;

    public StepPropertySource(Step step, ActionRegistry registry, Runnable save) {
        this.step     = step;
        this.registry = registry;
        this.save     = save;
    }

    @Override
    public IPropertyDescriptor[] getPropertyDescriptors() {
        List<IPropertyDescriptor> list = new ArrayList<>();

        PropertyDescriptor actionDesc = new PropertyDescriptor(PROP_ACTION, "Action");
        actionDesc.setCategory("Step");
        list.add(actionDesc);

        TextPropertyDescriptor nameDesc = new TextPropertyDescriptor(PROP_NAME, "Name");
        nameDesc.setCategory("Step");
        list.add(nameDesc);

        ComboBoxPropertyDescriptor boldDesc =
            new ComboBoxPropertyDescriptor(PROP_BOLD, "Bold", new String[]{"No", "Yes"});
        boldDesc.setCategory("Step");
        list.add(boldDesc);

        for (String key : configKeys()) {
            PropertyDescriptor d = createConfigDescriptor(key);
            d.setCategory("Config");
            list.add(d);
        }
        return list.toArray(new IPropertyDescriptor[0]);
    }

    @Override
    public Object getPropertyValue(Object id) {
        if (PROP_ACTION.equals(id)) return step.getActionId() != null ? step.getActionId() : "";
        if (PROP_NAME.equals(id))   return step.getName() != null ? step.getName() : "";
        if (PROP_BOLD.equals(id))   return step.isBold() ? 1 : 0;
        return (id instanceof String key) ? step.getConfig().getOrDefault(key, "") : "";
    }

    @Override
    public void setPropertyValue(Object id, Object value) {
        if (PROP_ACTION.equals(id)) return;
        if (PROP_NAME.equals(id)) {
            String s = value instanceof String str ? str : "";
            step.setName(s.isBlank() ? null : s);
            save.run();
            return;
        }
        if (PROP_BOLD.equals(id)) {
            step.setBold(value instanceof Integer i && i == 1);
            save.run();
            return;
        }
        if (!(id instanceof String key) || !(value instanceof String strVal)) return;
        step.getConfig().put(key, strVal);
        save.run();
    }

    @Override
    public void resetPropertyValue(Object id) {
        if (PROP_ACTION.equals(id)) return;
        if (PROP_NAME.equals(id)) {
            step.setName(null);
            save.run();
            return;
        }
        if (PROP_BOLD.equals(id)) {
            step.setBold(false);
            save.run();
            return;
        }
        if (!(id instanceof String key)) return;
        IAction action = registry.getAction(step.getActionId());
        if (action == null) return;
        String def = action.getDefaultConfig().get(key);
        if ((StepOperations.isDirField(key) || StepOperations.isFileField(key))
                && (def == null || def.isBlank())) {
            String wsParent = StepOperations.workspaceParent();
            if (wsParent != null) def = wsParent;
        }
        if (def != null) {
            step.getConfig().put(key, def);
            save.run();
        }
    }

    @Override
    public boolean isPropertySet(Object id) {
        if (PROP_ACTION.equals(id)) return false;
        if (PROP_NAME.equals(id))   return step.getName() != null && !step.getName().isBlank();
        if (PROP_BOLD.equals(id))   return step.isBold();
        if (!(id instanceof String key)) return false;
        IAction action = registry.getAction(step.getActionId());
        if (action == null) return step.getConfig().containsKey(key);
        String currentVal = step.getConfig().get(key);
        if (currentVal == null) return false;
        return !currentVal.equals(action.getDefaultConfig().get(key));
    }

    @Override
    public Object getEditableValue() { return null; }

    private PropertyDescriptor createConfigDescriptor(String key) {
        if ("projectName".equals(key)) {
            return new PropertyDescriptor(key, key) {
                @Override
                public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                    return new ProjectComboBoxCellEditor(parent);
                }
            };
        }
        if (StepOperations.isDirField(key)) {
            return new PropertyDescriptor(key, key) {
                @Override
                public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                    return new PathCellEditor(parent, step, PathCellEditor.PathType.DIRECTORY);
                }
            };
        }
        if (StepOperations.isFileField(key)) {
            return new PropertyDescriptor(key, key) {
                @Override
                public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                    return new PathCellEditor(parent, step, PathCellEditor.PathType.FILE);
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

    private static boolean isMultiLineField(String actionId, String key) {
        return ("shell-command".equals(actionId) && "command".equals(key))
            || ("write-file".equals(actionId) && "content".equals(key));
    }

    private List<String> configKeys() {
        IAction action = registry.getAction(step.getActionId());
        Map<String, String> source = (action != null) ? action.getDefaultConfig() : step.getConfig();
        return new ArrayList<>(source.keySet());
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java \
        com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java
git commit -m "feat: add name and bold properties to StepPropertySource; workspace-parent reset for dir/file fields"
```

---

### Task 5: Bold font rendering in `StepLabelProvider`

**Files:**
- Modify: `com.example.automation/src/main/java/com/example/automation/StepLabelProvider.java`

- [ ] **Step 1: Implement IFontProvider in StepLabelProvider.Name**

Replace the `Name` inner class:

```java
/**
 * Label provider for the Name column. Returns the step's custom name if set,
 * otherwise falls back to the action's display name, then to the raw action ID.
 * Uses bold font when {@link Step#isBold()} is true.
 */
public static class Name extends ColumnLabelProvider implements org.eclipse.jface.viewers.IFontProvider {

    @Override
    public String getText(Object element) {
        Step step = (Step) element;
        if (step.getName() != null && !step.getName().isBlank())
            return step.getName();
        IAction action = ActionRegistry.getInstance().getAction(step.getActionId());
        return action != null ? action.getName() : step.getActionId();
    }

    @Override
    public org.eclipse.swt.graphics.Font getFont(Object element) {
        Step step = (Step) element;
        if (step.isBold())
            return JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);
        return null; // null = use viewer default font
    }
}
```

Add the missing import at the top of `StepLabelProvider.java`:
```java
import org.eclipse.jface.resource.JFaceResources;
```

- [ ] **Step 2: Verify build passes**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepLabelProvider.java
git commit -m "feat: bold font rendering for steps with bold=true in workflow table"
```

---

### Task 6: `AutomationView` — Duplicate button, multi-select move, workspace-parent pre-fill

**Files:**
- Modify: `com.example.automation/src/main/java/com/example/automation/AutomationView.java`

- [ ] **Step 1: Add duplicateStepItem field**

In `AutomationView.java`, add to the toolbar item declarations (around line 70):
```java
private ToolItem addStepItem, deleteStepItem, moveUpItem, moveDownItem, duplicateStepItem;
```

- [ ] **Step 2: Add duplicate button to toolbar**

In `createToolBar()`, after `moveDownItem = makeButton(...)`, add:
```java
duplicateStepItem = makeButton(bar, "Duplicate Step",
    shared.getImage(ISharedImages.IMG_TOOL_COPY),
    SelectionListener.widgetSelectedAdapter(e -> onDuplicate()));
```

- [ ] **Step 3: Add Arrays import**

Add at the top of the file:
```java
import java.util.Arrays;
```

- [ ] **Step 4: Replace updateButtonStates() with multi-select aware version**

Replace the entire `updateButtonStates()` method:
```java
private void updateButtonStates() {
    boolean hasWorkflow = currentWorkflow != null;
    IStructuredSelection sel = viewer.getStructuredSelection();
    boolean hasStep = !sel.isEmpty();
    int[] selIndices = viewer.getTable().getSelectionIndices();
    Arrays.sort(selIndices);
    boolean contiguous = StepOperations.isContiguous(selIndices);
    int stepCount = hasWorkflow ? currentWorkflow.getSteps().size() : 0;
    boolean running = activeRunner != null;

    newWorkflowItem.setEnabled(!running);
    editWorkflowItem.setEnabled(!running && hasWorkflow);
    openWorkflowItem.setEnabled(!running && !workflows.isEmpty());
    addStepItem.setEnabled(!running && hasWorkflow);
    deleteStepItem.setEnabled(!running && hasStep);
    moveUpItem.setEnabled(!running && contiguous
        && selIndices.length > 0 && selIndices[0] > 0);
    moveDownItem.setEnabled(!running && contiguous
        && selIndices.length > 0 && selIndices[selIndices.length - 1] < stepCount - 1);
    duplicateStepItem.setEnabled(!running && contiguous && selIndices.length > 0);
    runItem.setEnabled(!running && hasWorkflow && stepCount > 0);
    runSelectedItem.setEnabled(!running && hasStep);
    stopItem.setEnabled(running);
}
```

- [ ] **Step 5: Replace onMoveUp() with block-move version**

```java
private void onMoveUp() {
    List<Step> steps = currentWorkflow.getSteps();
    int[] indices = viewer.getTable().getSelectionIndices();
    Arrays.sort(indices);
    if (!StepOperations.isContiguous(indices) || indices[0] <= 0) return;
    List<Step> block = new ArrayList<>();
    for (int i = indices.length - 1; i >= 0; i--) block.add(0, steps.remove(indices[i]));
    int insertAt = indices[0] - 1;
    steps.addAll(insertAt, block);
    save();
    viewer.refresh();
    int[] newSel = new int[indices.length];
    for (int i = 0; i < indices.length; i++) newSel[i] = indices[i] - 1;
    viewer.getTable().setSelection(newSel);
    updateButtonStates();
}
```

- [ ] **Step 6: Replace onMoveDown() with block-move version**

```java
private void onMoveDown() {
    List<Step> steps = currentWorkflow.getSteps();
    int[] indices = viewer.getTable().getSelectionIndices();
    Arrays.sort(indices);
    if (!StepOperations.isContiguous(indices)
            || indices[indices.length - 1] >= steps.size() - 1) return;
    List<Step> block = new ArrayList<>();
    for (int i = indices.length - 1; i >= 0; i--) block.add(0, steps.remove(indices[i]));
    int insertAt = indices[0] + 1;
    steps.addAll(insertAt, block);
    save();
    viewer.refresh();
    int[] newSel = new int[indices.length];
    for (int i = 0; i < indices.length; i++) newSel[i] = indices[i] + 1;
    viewer.getTable().setSelection(newSel);
    updateButtonStates();
}
```

- [ ] **Step 7: Add onDuplicate() method**

Add after `onMoveDown()`:
```java
private void onDuplicate() {
    if (currentWorkflow == null) return;
    List<Step> steps = currentWorkflow.getSteps();
    int[] indices = viewer.getTable().getSelectionIndices();
    Arrays.sort(indices);
    if (!StepOperations.isContiguous(indices)) return;
    List<Step> copies = new ArrayList<>();
    for (int idx : indices) copies.add(StepOperations.deepCopy(steps.get(idx)));
    int insertAt = indices[indices.length - 1] + 1;
    steps.addAll(insertAt, copies);
    save();
    viewer.refresh();
    int[] newSel = new int[copies.size()];
    for (int i = 0; i < copies.size(); i++) newSel[i] = insertAt + i;
    viewer.getTable().setSelection(newSel);
    updateButtonStates();
}
```

- [ ] **Step 8: Update onAddStep() to pre-fill workspace-parent defaults**

Replace `onAddStep()`:
```java
private void onAddStep() {
    if (currentWorkflow == null) return;
    AddStepDialog dialog = new AddStepDialog(getSite().getShell(), ActionRegistry.getInstance());
    if (dialog.open() == Window.OK) {
        Step step = dialog.getResult();
        com.example.automation.api.IAction action =
            ActionRegistry.getInstance().getAction(step.getActionId());
        if (action != null) {
            String wsParent = StepOperations.workspaceParent();
            if (wsParent != null) {
                for (String key : action.getDefaultConfig().keySet()) {
                    String defVal = action.getDefaultConfig().get(key);
                    if ((defVal == null || defVal.isBlank())
                            && (StepOperations.isDirField(key) || StepOperations.isFileField(key))) {
                        step.getConfig().put(key, wsParent);
                    }
                }
            }
        }
        currentWorkflow.getSteps().add(step);
        save();
        viewer.refresh();
        updateButtonStates();
    }
}
```

- [ ] **Step 9: Verify build passes**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 10: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java
git commit -m "feat: add Duplicate Step button, multi-select block move, and workspace-parent dir defaults"
```

---

### Task 7: Fix `ProjectComboBoxCellEditor` — refresh on focus

**Files:**
- Modify: `com.example.automation/src/main/java/com/example/automation/ProjectComboBoxCellEditor.java`

- [ ] **Step 1: Move project population from createControl() to doSetFocus()**

Replace the entire file:
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
 * project names, sorted alphabetically. The list is rebuilt on every focus
 * event so it is never stale. The user may also type a name not in the list.
 */
public class ProjectComboBoxCellEditor extends CellEditor {

    private Combo combo;

    public ProjectComboBoxCellEditor(Composite parent) {
        super(parent);
    }

    @Override
    protected Control createControl(Composite parent) {
        combo = new Combo(parent, SWT.DROP_DOWN);
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
    protected void doSetFocus() {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        String[] names = Arrays.stream(projects)
            .map(IProject::getName)
            .sorted(Comparator.naturalOrder())
            .toArray(String[]::new);
        combo.setItems(names);
        combo.setFocus();
    }

    @Override
    protected Object doGetValue() {
        return combo.getText();
    }

    @Override
    protected void doSetValue(Object value) {
        combo.setText(value instanceof String s ? s : "");
    }
}
```

- [ ] **Step 2: Verify build passes**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ProjectComboBoxCellEditor.java
git commit -m "fix: refresh project list on focus in ProjectComboBoxCellEditor to prevent stale/empty combo"
```

---

### Task 8: SWTBot integration tests

**Files:**
- Create: `com.example.automation.tests/src/main/java/com/example/automation/tests/BoldStepTest.java`
- Create: `com.example.automation.tests/src/main/java/com/example/automation/tests/DuplicateStepTest.java`
- Create: `com.example.automation.tests/src/main/java/com/example/automation/tests/MultiSelectMoveTest.java`
- Create: `com.example.automation.tests/src/main/java/com/example/automation/tests/StepNameEditTest.java`

All tests follow the same pattern as `StepManagementTest`: `@BeforeClass` creates fixtures via `WorkflowRepository`, `@AfterClass` cleans up. The `loadWorkflow(String displayName)` helper (copy from `StepManagementTest`) opens the view and selects the workflow by display name.

- [ ] **Step 1: Create BoldStepTest.java**

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

public class BoldStepTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF = "bold-step-test";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        Workflow wf = new Workflow(WF, "Bold Step Test", "");
        Step boldStep = new Step("refresh-all");
        boldStep.setBold(true);
        wf.getSteps().add(boldStep);
        repo.save(wf);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF);
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
    public void boldStep_persistedAndRendered_doesNotCrash() {
        // Verifies that a workflow with bold=true loads and renders without error.
        // Font weight cannot be inspected via SWTBot, so we check the step is shown.
        loadWorkflow("Bold Step Test");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(1, table.rowCount());
        // The step name "Refresh All" must still be visible (not blank or crashed)
        assertFalse("step name must not be empty", table.cell(0, 1).isBlank());
    }

    @Test
    public void boldFalseStep_renderedNormally_doesNotCrash() {
        // Verifies that bold=false (default) loads without error alongside bold=true.
        Workflow wf = new Workflow("bold-false-test", "Bold False Test", "");
        Step normal = new Step("refresh-all");
        wf.getSteps().add(normal);
        try {
            repo.save(wf);
            loadWorkflow("Bold False Test");
            SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
            assertEquals(1, table.rowCount());
            assertFalse(table.cell(0, 1).isBlank());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try { repo.delete("bold-false-test"); } catch (Exception ignored) {}
        }
    }
}
```

- [ ] **Step 2: Create DuplicateStepTest.java**

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

public class DuplicateStepTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF_SINGLE = "dup-single";
    private static final String WF_MULTI  = "dup-multi";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        Workflow single = new Workflow(WF_SINGLE, "Dup Single", "");
        single.getSteps().add(new Step("refresh-all"));
        repo.save(single);

        Workflow multi = new Workflow(WF_MULTI, "Dup Multi", "");
        multi.getSteps().add(new Step("refresh-all"));
        multi.getSteps().add(new Step("refresh-project"));
        repo.save(multi);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF_SINGLE);
        repo.delete(WF_MULTI);
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
    public void duplicateSingleStep_insertsOneCopyBelow() {
        loadWorkflow("Dup Single");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(1, table.rowCount());
        String originalName = table.cell(0, 1);

        table.click(0, 1);
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Duplicate Step").click();

        assertEquals("Table must have 2 rows after duplicate", 2, table.rowCount());
        assertEquals("Row 0 must be original", originalName, table.cell(0, 1));
        assertEquals("Row 1 must be the copy", originalName, table.cell(1, 1));
    }

    @Test
    public void duplicateContiguousSelection_insertsAllCopiesBelow() {
        loadWorkflow("Dup Multi");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(2, table.rowCount());

        String name0 = table.cell(0, 1);
        String name1 = table.cell(1, 1);

        // Select both rows via keyboard selection: click row 0, then shift+click row 1
        table.click(0, 1);
        table.getTableItem(1).widget.getDisplay().syncExec(
            () -> table.widget.select(0, 1));
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Duplicate Step").click();

        assertEquals("Table must have 4 rows after duplicating 2 steps", 4, table.rowCount());
        assertEquals(name0, table.cell(0, 1));
        assertEquals(name1, table.cell(1, 1));
        assertEquals("Copy of row 0 must be at row 2", name0, table.cell(2, 1));
        assertEquals("Copy of row 1 must be at row 3", name1, table.cell(3, 1));
    }
}
```

- [ ] **Step 3: Create MultiSelectMoveTest.java**

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

public class MultiSelectMoveTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF = "multi-move-test";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        Workflow wf = new Workflow(WF, "Multi Move Test", "");
        wf.getSteps().add(new Step("refresh-all"));
        wf.getSteps().add(new Step("refresh-project"));
        wf.getSteps().add(new Step("shell-command"));
        repo.save(wf);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF);
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
    public void moveUpBlock_shiftsContiguousSelectionUp() {
        loadWorkflow("Multi Move Test");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(3, table.rowCount());

        String name0 = table.cell(0, 1); // Refresh All
        String name1 = table.cell(1, 1); // Refresh Project
        String name2 = table.cell(2, 1); // Shell Command

        // Select rows 1 and 2
        table.click(1, 1);
        table.getTableItem(2).widget.getDisplay().syncExec(() ->
            table.getTableItem(2).select());

        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Move Step Up").click();

        assertEquals("Row 0 must be what was row 1", name1, table.cell(0, 1));
        assertEquals("Row 1 must be what was row 2", name2, table.cell(1, 1));
        assertEquals("Row 2 must be what was row 0", name0, table.cell(2, 1));
    }

    @Test
    public void moveDownBlock_shiftsContiguousSelectionDown() {
        loadWorkflow("Multi Move Test");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(3, table.rowCount());

        String name0 = table.cell(0, 1);
        String name1 = table.cell(1, 1);
        String name2 = table.cell(2, 1);

        // Select rows 0 and 1
        table.click(0, 1);
        table.getTableItem(1).widget.getDisplay().syncExec(() ->
            table.getTableItem(1).select());

        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Move Step Down").click();

        assertEquals("Row 0 must be what was row 2", name2, table.cell(0, 1));
        assertEquals("Row 1 must be what was row 0", name0, table.cell(1, 1));
        assertEquals("Row 2 must be what was row 1", name1, table.cell(2, 1));
    }
}
```

- [ ] **Step 4: Create StepNameEditTest.java**

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

public class StepNameEditTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF_NAMED   = "step-name-named";
    private static final String WF_DEFAULT = "step-name-default";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        // Workflow with a named step
        Workflow named = new Workflow(WF_NAMED, "Step Name Named", "");
        Step s1 = new Step("refresh-all");
        s1.setName("My Custom Name");
        named.getSteps().add(s1);
        repo.save(named);

        // Workflow with a step that has no custom name (uses action display name)
        Workflow def = new Workflow(WF_DEFAULT, "Step Name Default", "");
        def.getSteps().add(new Step("refresh-all"));
        repo.save(def);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF_NAMED);
        repo.delete(WF_DEFAULT);
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
    public void customName_showsInTableNameColumn() {
        loadWorkflow("Step Name Named");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(1, table.rowCount());
        assertEquals("My Custom Name", table.cell(0, 1));
    }

    @Test
    public void noCustomName_showsActionDisplayName() {
        loadWorkflow("Step Name Default");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(1, table.rowCount());
        // ActionRegistry returns "Refresh All" for "refresh-all"
        assertEquals("Refresh All", table.cell(0, 1));
    }
}
```

- [ ] **Step 5: Run all tests**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 6: Commit**

```
git add \
  com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/BoldStepTest.java \
  com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/DuplicateStepTest.java \
  com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MultiSelectMoveTest.java \
  com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepNameEditTest.java
git commit -m "test: add SWTBot integration tests for bold, duplicate, multi-select move, and step name"
```

---

### Task 9: Update p2 local-repo and push

- [ ] **Step 1: Rebuild to regenerate local-repo**

```
cd com.example.automation.parent && mvn clean verify -q
```
Expected: `BUILD SUCCESS`. The site module's `maven-antrun-plugin` copies the new `1.2.0.*` artefacts into `local-repo/p2/automation-plugin/`.

- [ ] **Step 2: Commit local-repo update**

```
git add com.example.automation.parent/local-repo/
git commit -m "chore: update p2 update site with 1.2.0 build"
```

- [ ] **Step 3: Push**

```
git push origin main
```
