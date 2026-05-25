# Action Extension Point and API — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define the `IAction`/`IActionContext` contract, declare an Eclipse extension point so third-party plugins can contribute actions, and provide an `ActionRegistry` that discovers and caches contributed actions.

**Architecture:** Three interfaces/classes in a new `com.example.automation.api` package. The extension point is declared in `plugin.xml` with an XSD schema; `ActionRegistry.getInstance()` reads `Platform.getExtensionRegistry()` at runtime. A package-accessible `ActionRegistry(List<IAction>)` constructor allows tests to inject fake actions without needing OSGi. Tests are written first (TDD).

**Tech Stack:** Java 17, OSGi/Tycho 3.0.5, Eclipse extension point API (`IExtensionRegistry`, `IConfigurationElement`), JUnit 4

---

## File Map

| File | Change |
|---|---|
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/IAction.java` | New |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/IActionContext.java` | New |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/ActionRegistry.java` | New |
| `com.example.automation.parent/com.example.automation/schema/actions.exsd` | New |
| `com.example.automation.parent/com.example.automation/plugin.xml` | Add `<extension-point>` declaration |
| `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF` | Add `com.example.automation.api` to `Export-Package` |
| `com.example.automation.parent/com.example.automation/build.properties` | Add `schema/` to `bin.includes` |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ActionRegistryTest.java` | New |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/IActionContractTest.java` | New |

---

### Task 1: Write failing tests (TDD — test first)

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ActionRegistryTest.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/IActionContractTest.java`

Write both test classes before any implementation exists. The build will fail at compilation because `com.example.automation.api` doesn't exist yet — that is expected.

- [ ] **Step 1: Create ActionRegistryTest.java**

Create `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ActionRegistryTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class ActionRegistryTest {

    private static IAction stub(String id) {
        return new IAction() {
            @Override public String getId() { return id; }
            @Override public String getName() { return "Name-" + id; }
            @Override public String getDescription() { return "Desc-" + id; }
            @Override public Map<String, String> getDefaultConfig() { return Collections.emptyMap(); }
            @Override public List<String> validate(Map<String, String> config) { return Collections.emptyList(); }
            @Override public void execute(Map<String, String> config, IActionContext context) {}
        };
    }

    @Test
    public void getAllActionsReturnsBoth() {
        IAction a = stub("a");
        IAction b = stub("b");
        ActionRegistry reg = new ActionRegistry(Arrays.asList(a, b));
        List<IAction> all = reg.getAllActions();
        assertEquals(2, all.size());
        assertTrue(all.contains(a));
        assertTrue(all.contains(b));
    }

    @Test
    public void getActionFindsById() {
        IAction a = stub("my-action");
        ActionRegistry reg = new ActionRegistry(Arrays.asList(a));
        assertSame(a, reg.getAction("my-action"));
    }

    @Test
    public void getActionReturnsNullForUnknown() {
        ActionRegistry reg = new ActionRegistry(Collections.emptyList());
        assertNull(reg.getAction("missing"));
    }

    @Test
    public void duplicateIdLastWins() {
        IAction first = stub("dup");
        IAction second = new IAction() {
            @Override public String getId() { return "dup"; }
            @Override public String getName() { return "Second"; }
            @Override public String getDescription() { return "desc"; }
            @Override public Map<String, String> getDefaultConfig() { return Collections.emptyMap(); }
            @Override public List<String> validate(Map<String, String> config) { return Collections.emptyList(); }
            @Override public void execute(Map<String, String> config, IActionContext context) {}
        };
        ActionRegistry reg = new ActionRegistry(Arrays.asList(first, second));
        assertEquals("Second", reg.getAction("dup").getName());
    }
}
```

- [ ] **Step 2: Create IActionContractTest.java**

