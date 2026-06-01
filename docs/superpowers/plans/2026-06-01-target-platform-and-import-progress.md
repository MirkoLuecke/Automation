# Target Platform Activation & Import Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `SetActiveTargetPlatformAction` that resolves and activates a `.target` file with live progress, add a progress bridge to `ImportMavenProjectAction`, and wire both into the bundled `setup-automation-plugin.json` workflow.

**Architecture:** One new action class (`SetActiveTargetPlatformAction`) uses `ITargetPlatformService` from `org.eclipse.pde.core` to load and resolve the target, then `LoadTargetDefinitionJob` from `org.eclipse.pde.ui` to activate it. `ImportMavenProjectAction` gets a private inner class `ImportMonitor` that bridges `IProgressMonitor.worked()` calls to `IActionContext.setProgress()`. Both changes are inside the `com.example.automation` bundle; the workflow JSON gains one new step.

**Tech Stack:** Java 17, OSGi/Tycho 3.0.5, Eclipse PDE (`org.eclipse.pde.core`, `org.eclipse.pde.ui`), M2E (`org.eclipse.m2e.core`), JUnit 4

---

## File Map

| File | Change |
|------|--------|
| `com.example.automation/META-INF/MANIFEST.MF` | Add `org.eclipse.pde.core` and `org.eclipse.pde.ui` to `Require-Bundle` |
| `com.example.automation/plugin.xml` | Register `SetActiveTargetPlatformAction` in the `com.example.automation.actions` extension point |
| `com.example.automation/src/main/java/com/example/automation/actions/SetActiveTargetPlatformAction.java` | New file — full action implementation |
| `com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java` | Add `ImportMonitor` inner class; pass it to `importProjects()` |
| `com.example.automation/src/main/resources/workflows/setup-automation-plugin.json` | Insert `set-active-target-platform` step |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/SetActiveTargetPlatformActionTest.java` | New file — unit tests for `validate()` and `getDefaultConfig()` |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/ImportMavenProjectActionTest.java` | Add progress-tracking test |

---

## Task 1: Add PDE dependencies to MANIFEST.MF and plugin.xml

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml`

- [ ] **Step 1: Add `org.eclipse.pde.core` and `org.eclipse.pde.ui` to MANIFEST.MF**

Open `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`. The current `Require-Bundle` ends with `org.eclipse.core.variables`. Add two more entries:

```
Require-Bundle: org.eclipse.ui,
 org.eclipse.ui.views,
 org.eclipse.core.runtime,
 org.eclipse.core.commands,
 org.eclipse.jface,
 org.eclipse.swt,
 org.eclipse.ui.console,
 org.eclipse.core.resources,
 org.eclipse.debug.core,
 org.eclipse.debug.ui,
 org.eclipse.m2e.core,
 org.eclipse.core.variables,
 org.eclipse.pde.core,
 org.eclipse.pde.ui
```

- [ ] **Step 2: Register the new action in plugin.xml**

Open `com.example.automation.parent/com.example.automation/plugin.xml`. After the last `<extension point="com.example.automation.actions">` block (the one for `SetMavenSettingsAction`), add:

```xml
  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.SetActiveTargetPlatformAction"/>
  </extension>
```

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF
git add com.example.automation.parent/com.example.automation/plugin.xml
git commit -m "feat: add org.eclipse.pde.core/ui dependencies and register SetActiveTargetPlatformAction"
```

---

## Task 2: SetActiveTargetPlatformAction — validate() and skeleton

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetActiveTargetPlatformActionTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetActiveTargetPlatformAction.java` (skeleton only — `execute()` throws `UnsupportedOperationException`)

- [ ] **Step 1: Write the failing tests**

Create `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetActiveTargetPlatformActionTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.SetActiveTargetPlatformAction;

public class SetActiveTargetPlatformActionTest {

    @Test
    public void validate_blankTargetFile_rejected() {
        List<String> errors = new SetActiveTargetPlatformAction()
            .validate(Map.of("targetFile", ""));
        assertTrue("expected targetFile error",
            errors.stream().anyMatch(e -> e.contains("targetFile")));
    }

    @Test
    public void defaultConfig_containsTargetFileKey() {
        assertTrue(new SetActiveTargetPlatformAction()
            .getDefaultConfig().containsKey("targetFile"));
    }

