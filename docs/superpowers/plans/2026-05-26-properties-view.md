# Properties View Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the Eclipse Properties View to the step table so selecting a row shows a read-only Action field and editable config key/value pairs that save immediately.

**Architecture:** `StepPropertySource` implements `IPropertySource` for a `Step`; `StepAdapterFactory` creates it on demand; `AutomationView` registers the factory programmatically on create and unregisters on dispose, and calls `getSite().setSelectionProvider(viewer)` so the Properties View receives selections.

**Tech Stack:** Java 17, Eclipse SWT/JFace, `org.eclipse.ui.views.properties`, OSGi/Tycho, JUnit 4.

---

## File Structure

| File | Role |
|---|---|
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java` | **New.** 5 plain JUnit 4 unit tests — no running display needed. |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java` | **New.** `IPropertySource` implementation for `Step`. |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepAdapterFactory.java` | **New.** `IAdapterFactory` that creates `StepPropertySource` per selected step. |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java` | **Modified.** Add `adapterFactory` field; register in `createPartControl()`; unregister in `dispose()`. |

No `MANIFEST.MF` changes — `org.eclipse.core.runtime` and `org.eclipse.ui` (both already required) provide all needed types.

---

### Task 1: Write failing StepPropertySource unit tests

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java`

- [ ] **Step 1: Create the test file**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.TextPropertyDescriptor;
import org.junit.Test;

import com.example.automation.StepPropertySource;
import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;

public class StepPropertySourceTest {

    private static IAction stub(String id, Map<String, String> defaults) {
        return new IAction() {
            @Override public String getId()          { return id; }
            @Override public String getName()        { return id; }
            @Override public String getDescription() { return ""; }
            @Override public Map<String, String> getDefaultConfig() { return defaults; }
            @Override public List<String> validate(Map<String, String> c) { return List.of(); }
            @Override public void execute(Map<String, String> c, IActionContext ctx) {}
        };
    }

    private StepPropertySource src(Step step, ActionRegistry reg, boolean[] saved) {
        return new StepPropertySource(step, reg, () -> saved[0] = true);
    }

    @Test
    public void actionProperty_isReadOnly() {
        Step step = new Step("my.action");
        ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
        boolean[] saved = {false};

        StepPropertySource s = src(step, reg, saved);
        IPropertyDescriptor[] descs = s.getPropertyDescriptors();

        assertEquals(1, descs.length);
        assertEquals("action", descs[0].getId());
        assertFalse("action descriptor must not be a TextPropertyDescriptor",
            descs[0] instanceof TextPropertyDescriptor);
        assertEquals("my.action", s.getPropertyValue("action"));
        assertFalse(saved[0]);
    }

    @Test
    public void configProperty_returnsCurrentValue() {
        Step step = new Step("my.action");
        step.getConfig().put("timeout", "30");
        ActionRegistry reg = new ActionRegistry(List.of(
            stub("my.action", Map.of("timeout", "10"))));
        boolean[] saved = {false};

        assertEquals("30", src(step, reg, saved).getPropertyValue("timeout"));
    }

    @Test
    public void setPropertyValue_updatesConfigAndSaves() {
        Step step = new Step("my.action");
        ActionRegistry reg = new ActionRegistry(List.of(
            stub("my.action", Map.of("timeout", "10"))));
        boolean[] saved = {false};

        src(step, reg, saved).setPropertyValue("timeout", "60");

        assertEquals("60", step.getConfig().get("timeout"));
        assertTrue(saved[0]);
    }

    @Test
    public void resetToDefault_restoresDefaultAndSaves() {
        Step step = new Step("my.action");
        step.getConfig().put("timeout", "99");
        ActionRegistry reg = new ActionRegistry(List.of(
            stub("my.action", Map.of("timeout", "10"))));
        boolean[] saved = {false};

        src(step, reg, saved).resetPropertyValue("timeout");

        assertEquals("10", step.getConfig().get("timeout"));
        assertTrue(saved[0]);
    }

    @Test
    public void unknownAction_fallsBackToExistingConfig() {
        Step step = new Step("unknown");
        step.getConfig().put("foo", "bar");
        ActionRegistry reg = new ActionRegistry(List.of());
        boolean[] saved = {false};

        IPropertyDescriptor[] descs = src(step, reg, saved).getPropertyDescriptors();

        // "action" + "foo"
        assertEquals(2, descs.length);
        boolean foundFoo = false;
        for (IPropertyDescriptor d : descs) {
            if ("foo".equals(d.getId())) {
                assertTrue(d instanceof TextPropertyDescriptor);
                foundFoo = true;
            }
        }
        assertTrue("foo key must appear as an editable TextPropertyDescriptor", foundFoo);
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
cd com.example.automation.parent
mvn verify -q
```