Create `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/IActionContractTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class IActionContractTest {

    private static class FakeContext implements IActionContext {
        boolean cancelled = false;
        int lastProgress = -1;
        final OutputStream out = new ByteArrayOutputStream();
        final OutputStream err = new ByteArrayOutputStream();

        @Override public void setProgress(int percent) { lastProgress = percent; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public OutputStream getOutputStream() { return out; }
        @Override public OutputStream getErrorStream() { return err; }
    }

    private static class TestAction implements IAction {
        @Override public String getId() { return "test.action"; }
        @Override public String getName() { return "Test"; }
        @Override public String getDescription() { return "Test action"; }

        @Override
        public Map<String, String> getDefaultConfig() {
            Map<String, String> defaults = new HashMap<>();
            defaults.put("name", "default");
            return defaults;
        }

        @Override
        public List<String> validate(Map<String, String> config) {
            List<String> errors = new ArrayList<>();
            if (!config.containsKey("name") || config.get("name").isBlank()) {
                errors.add("'name' is required");
            }
            return errors;
        }

        @Override
        public void execute(Map<String, String> config, IActionContext context) throws Exception {
            context.setProgress(0);
            if (context.isCancelled()) return;
            context.setProgress(100);
        }
    }

    @Test
    public void defaultConfigNotNull() {
        assertNotNull(new TestAction().getDefaultConfig());
    }

    @Test
    public void validateEmptyListForValidConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("name", "my-workflow");
        assertTrue(new TestAction().validate(config).isEmpty());
    }

    @Test
    public void validateNonEmptyForInvalidConfig() {
        assertFalse(new TestAction().validate(Collections.emptyMap()).isEmpty());
    }

    @Test
    public void executeChecksIsCancelled() throws Exception {
        FakeContext ctx = new FakeContext();
        ctx.cancelled = true;
        new TestAction().execute(Collections.singletonMap("name", "x"), ctx);
        // isCancelled() checked after setProgress(0) — execution returns early, never reaches 100
        assertEquals(0, ctx.lastProgress);
    }

    @Test
    public void executeReportsProgress() throws Exception {
        FakeContext ctx = new FakeContext();
        new TestAction().execute(Collections.singletonMap("name", "x"), ctx);
        assertTrue("execute() must call setProgress() at least once", ctx.lastProgress >= 0);
    }
}
```

- [ ] **Step 3: Run build — confirm compilation failure**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: `BUILD FAILURE` with errors like:
```
package com.example.automation.api does not exist
```

This confirms the tests are driving the implementation.

- [ ] **Step 4: Commit the failing tests**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ActionRegistryTest.java"
git add "com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/IActionContractTest.java"
git commit -m "test: add ActionRegistryTest and IActionContractTest (failing - api not yet implemented)"
```

---

### Task 2: Implement IAction and IActionContext

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/IAction.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/IActionContext.java`

- [ ] **Step 1: Create IAction.java**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/IAction.java`:

```java
package com.example.automation.api;

import java.util.List;
import java.util.Map;

public interface IAction {

    String getId();

    String getName();

    String getDescription();

    /**
     * Returns default key/value pairs for new steps. Never null; return an empty map if there are no defaults.
     */
    Map<String, String> getDefaultConfig();

    /**
     * Validates the given config. Returns an empty list if valid; non-empty entries are shown to the user.
     * Called by the execution engine before execute().
     */
    List<String> validate(Map<String, String> config);

    /**
     * Executes the action. Must check context.isCancelled() periodically and return early if true.
     * Throws Exception on fatal error — the execution engine marks the step RED and aborts the workflow.
     */
    void execute(Map<String, String> config, IActionContext context) throws Exception;
}
```

- [ ] **Step 2: Create IActionContext.java**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/IActionContext.java`:

```java
package com.example.automation.api;

import java.io.OutputStream;

public interface IActionContext {

    /**
     * Reports execution progress. Call with values 0–100.
     * Reported to the Eclipse Progress View and the table status column.
     */
    void setProgress(int percent);

    /**
     * Returns true if the user has requested a stop. Check this periodically inside execute()
     * and return early if true.
     */
    boolean isCancelled();

    /**
     * Returns the stream for standard output. Write progress messages and results here.
     * The execution engine wires this to a per-action Eclipse Console.
     */
    OutputStream getOutputStream();

    /**
     * Returns the stream for standard error. Write error details here.
     * The execution engine wires this to a per-action Eclipse Console.
     */
    OutputStream getErrorStream();
}
```

- [ ] **Step 3: Run build — expect failure only for ActionRegistry**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: `BUILD FAILURE` with:
```
cannot find symbol: class ActionRegistry
```

`IAction` and `IActionContext` now compile; only `ActionRegistry` is still missing.

- [ ] **Step 4: Commit interfaces**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/"
git commit -m "feat: add IAction and IActionContext interfaces"
```

---

### Task 3: Implement ActionRegistry and wire up the extension point

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/ActionRegistry.java`
- Create: `com.example.automation.parent/com.example.automation/schema/actions.exsd`
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml`
- Modify: `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`
- Modify: `com.example.automation.parent/com.example.automation/build.properties`

- [ ] **Step 1: Create ActionRegistry.java**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/ActionRegistry.java`:

```java
package com.example.automation.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

public class ActionRegistry {

    private static final ILog LOG = Platform.getLog(ActionRegistry.class);
    private static final String EXTENSION_POINT = "com.example.automation.actions";

    private static ActionRegistry instance;

    private final Map<String, IAction> actionsById;
    private final List<IAction> allActions;

    /** Package-accessible for tests — bypasses the extension registry. */
    ActionRegistry(List<IAction> actions) {
        Map<String, IAction> map = new LinkedHashMap<>();
        for (IAction action : actions) {
            map.put(action.getId(), action); // duplicate id: last wins
        }
        this.actionsById = Collections.unmodifiableMap(map);
        this.allActions = Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    public static synchronized ActionRegistry getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ActionRegistry load() {
        List<IAction> actions = new ArrayList<>();
        IConfigurationElement[] elements =
                Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT);
        for (IConfigurationElement element : elements) {
            try {
                IAction action = (IAction) element.createExecutableExtension("class");
                actions.add(action);
            } catch (Exception e) {
                LOG.error("Failed to load action: " + element.getAttribute("class"), e);
            }
        }
        return new ActionRegistry(actions);
    }

    public IAction getAction(String id) {
        return actionsById.get(id);
    }

    public List<IAction> getAllActions() {
        return allActions;
    }
}
```