    @Test
    public void getId_returnsExpectedId() {
        assertEquals("set-active-target-platform",
            new SetActiveTargetPlatformAction().getId());
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

In Eclipse: right-click `SetActiveTargetPlatformActionTest.java` → Run As → JUnit Plug-in Test.

Expected: compilation error — `SetActiveTargetPlatformAction` does not exist.

- [ ] **Step 3: Create the skeleton action**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetActiveTargetPlatformAction.java`:

```java
package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class SetActiveTargetPlatformAction implements IAction {

    @Override public String getId()          { return "set-active-target-platform"; }
    @Override public String getName()        { return "Set Active Target Platform"; }
    @Override public String getDescription() {
        return "Loads, resolves, and activates a .target file as the Eclipse workspace target platform.";
    }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("targetFile", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        Bundle pdeCore = Platform.getBundle("org.eclipse.pde.core");
        if (pdeCore == null || pdeCore.getState() != Bundle.ACTIVE)
            errors.add("PDE Core (org.eclipse.pde.core) is not installed or not active.");
        if (config.getOrDefault("targetFile", "").isBlank())
            errors.add("targetFile must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

In Eclipse: right-click `SetActiveTargetPlatformActionTest.java` → Run As → JUnit Plug-in Test.

Expected: all 3 tests pass (the PDE bundle check in `validate()` passes because the tests run inside Eclipse which has PDE active).

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetActiveTargetPlatformAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetActiveTargetPlatformActionTest.java
git commit -m "feat: add SetActiveTargetPlatformAction skeleton with validate()"
```

---

## Task 3: SetActiveTargetPlatformAction — execute() with TargetMonitor

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetActiveTargetPlatformAction.java`

`execute()` cannot be meaningfully unit-tested without a real Eclipse + network (it downloads p2 content). The test for this task is: after the full workflow runs, verify the target platform is active and no compile errors appear.

- [ ] **Step 1: Replace the skeleton execute() with the full implementation**

Rewrite `SetActiveTargetPlatformAction.java` in full:

```java
package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.pde.core.target.ITargetDefinition;
import org.eclipse.pde.core.target.ITargetHandle;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.internal.ui.preferences.LoadTargetDefinitionJob;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class SetActiveTargetPlatformAction implements IAction {

    @Override public String getId()          { return "set-active-target-platform"; }
    @Override public String getName()        { return "Set Active Target Platform"; }
    @Override public String getDescription() {
        return "Loads, resolves, and activates a .target file as the Eclipse workspace target platform.";
    }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("targetFile", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        Bundle pdeCore = Platform.getBundle("org.eclipse.pde.core");
        if (pdeCore == null || pdeCore.getState() != Bundle.ACTIVE)
            errors.add("PDE Core (org.eclipse.pde.core) is not installed or not active.");
        if (config.getOrDefault("targetFile", "").isBlank())
            errors.add("targetFile must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String targetPath = config.get("targetFile");
        if (targetPath == null || targetPath.isBlank())
            throw new IllegalArgumentException("targetFile must not be blank");

        File targetFile = new File(targetPath);
        if (!targetFile.exists())
            throw new Exception(".target file not found at: " + targetFile.getAbsolutePath());

        Bundle pdeCore = Platform.getBundle("org.eclipse.pde.core");
        BundleContext bc = pdeCore.getBundleContext();
        ServiceReference<ITargetPlatformService> ref =
            bc.getServiceReference(ITargetPlatformService.class);
        ITargetPlatformService service = bc.getService(ref);

        ITargetHandle handle = service.getTarget(targetFile.toURI());
        ITargetDefinition definition = handle.getTargetDefinition();

        context.getStdout().println("Resolving target platform: " + targetFile.getName());
        IStatus status = definition.resolve(new TargetMonitor(context));
        if (status.getSeverity() == IStatus.ERROR)
            throw new Exception("Target platform resolution failed: " + status.getMessage());

        context.getStdout().println("Activating target platform...");
        LoadTargetDefinitionJob job = new LoadTargetDefinitionJob(definition);
        job.schedule();
        job.join();

        context.setProgress(100);
    }

    private static final class TargetMonitor implements IProgressMonitor {
        private final IActionContext context;
        private int total = 1;
        private int done  = 0;

        TargetMonitor(IActionContext context) { this.context = context; }

        @Override
        public void beginTask(String name, int totalWork) {
            this.total = totalWork > 0 ? totalWork : 1;
            context.setProgress(0);
        }

        @Override
        public void worked(int work) {
            done += work;
            context.setProgress(Math.min(90, done * 90 / total));
        }

        @Override public void done()                      {}
        @Override public boolean isCanceled()             { return false; }
        @Override public void setCanceled(boolean value)  {}
        @Override public void setTaskName(String name)    {}
        @Override public void subTask(String name)        {}
        @Override public void internalWorked(double work) {}
    }
}
```

**Note on `LoadTargetDefinitionJob`:** This class is in the internal package `org.eclipse.pde.internal.ui.preferences` of the `org.eclipse.pde.ui` bundle. Access via `Require-Bundle` works in Eclipse (the `x-internal` directive is a convention, not a hard OSGi restriction). If the compiler reports an error about restricted access, add `org.eclipse.pde.ui` to the access rules in the project's build path (in Eclipse: Project Properties → Java Build Path → Libraries → `org.eclipse.pde.ui` → Access Rules → add `org/eclipse/pde/internal/**` as Accessible).

- [ ] **Step 2: Verify the existing SetActiveTargetPlatformActionTest still passes**

In Eclipse: right-click `SetActiveTargetPlatformActionTest.java` → Run As → JUnit Plug-in Test.

Expected: all 3 tests still pass (they only exercise `validate()` and `getDefaultConfig()`).

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetActiveTargetPlatformAction.java
git commit -m "feat: implement SetActiveTargetPlatformAction execute() with live progress"
```

---

## Task 4: ImportMavenProjectAction — ImportMonitor progress bridge

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ImportMavenProjectActionTest.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java`

- [ ] **Step 1: Write a failing test that verifies monotonic progress**

Add these imports to `ImportMavenProjectActionTest.java` (alongside existing imports):

```java
import java.util.ArrayList;
import java.util.List;
```

Add a `@Before` method and new test to `ImportMavenProjectActionTest`:

```java
@Before
public void cleanupWorkspaceBeforeTest() throws Exception {
    cleanupWorkspace();
}

@Test
public void execute_reportsMonotonicProgress() throws Exception {
    File dir = tmp.newFolder("test-import-progress");
    try (InputStream src = ImportMavenProjectActionTest.class
            .getResourceAsStream("/test-import-pom.xml")) {
        assertNotNull("test-import-pom.xml must be on classpath", src);
        Files.copy(src, new File(dir, "pom.xml").toPath());
    }

    List<Integer> progressValues = new ArrayList<>();
    IActionContext trackingContext = new IActionContext() {
        @Override public OutputStream getOutputStream() { return System.out; }
        @Override public OutputStream getErrorStream()  { return System.err; }
        @Override public void setProgress(int p)        { progressValues.add(p); }
        @Override public boolean isCancelled()          { return false; }
    };

    new ImportMavenProjectAction().execute(
        Map.of("pomPath", dir.getAbsolutePath()), trackingContext);

    assertFalse("setProgress must be called at least once", progressValues.isEmpty());
    assertEquals("last setProgress call must be 100", 100,
        (int) progressValues.get(progressValues.size() - 1));
    assertTrue("importProjects bridge must emit intermediate values (not just 0 and 100)",
        progressValues.size() >= 3);
    for (int i = 1; i < progressValues.size(); i++)
        assertTrue("progress must never decrease: " + progressValues,
            progressValues.get(i) >= progressValues.get(i - 1));
}
```

- [ ] **Step 2: Run the new test — verify it fails**

In Eclipse: right-click `ImportMavenProjectActionTest.java` → Run As → JUnit Plug-in Test.

Expected: `execute_reportsMonotonicProgress` FAILS on the `progressValues.size() >= 3` assertion. The current code only calls `setProgress(0)` then `setProgress(100)` — two values — so the bridge is needed to emit intermediate values.

- [ ] **Step 3: Add the ImportMonitor inner class and wire it up**

Open `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java`.

Add the import at the top:

```java
import org.eclipse.core.runtime.IProgressMonitor;
```

Replace the `importProjects()` call and the following `setProgress(100)`:

```java
// Before:
MavenPlugin.getProjectConfigurationManager().importProjects(
    allModules,
    new ProjectImportConfiguration(),
    new NullProgressMonitor());
context.setProgress(100);

// After:
MavenPlugin.getProjectConfigurationManager().importProjects(
    allModules,
    new ProjectImportConfiguration(),
    new ImportMonitor(context, allModules.size()));
context.setProgress(100);
```

Add the `ImportMonitor` as a private static inner class at the bottom of `ImportMavenProjectAction`, before the closing `}`:

```java
private static final class ImportMonitor implements IProgressMonitor {
    private final IActionContext context;
    private final int total;
    private int done = 0;

    ImportMonitor(IActionContext context, int total) {
        this.context = context;
        this.total = total > 0 ? total : 1;
    }

    @Override
    public void beginTask(String name, int totalWork) { context.setProgress(0); }

    @Override
    public void worked(int work) {
        done += work;
        context.setProgress(Math.min(99, done * 100 / total));
    }

    @Override public void done()                      {}
    @Override public boolean isCanceled()             { return false; }
    @Override public void setCanceled(boolean value)  {}
    @Override public void setTaskName(String name)    {}
    @Override public void subTask(String name)        {}
    @Override public void internalWorked(double work) {}
}
```

- [ ] **Step 4: Run all ImportMavenProjectActionTest tests — verify they pass**

In Eclipse: right-click `ImportMavenProjectActionTest.java` → Run As → JUnit Plug-in Test.

Expected: all tests pass. The `execute_reportsMonotonicProgress` test now sees intermediate progress values between 0 and 99 (one per module) followed by 100.

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ImportMavenProjectActionTest.java
git commit -m "feat: add ImportMonitor progress bridge to ImportMavenProjectAction"
```

---

## Task 5: Insert set-active-target-platform step in workflow JSON

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/resources/workflows/setup-automation-plugin.json`

- [ ] **Step 1: Insert the new step**

Open `setup-automation-plugin.json`. After the `import-maven-project` step object and before the `maven-run-with-progress` step object, insert:

```json
    {
      "actionId": "set-active-target-platform",
      "config": {
        "targetFile": "${project_loc:com.example.automation.parent}/platform.target"
      }
    },
```

The full `steps` array after the edit:

```json
  "steps": [
    {
      "actionId": "shell-command",
      "config": {
        "command": "echo Working directory: ${workspace_loc}/..",
        "workingDir": "${workspace_loc}/.."
      }
    },
    {
      "actionId": "write-file",
      "config": {
        "filePath": "${workspace_loc}/../settings.xml",
        "content": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd\">\n  <localRepository>${workspace_loc}/../.m2/repository</localRepository>\n</settings>"
      }
    },
    {
      "actionId": "set-maven-settings",
      "config": {
        "filePath": "${workspace_loc}/../settings.xml"
      }
    },
    {
      "actionId": "git-clone",
      "config": {
        "url": "https://github.com/MirkoLuecke/Automation",
        "targetDir": "${workspace_loc}/../automation-plugin"
      }
    },
    {
      "actionId": "import-maven-project",
      "config": {
        "pomPath": "${workspace_loc}/../automation-plugin/com.example.automation.parent/pom.xml"
      }
    },
    {
      "actionId": "set-active-target-platform",
      "config": {
        "targetFile": "${project_loc:com.example.automation.parent}/platform.target"
      }
    },
    {
      "actionId": "maven-run-with-progress",
      "config": {
        "goals": "-s ${workspace_loc}/../settings.xml clean install",
        "workingDir": "${workspace_loc}/../automation-plugin/com.example.automation.parent"
      }
    },
    {
      "actionId": "maven-update-project",
      "config": {
        "projectName": "com.example.automation.parent"
      }
    },
    {
      "actionId": "refresh-all",
      "config": {}
    }
  ]
```

- [ ] **Step 2: Verify the JSON is valid**

In Eclipse, open the file and check there are no red squiggles. Or run:

```
python -c "import json,sys; json.load(open(sys.argv[1]))" com.example.automation.parent/com.example.automation/src/main/resources/workflows/setup-automation-plugin.json
```

Expected: no output (valid JSON).

- [ ] **Step 3: Commit**

```
git add "com.example.automation.parent/com.example.automation/src/main/resources/workflows/setup-automation-plugin.json"
git commit -m "feat: add set-active-target-platform step to setup-automation-plugin workflow"
```