Expected: `BUILD FAILURE` — `StepPropertySource` does not exist yet.

- [ ] **Step 3: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java
git commit -m "test: add failing StepPropertySource unit tests"
```

---

### Task 2: Implement StepPropertySource and StepAdapterFactory

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepAdapterFactory.java`

- [ ] **Step 1: Create StepPropertySource.java**

```java
package com.example.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.PropertyDescriptor;
import org.eclipse.ui.views.properties.TextPropertyDescriptor;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.model.Step;

public class StepPropertySource implements IPropertySource {

    private static final String PROP_ACTION = "action";

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
        for (String key : configKeys()) {
            TextPropertyDescriptor d = new TextPropertyDescriptor(key, key);
            d.setCategory("Config");
            list.add(d);
        }
        return list.toArray(new IPropertyDescriptor[0]);
    }

    @Override
    public Object getPropertyValue(Object id) {
        if (PROP_ACTION.equals(id)) {
            String aid = step.getActionId();
            return aid != null ? aid : "";
        }
        return step.getConfig().getOrDefault((String) id, "");
    }

    @Override
    public void setPropertyValue(Object id, Object value) {
        if (PROP_ACTION.equals(id)) return;
        step.getConfig().put((String) id, (String) value);
        save.run();
    }

    @Override
    public void resetPropertyValue(Object id) {
        if (PROP_ACTION.equals(id)) return;
        IAction action = registry.getAction(step.getActionId());
        if (action != null) {
            String def = action.getDefaultConfig().get((String) id);
            if (def != null) {
                step.getConfig().put((String) id, def);
                save.run();
            }
        }
    }

    @Override
    public boolean isPropertySet(Object id) {
        if (PROP_ACTION.equals(id)) return false;
        return step.getConfig().containsKey((String) id);
    }

    @Override
    public Object getEditableValue() {
        return null;
    }

    private List<String> configKeys() {
        IAction action = registry.getAction(step.getActionId());
        Map<String, String> source = (action != null) ? action.getDefaultConfig() : step.getConfig();
        return new ArrayList<>(source.keySet());
    }
}
```

- [ ] **Step 2: Create StepAdapterFactory.java**

```java
package com.example.automation;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.ui.views.properties.IPropertySource;

import com.example.automation.api.ActionRegistry;
import com.example.automation.model.Step;

public class StepAdapterFactory implements IAdapterFactory {

    private final Runnable save;

    public StepAdapterFactory(Runnable save) {
        this.save = save;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
        if (adapterType == IPropertySource.class && adaptableObject instanceof Step step) {
            return (T) new StepPropertySource(step, ActionRegistry.getInstance(), save);
        }
        return null;
    }

    @Override
    public Class<?>[] getAdapterList() {
        return new Class<?>[] { IPropertySource.class };
    }
}
```

- [ ] **Step 3: Run to confirm 33 tests pass**

```
cd com.example.automation.parent
mvn verify -q
```

Expected: `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`

(28 existing + 5 new `StepPropertySourceTest` tests)

- [ ] **Step 4: Commit**

```
git add com.example.automation/src/main/java/com/example/automation/StepPropertySource.java
git add com.example.automation/src/main/java/com/example/automation/StepAdapterFactory.java
git commit -m "feat: add StepPropertySource and StepAdapterFactory"
```

---

### Task 3: Wire AutomationView

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java`

- [ ] **Step 1: Add the `adapterFactory` field**

In `AutomationView.java`, after the `private WorkflowRunner activeRunner;` field (around line 52), add:

```java
private StepAdapterFactory adapterFactory;
```

- [ ] **Step 2: Register factory and selection provider in `createPartControl()`**

The current `createPartControl()` ends with:

```java
        loadWorkflows();
        updateButtonStates();
    }
```

Replace those last two lines with:

```java
        loadWorkflows();
        updateButtonStates();
        getSite().setSelectionProvider(viewer);
        adapterFactory = new StepAdapterFactory(this::save);
        Platform.getAdapterManager().registerAdapters(adapterFactory, Step.class);
    }
```

- [ ] **Step 3: Unregister factory in `dispose()`**

The current `dispose()` is:

```java
    @Override
    public void dispose() {
        if (activeRunner != null) activeRunner.cancel();
        super.dispose();
    }
```

Replace it with:

```java
    @Override
    public void dispose() {
        if (adapterFactory != null) Platform.getAdapterManager().unregisterAdapters(adapterFactory);
        if (activeRunner != null) activeRunner.cancel();
        super.dispose();
    }
```

- [ ] **Step 4: Run all tests**

```
cd com.example.automation.parent
mvn verify -q
```

Expected: `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation/src/main/java/com/example/automation/AutomationView.java
git commit -m "feat: register Properties View adapter and selection provider in AutomationView"
```