- [ ] **Step 2: Create schema/actions.exsd**

Create `com.example.automation.parent/com.example.automation/schema/actions.exsd`:

```xml
<?xml version='1.0' encoding='UTF-8'?>
<!-- Schema file for com.example.automation.actions extension point -->
<schema targetNamespace="com.example.automation"
        xmlns="http://www.eclipse.org/OSGI/1.0/PDESchema">
   <annotation>
      <appinfo>
         <meta.schema plugin="com.example.automation"
                      id="actions"
                      name="Automation Actions"/>
      </appinfo>
      <documentation>
         Contribute actions that can be used as steps in automation workflows.
         Each contributed class must implement com.example.automation.api.IAction
         and have a public no-arg constructor.
      </documentation>
   </annotation>

   <element name="extension">
      <annotation>
         <appinfo>
            <meta.element/>
         </appinfo>
      </annotation>
      <complexType>
         <sequence minOccurs="1" maxOccurs="unbounded">
            <element ref="action"/>
         </sequence>
         <attribute name="point" type="string" use="required"/>
         <attribute name="id" type="string"/>
         <attribute name="name" type="string"/>
      </complexType>
   </element>

   <element name="action">
      <complexType>
         <attribute name="class" type="string" use="required">
            <annotation>
               <documentation>
                  Fully qualified class name of the IAction implementation.
                  Must have a public no-arg constructor.
               </documentation>
               <appinfo>
                  <meta.attribute kind="java"
                                  basedOn=":com.example.automation.api.IAction"/>
               </appinfo>
            </annotation>
         </attribute>
      </complexType>
   </element>

   <annotation>
      <appinfo>
         <meta.section type="since"/>
      </appinfo>
      <documentation>1.0.0</documentation>
   </annotation>
</schema>
```

- [ ] **Step 3: Update plugin.xml — add extension-point declaration**

Replace `com.example.automation.parent/com.example.automation/plugin.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>

  <extension-point
      id="actions"
      name="Automation Actions"
      schema="schema/actions.exsd"/>

  <extension point="org.eclipse.ui.views">
    <view
        id="com.example.automation.view"
        name="Automation"
        class="com.example.automation.AutomationView"
        icon="icons/configs.png"
        restorable="true"/>
  </extension>

  <extension point="org.eclipse.ui.commands">
    <command
        id="com.example.automation.showView"
        name="Automation"/>
  </extension>

  <extension point="org.eclipse.ui.handlers">
    <handler
        commandId="com.example.automation.showView"
        class="com.example.automation.ShowAutomationViewHandler"/>
  </extension>

  <extension point="org.eclipse.ui.menus">
    <menuContribution locationURI="menu:project?after=additions">
      <command
          commandId="com.example.automation.showView"
          label="Automation"
          icon="icons/configs.png"/>
    </menuContribution>
  </extension>

</plugin>
```

- [ ] **Step 4: Update MANIFEST.MF — add Export-Package for api**

Replace `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF` with:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Automation
Bundle-SymbolicName: com.example.automation;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Activator: com.example.automation.Activator
Require-Bundle: org.eclipse.ui,
 org.eclipse.core.runtime,
 org.eclipse.core.commands
Bundle-RequiredExecutionEnvironment: JavaSE-17
Bundle-ActivationPolicy: lazy
Bundle-ClassPath: .,
 lib/gson-2.10.1.jar
Export-Package: com.example.automation.api,
 com.example.automation.model,
 com.example.automation.persistence

```

The blank line at the end is required by the OSGi spec — keep it.

- [ ] **Step 5: Update build.properties — add schema/**

Replace `com.example.automation.parent/com.example.automation/build.properties` with:

```properties
source.. = src/main/java/
output.. = target/classes/
bin.includes = META-INF/,\
               .,\
               plugin.xml,\
               icons/,\
               lib/,\
               schema/
```

- [ ] **Step 6: Run full build — all tests must pass**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected last lines:
```
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

(10 existing tests + 4 ActionRegistryTest + 5 IActionContractTest = 19 total)

- [ ] **Step 7: Commit**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/api/"
git add "com.example.automation.parent/com.example.automation/schema/"
git add "com.example.automation.parent/com.example.automation/plugin.xml"
git add "com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF"
git add "com.example.automation.parent/com.example.automation/build.properties"
git commit -m "feat: add IAction/IActionContext API, ActionRegistry, and actions extension point"
```
