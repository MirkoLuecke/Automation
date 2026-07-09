# Batch Features and Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 10 features and fixes: remove variable resolution from CreateFile content, fix ImportMavenProject PathType, insert-after-selection for new steps, per-step retry config (model + UI + execution), migrate WorkflowRunner to a background Job with workspace refresh, replace Duplicate with Copy+Paste, persist last opened workflow, and three new actions (SetXmlTagText, SetMavenPreferences, SetBuildAutomatically).

**Architecture:** Changes span the action layer (new actions, fixes), the step data model (retry fields), the execution engine (WorkflowRunner → WorkflowJob using Eclipse's Job API), and the AutomationView UI (toolbar, startup restore, insert-after-selection). All changes are backward compatible with existing workflow JSON files.

**Tech Stack:** Eclipse SWT/JFace, Eclipse Job API (`Job`, `IProgressMonitor`), Eclipse Workspace API (`IWorkspace.getRoot().refreshLocal()`), SWT `Clipboard`/`TextTransfer`, Java DOM API (`DocumentBuilder`, `Transformer`), Gson, JUnit 4, SWTBot.

---

## File Map

| File | Change |
|---|---|
| `com.example.automation/src/main/java/com/example/automation/actions/WriteFileAction.java` | Remove `EclipseVariables.resolve()` from content |
| `com.example.automation/src/main/java/com/example/automation/StepPropertySource.java` | Add retry property descriptors; fix ImportMavenProject pomPath to DIRECTORY; add combo descriptors for SetMavenPreferences and SetBuildAutomatically |
| `com.example.automation/src/main/java/com/example/automation/model/Step.java` | Add `retryOnError` (boolean, default false) and `retryWaitSeconds` (int, default 10) |
| `com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java` | Delete (replaced by WorkflowJob) |
| `com.example.automation/src/main/java/com/example/automation/WorkflowJob.java` | New — `Job` subclass, null scheduling rule, progress reporting, workspace refresh, retry |
| `com.example.automation/src/main/java/com/example/automation/AutomationView.java` | Use WorkflowJob; fix onAddStep insert-after-selection; extract `lastSelectedIndex()`; replace Duplicate with Copy+Paste; persist/restore last workflow; add IPartListener2 for paste state |
| `com.example.automation/src/main/java/com/example/automation/preferences/AutomationPreferences.java` | Add `KEY_LAST_WORKFLOW_ID` constant |
| `com.example.automation/src/main/java/com/example/automation/actions/SetXmlTagTextAction.java` | New action |
| `com.example.automation/src/main/java/com/example/automation/actions/SetMavenPreferencesAction.java` | New action |
| `com.example.automation/src/main/java/com/example/automation/actions/SetBuildAutomaticallyAction.java` | New action |
| `com.example.automation/plugin.xml` | Register 3 new actions |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/WriteFileActionTest.java` | Add test: content with `${...}` written verbatim |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/StepTest.java` | Add tests: retry field defaults and Gson round-trip |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRunnerTest.java` | Delete (replaced by WorkflowJobTest) |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowJobTest.java` | New — mirrors WorkflowRunnerTest, adds retry and workspace-refresh tests |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/SetXmlTagTextActionTest.java` | New |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/SetMavenPreferencesActionTest.java` | New |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/SetBuildAutomaticallyActionTest.java` | New |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/DuplicateStepTest.java` | Delete (replaced by CopyPasteStepTest) |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/CopyPasteStepTest.java` | New SWTBot test |

---

## Task 1: Fix WriteFileAction — remove content variable resolution

**Spec:** `WriteFileAction.execute()` must write `content` verbatim. Currently it calls `EclipseVariables.resolve(content)` which fails when content contains literal `${...}` patterns.

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/WriteFileAction.java`
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WriteFileActionTest.java`

- [ ] **Step 1: Write the failing test**

Add to `WriteFileActionTest.java`:

```java
@Test
public void execute_contentWithVariableLiteral_writtenVerbatim() throws Exception {
    File file = new File(tmp.getRoot(), "out.txt");
    String rawContent = "value=${some_undefined_variable}";
    new WriteFileAction().execute(
        Map.of("filePath", file.getAbsolutePath(), "content", rawContent),
        trackingCtx(new ArrayList<>()));
    assertEquals(rawContent, Files.readString(file.toPath()));
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run the test bundle in Eclipse (Run As → JUnit Plug-in Test). Expect: `AssertionError` because the `${}` resolution either throws or changes the value.

- [ ] **Step 3: Remove content resolution in WriteFileAction**

In `WriteFileAction.java`, change `execute()`:

```java
@Override
public void execute(Map<String, String> config, IActionContext context) throws Exception {
    String filePath = config.getOrDefault("filePath", "");
    String content  = config.getOrDefault("content", "");
    context.setProgress(0);
    filePath = EclipseVariables.resolve(filePath);
    writeFile(filePath, content);
    context.getStdout().println("Written: " + Path.of(filePath).toAbsolutePath());
    context.setProgress(100);
}
```

Also update `getDescription()` to remove the mention of variable support in content:

```java
@Override public String getDescription() { return "Writes text content to a file, creating parent directories as needed. Eclipse variables are supported in the path."; }
```

- [ ] **Step 4: Run all WriteFileActionTest tests and verify they pass**

- [ ] **Step 5: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/WriteFileAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WriteFileActionTest.java
git commit -m "fix: write file content verbatim (no variable resolution)"
```

---

## Task 2: Fix ImportMavenProject PathType — use DIRECTORY picker

**Spec:** `pomPath` ends in "Path" so `StepOperations.isFileField()` returns true, giving it a FILE picker. It must use a DIRECTORY picker because `ImportMavenProjectAction` accepts a directory.

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`

The fix is a targeted early-return case added **before** the generic `isFileField` check in `createConfigDescriptor()`.

- [ ] **Step 1: Add the DIRECTORY override for import-maven-project/pomPath**

In `StepPropertySource.java`, in `createConfigDescriptor(String key)`, insert this block immediately before the `if (StepOperations.isDirField(key))` check (currently around line 157):

```java
if ("import-maven-project".equals(step.getActionId()) && "pomPath".equals(key)) {
    return new PropertyDescriptor(key, key) {
        @Override
        public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
            return new PathCellEditor(parent, PathCellEditor.PathType.DIRECTORY);
        }
    };
}
```

- [ ] **Step 2: Verify manually**

In Eclipse: create a workflow with an "Import Maven Project" step. Click the `pomPath` field in the Properties view — the dialog must open a DIRECTORY picker (not file picker).

- [ ] **Step 3: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java
git commit -m "fix: use DIRECTORY picker for import-maven-project pomPath"
```

---

## Task 3: Add retry fields to Step model

**Spec:** `Step` gains `retryOnError` (boolean, default `false`) and `retryWaitSeconds` (int, default `10`). Missing JSON fields deserialize to defaults (Gson uses Java field defaults).

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/Step.java`
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepTest.java`

- [ ] **Step 1: Write failing tests**

Add to `StepTest.java`:

```java
@Test
public void retryOnError_defaultsFalse() {
    assertFalse(new Step("a").isRetryOnError());
}

@Test
public void retryWaitSeconds_defaultsTen() {
    assertEquals(10, new Step("a").getRetryWaitSeconds());
}

@Test
public void retry_gsonRoundTrip() {
    Step step = new Step("a");
    step.setRetryOnError(true);
    step.setRetryWaitSeconds(30);
    String json = new Gson().toJson(step);
    Step loaded = new Gson().fromJson(json, Step.class);
    assertTrue(loaded.isRetryOnError());
    assertEquals(30, loaded.getRetryWaitSeconds());
}

@Test
public void retry_missingFieldsDefaultsFromJson() {
    String json = "{\"actionId\":\"a\"}";
    Step loaded = new Gson().fromJson(json, Step.class);
    assertFalse("retryOnError must default to false", loaded.isRetryOnError());
    assertEquals("retryWaitSeconds must default to 10", 10, loaded.getRetryWaitSeconds());
}
```

- [ ] **Step 2: Run tests and verify they fail** (fields don't exist yet)

- [ ] **Step 3: Add fields to Step.java**

Replace the `Step` field block and add getters/setters. The full updated class:

```java
package com.example.automation.model;

import java.util.HashMap;
import java.util.Map;

public class Step {

    private String actionId;
    private String name;
    private boolean bold;
    private Map<String, String> config = new HashMap<>();
    private boolean retryOnError = false;
    private int retryWaitSeconds = 10;

    private transient StepStatus status = StepStatus.WHITE;
    private transient int progress = 0;

    public Step() {}

    public Step(String actionId) {
        this.actionId = actionId;
    }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isBold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }

    public Map<String, String> getConfig() { return config; }
    public void setConfig(Map<String, String> config) { this.config = config; }

    public boolean isRetryOnError() { return retryOnError; }
    public void setRetryOnError(boolean retryOnError) { this.retryOnError = retryOnError; }

    public int getRetryWaitSeconds() { return retryWaitSeconds; }
    public void setRetryWaitSeconds(int retryWaitSeconds) { this.retryWaitSeconds = retryWaitSeconds; }

    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
}
```

- [ ] **Step 4: Run StepTest and verify all tests pass**

- [ ] **Step 5: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/Step.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepTest.java
git commit -m "feat: add retryOnError and retryWaitSeconds fields to Step"
```

---

## Task 4: Create WorkflowJob (replaces WorkflowRunner)

**Spec:** A `Job` (null scheduling rule) that executes workflow steps sequentially, reports progress to Eclipse's Progress view, refreshes the Eclipse workspace after every step, and supports per-step retry. Replaces `WorkflowRunner` (daemon thread). `WorkflowRunner.java` is deleted after the migration in Task 5.

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowJob.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowJobTest.java`

- [ ] **Step 1: Write WorkflowJobTest**

Create `WorkflowJobTest.java`. This test calls `job.run(new NullProgressMonitor())` synchronously (no need for the job scheduler):

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;

import com.example.automation.WorkflowJob;
import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;

public class WorkflowJobTest {

    @FunctionalInterface
    interface ThrowingConsumer {
        void accept(IActionContext ctx) throws Exception;
    }

    private static IAction stub(String id, ThrowingConsumer body) {
        return new IAction() {
            @Override public String getId()          { return id; }
            @Override public String getName()        { return id; }
            @Override public String getDescription() { return ""; }
            @Override public Map<String, String> getDefaultConfig() { return Map.of(); }
            @Override public List<String> validate(Map<String, String> c) { return List.of(); }
            @Override public void execute(Map<String, String> config, IActionContext ctx) throws Exception {
                body.accept(ctx);
            }
        };
    }

    private void run(List<Step> steps, ActionRegistry registry) throws InterruptedException {
        WorkflowJob job = new WorkflowJob("test", steps, registry,
            Runnable::run, () -> {}, () -> {}, OutputStream.nullOutputStream(), OutputStream.nullOutputStream());
        job.schedule();
        job.join();
    }

    @Test
    public void successfulStep_statusGreen() throws Exception {
        Step step = new Step("a");
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {}))));
        assertEquals(StepStatus.GREEN, step.getStatus());
    }

    @Test
    public void unknownActionId_statusRed_nextSkipped() throws Exception {
        Step step1 = new Step("missing");
        Step step2 = new Step("ok");
        run(List.of(step1, step2), new ActionRegistry(List.of()));
        assertEquals(StepStatus.RED,   step1.getStatus());
        assertEquals(StepStatus.WHITE, step2.getStatus());
    }

    @Test
    public void throwingAction_statusRed_nextSkipped() throws Exception {
        Step step1 = new Step("fail");
        Step step2 = new Step("ok");
        run(List.of(step1, step2), new ActionRegistry(List.of(
            stub("fail", ctx -> { throw new RuntimeException("boom"); }),
            stub("ok",   ctx -> {})
        )));
        assertEquals(StepStatus.RED,   step1.getStatus());
        assertEquals(StepStatus.WHITE, step2.getStatus());
    }

    @Test
    public void progressUpdatesStep() throws Exception {
        Step step = new Step("a");
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> ctx.setProgress(50)))));
        assertEquals(50, step.getProgress());
        assertEquals(StepStatus.GREEN, step.getStatus());
    }

    @Test
    public void multipleSteps_allGreen() throws Exception {
        Step step1 = new Step("a");
        Step step2 = new Step("b");
        run(List.of(step1, step2), new ActionRegistry(List.of(
            stub("a", ctx -> {}),
            stub("b", ctx -> {})
        )));
        assertEquals(StepStatus.GREEN, step1.getStatus());
        assertEquals(StepStatus.GREEN, step2.getStatus());
    }

    @Test
    public void configPassedToAction() throws Exception {
        AtomicReference<Map<String, String>> received = new AtomicReference<>();
        Step step = new Step("a");
        step.getConfig().put("myKey", "myValue");
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {
            received.set(new HashMap<>(ctx.getConfig()));
        }))));
        assertEquals("myValue", received.get().get("myKey"));
    }

    @Test
    public void retryOnError_secondAttemptSucceeds_statusGreen() {
        AtomicInteger calls = new AtomicInteger();
        Step step = new Step("a");
        step.setRetryOnError(true);
        step.setRetryWaitSeconds(0); // no actual sleep in tests
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {
            if (calls.incrementAndGet() == 1) throw new RuntimeException("first attempt fails");
        }))));
        assertEquals(2, calls.get());
        assertEquals(StepStatus.GREEN, step.getStatus());
    }

    @Test
    public void retryOnError_bothAttemptsThrow_statusRed() {
        AtomicInteger calls = new AtomicInteger();
        Step step = new Step("a");
        step.setRetryOnError(true);
        step.setRetryWaitSeconds(0);
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {
            calls.incrementAndGet();
            throw new RuntimeException("always fails");
        }))));
        assertEquals(2, calls.get());
        assertEquals(StepStatus.RED, step.getStatus());
    }

    @Test
    public void noRetry_singleAttempt_statusRed() {
        AtomicInteger calls = new AtomicInteger();
        Step step = new Step("a");
        // retryOnError defaults to false
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {
            calls.incrementAndGet();
            throw new RuntimeException("fail");
        }))));
        assertEquals(1, calls.get());
        assertEquals(StepStatus.RED, step.getStatus());
    }
}
```

- [ ] **Step 2: Run WorkflowJobTest — verify it fails** (WorkflowJob doesn't exist yet)

- [ ] **Step 3: Create WorkflowJob.java**

```java
package com.example.automation;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.preferences.AutomationPreferences;

public class WorkflowJob extends Job {

    private final String workflowName;
    private final List<Step> steps;
    private final ActionRegistry registry;
    private final Consumer<Runnable> uiExec;
    private final Runnable onRefresh;
    private final Runnable onDone;
    private final PrintStream stdout;
    private final PrintStream stderr;

    private volatile Thread runThread;

    public WorkflowJob(String workflowName,
                       List<Step> steps,
                       ActionRegistry registry,
                       Consumer<Runnable> uiExec,
                       Runnable onRefresh,
                       Runnable onDone,
                       OutputStream stdout,
                       OutputStream stderr) {
        super("Running workflow: " + workflowName);
        this.workflowName = workflowName;
        this.steps        = steps;
        this.registry     = registry;
        this.uiExec       = uiExec;
        this.onRefresh    = onRefresh;
        this.onDone       = onDone;
        this.stdout       = stdout instanceof PrintStream ps ? ps : new PrintStream(stdout);
        this.stderr       = stderr instanceof PrintStream ps ? ps : new PrintStream(stderr);
        setRule(null);
    }

    @Override
    public boolean cancel() {
        boolean result = super.cancel();
        Thread t = runThread;
        if (t != null) t.interrupt();
        return result;
    }

    @Override
    protected void done(IStatus result) {
        uiExec.accept(onDone);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        runThread = Thread.currentThread();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String workingDir = resolveWorkingDir(svm);

        monitor.beginTask(workflowName, steps.size());
        for (Step step : steps) {
            if (monitor.isCanceled()) break;
            monitor.subTask(step.getName() != null ? step.getName() : step.getActionId());
            setStatus(step, StepStatus.YELLOW);
            Map<String, String> resolvedConfig = resolveConfig(step.getConfig(), svm);
            IAction action = registry.getAction(step.getActionId());
            if (action == null) {
                stderr.println("Unknown action: " + step.getActionId());
                setStatus(step, StepStatus.RED);
                break;
            }
            try {
                executeWithRetry(step, action, resolvedConfig, workingDir, monitor);
                if (!monitor.isCanceled()) setStatus(step, StepStatus.GREEN);
            } catch (Exception e) {
                Platform.getLog(WorkflowJob.class).error("Step failed", e);
                stderr.println("Step failed: " + e);
                e.printStackTrace(stderr);
                setStatus(step, StepStatus.RED);
                break;
            }
            refreshWorkspace(monitor);
            monitor.worked(1);
        }
        return Status.OK_STATUS;
    }

    private void executeWithRetry(Step step, IAction action,
                                   Map<String, String> resolvedConfig,
                                   String workingDir,
                                   IProgressMonitor monitor) throws Exception {
        IActionContext ctx = new ActionContextImpl(resolvedConfig, workingDir, step, stdout, stderr, uiExec, onRefresh, monitor);
        try {
            action.execute(resolvedConfig, ctx);
        } catch (Exception e) {
            if (step.isRetryOnError()) {
                monitor.subTask("Retrying " + (step.getName() != null ? step.getName() : step.getActionId())
                    + " in " + step.getRetryWaitSeconds() + "s…");
                if (step.getRetryWaitSeconds() > 0) {
                    try { Thread.sleep(step.getRetryWaitSeconds() * 1000L); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
                action.execute(resolvedConfig, ctx);
            } else {
                throw e;
            }
        }
    }

    private void refreshWorkspace(IProgressMonitor monitor) {
        try {
            ResourcesPlugin.getWorkspace().getRoot()
                .refreshLocal(IResource.DEPTH_INFINITE, monitor);
        } catch (CoreException ignored) {}
    }

    private String resolveWorkingDir(IStringVariableManager svm) {
        String raw = AutomationPreferences.getDefaultWorkingDir();
        try {
            return svm.performStringSubstitution(raw);
        } catch (CoreException e) {
            Platform.getLog(WorkflowJob.class)
                .warn("Could not resolve working directory preference: " + raw, e);
            return System.getProperty("user.home");
        }
    }

    private Map<String, String> resolveConfig(Map<String, String> config, IStringVariableManager svm) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String value = entry.getValue();
            try {
                value = svm.performStringSubstitution(value);
            } catch (CoreException e) {
                Platform.getLog(WorkflowJob.class)
                    .warn("Variable substitution failed for value: " + entry.getValue());
            }
            result.put(entry.getKey(), value);
        }
        return result;
    }

    private void setStatus(Step step, StepStatus status) {
        step.setStatus(status);
        uiExec.accept(onRefresh);
    }

    private static class ActionContextImpl implements IActionContext {
        private final Map<String, String> config;
        private final String workingDirectory;
        private final Step step;
        private final PrintStream stdout;
        private final PrintStream stderr;
        private final Consumer<Runnable> uiExec;
        private final Runnable onRefresh;
        private final IProgressMonitor monitor;

        ActionContextImpl(Map<String, String> config, String workingDirectory,
                          Step step, PrintStream stdout, PrintStream stderr,
                          Consumer<Runnable> uiExec, Runnable onRefresh,
                          IProgressMonitor monitor) {
            this.config           = config;
            this.workingDirectory = workingDirectory;
            this.step             = step;
            this.stdout           = stdout;
            this.stderr           = stderr;
            this.uiExec           = uiExec;
            this.onRefresh        = onRefresh;
            this.monitor          = monitor;
        }

        @Override public Map<String, String> getConfig()        { return config; }
        @Override public String getWorkingDirectory()           { return workingDirectory; }
        @Override public Step getStep()                         { return step; }
        @Override public PrintStream getStdout()                { return stdout; }
        @Override public PrintStream getStderr()                { return stderr; }
        @Override public OutputStream getOutputStream()         { return stdout; }
        @Override public OutputStream getErrorStream()          { return stderr; }
        @Override public Consumer<Runnable> getUiExecutor()     { return uiExec; }
        @Override public boolean isCancelled()                  { return monitor.isCanceled(); }
        @Override public void setProgress(int percent)          { step.setProgress(percent); uiExec.accept(onRefresh); }
    }
}
```

- [ ] **Step 4: Run WorkflowJobTest and verify all tests pass**

- [ ] **Step 5: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowJob.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowJobTest.java
git commit -m "feat: add WorkflowJob (Job-based runner with retry and workspace refresh)"
```

---

## Task 5: Migrate AutomationView to WorkflowJob; delete WorkflowRunner

**Spec:** `AutomationView.startRunner()` creates a `WorkflowJob` (not `WorkflowRunner`). `WorkflowRunner.java` and `WorkflowRunnerTest.java` are deleted.

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java`
- Delete: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java`
- Delete: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRunnerTest.java`

- [ ] **Step 1: Update AutomationView — change activeRunner type and startRunner()**

In `AutomationView.java`:

1. Change field `private WorkflowRunner activeRunner;` to `private WorkflowJob activeRunner;`

2. Replace `startRunner(List<Step> steps)` entirely:

```java
private void startRunner(List<Step> steps) {
    if (activeRunner != null) return;
    MessageConsole console = openConsole();
    MessageConsoleStream stdout = console.newMessageStream();
    MessageConsoleStream stderr = console.newMessageStream();
    stderr.setColor(viewer.getControl().getDisplay().getSystemColor(SWT.COLOR_RED));
    Runnable safeRefresh = () -> {
        if (!viewer.getControl().isDisposed()) viewer.refresh();
    };
    Runnable onDone = () -> {
        activeRunner = null;
        if (!viewer.getControl().isDisposed()) {
            updateButtonStates();
            viewer.refresh();
        }
    };
    String name = currentWorkflow != null ? currentWorkflow.getDisplayName() : "Workflow";
    activeRunner = new WorkflowJob(name, steps, ActionRegistry.getInstance(),
        viewer.getControl().getDisplay()::asyncExec, safeRefresh, onDone, stdout, stderr);
    updateButtonStates();
    activeRunner.schedule();
}
```

3. Update `onStop()` — the cancel call stays the same (`activeRunner.cancel()`) since `WorkflowJob.cancel()` has the same signature.

4. Remove the import for `com.example.automation.WorkflowRunner`; add import for `com.example.automation.WorkflowJob`.

- [ ] **Step 2: Delete WorkflowRunner.java**

```bash
git rm "com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java"
```

- [ ] **Step 3: Delete WorkflowRunnerTest.java**

```bash
git rm "com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRunnerTest.java"
```

- [ ] **Step 4: Compile and verify — run all existing tests, all must pass**

- [ ] **Step 5: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java
git commit -m "refactor: replace WorkflowRunner with WorkflowJob in AutomationView"
```

---

## Task 6: onAddStep — insert after selection; extract lastSelectedIndex()

**Spec:** When the user clicks "Add Step" and steps are selected, the new step is inserted after the last selected index. If nothing is selected, it's appended at end.

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java`
- Modify (existing): `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepManagementTest.java`

Check what's in `StepManagementTest.java` first. The changes are simple refactors — no new test file needed if the existing test covers onAddStep.

- [ ] **Step 1: Add `lastSelectedIndex()` helper to AutomationView**

Add this private method to `AutomationView.java`:

```java
/** Returns the highest selected row index, or -1 if nothing is selected. */
private int lastSelectedIndex() {
    int[] indices = viewer.getTable().getSelectionIndices();
    if (indices.length == 0) return -1;
    Arrays.sort(indices);
    return indices[indices.length - 1];
}
```

- [ ] **Step 2: Update onAddStep() to use lastSelectedIndex()**

Replace the line `currentWorkflow.getSteps().add(step);` in `onAddStep()` with:

```java
int lastIdx = lastSelectedIndex();
if (lastIdx < 0) {
    currentWorkflow.getSteps().add(step);
} else {
    currentWorkflow.getSteps().add(lastIdx + 1, step);
}
```

- [ ] **Step 3: Refactor onDuplicate() to use lastSelectedIndex() for insert point**

`onDuplicate()` currently has `int insertAt = indices[indices.length - 1] + 1;`. Replace it with `int insertAt = lastSelectedIndex() + 1;` (the indices are already sorted by the time this runs, so the result is the same).

- [ ] **Step 4: Verify manually**

In Eclipse: open a workflow with 3 steps. Select step 2 (index 1). Click "Add Step". The new step should appear at index 2. With no selection, the new step should appear at the end.

- [ ] **Step 5: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java
git commit -m "feat: insert new step after current selection"
```

---

## Task 7: Add retry property descriptors to StepPropertySource

**Spec:** Two new rows in the Properties view for every step: "Retry on error" (combo: No/Yes) and "Retry wait (seconds)" (text, integer).

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/StepPropertySourceTest.java`

Read `StepPropertySourceTest.java` to understand the test pattern before writing tests.

- [ ] **Step 1: Add property ID constants**

At the top of `StepPropertySource`, add:

```java
private static final String PROP_RETRY_ON_ERROR      = "step.retryOnError";
private static final String PROP_RETRY_WAIT_SECONDS  = "step.retryWaitSeconds";
```

- [ ] **Step 2: Add descriptors in getPropertyDescriptors()**

After the `boldDesc` block (before the config keys loop), add:

```java
ComboBoxPropertyDescriptor retryDesc =
    new ComboBoxPropertyDescriptor(PROP_RETRY_ON_ERROR, "Retry on error", new String[]{"No", "Yes"});
retryDesc.setCategory("Step");
list.add(retryDesc);

TextPropertyDescriptor retryWaitDesc =
    new TextPropertyDescriptor(PROP_RETRY_WAIT_SECONDS, "Retry wait (seconds)");
retryWaitDesc.setCategory("Step");
list.add(retryWaitDesc);
```

- [ ] **Step 3: Handle getPropertyValue() for retry fields**

In `getPropertyValue()`, add cases before the final `return` line:

```java
if (PROP_RETRY_ON_ERROR.equals(id))     return step.isRetryOnError() ? 1 : 0;
if (PROP_RETRY_WAIT_SECONDS.equals(id)) return String.valueOf(step.getRetryWaitSeconds());
```

- [ ] **Step 4: Handle setPropertyValue() for retry fields**

In `setPropertyValue()`, add cases after the `PROP_BOLD` block:

```java
if (PROP_RETRY_ON_ERROR.equals(id)) {
    step.setRetryOnError(value instanceof Integer i && i == 1);
    save.run();
    return;
}
if (PROP_RETRY_WAIT_SECONDS.equals(id)) {
    try { step.setRetryWaitSeconds(Integer.parseInt((String) value)); }
    catch (NumberFormatException ignored) {}
    save.run();
    return;
}
```

- [ ] **Step 5: Handle resetPropertyValue() for retry fields**

In `resetPropertyValue()`, add cases after the `PROP_BOLD` block:

```java
if (PROP_RETRY_ON_ERROR.equals(id)) {
    step.setRetryOnError(false);
    save.run();
    return;
}
if (PROP_RETRY_WAIT_SECONDS.equals(id)) {
    step.setRetryWaitSeconds(10);
    save.run();
    return;
}
```

- [ ] **Step 6: Handle isPropertySet() for retry fields**

In `isPropertySet()`, add cases:

```java
if (PROP_RETRY_ON_ERROR.equals(id))     return step.isRetryOnError();
if (PROP_RETRY_WAIT_SECONDS.equals(id)) return step.getRetryWaitSeconds() != 10;
```

- [ ] **Step 7: Verify manually**

Open a workflow step's Properties view. Confirm "Retry on error" (No/Yes combo) and "Retry wait (seconds)" appear in the Step category. Set "Retry on error" to Yes and verify the step's `retryOnError` field is saved (check JSON file).

- [ ] **Step 8: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java
git commit -m "feat: add retry on error property descriptors to Properties view"
```

---

## Task 8: Replace Duplicate with Copy + Paste in AutomationView

**Spec:** Remove "Duplicate Step" toolbar button. Add "Copy Step(s)" and "Paste Step(s)" buttons. Copy serializes selected steps to the system clipboard as JSON. Paste reads JSON from clipboard and inserts steps after the current selection. Ctrl+C / Ctrl+V wired to the table. Copy enabled only when selection is non-empty; Paste enabled only when clipboard contains valid step JSON.

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java`
- Delete: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/DuplicateStepTest.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/CopyPasteStepTest.java`

- [ ] **Step 1: Add Gson and Clipboard imports/fields to AutomationView**

Add imports:
```java
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
```

Add fields:
```java
private ToolItem copyStepItem, pasteStepItem;
private final Gson gson = new GsonBuilder().create();
private final IPartListener2 partListener = new IPartListener2() {
    @Override
    public void partActivated(IWorkbenchPartReference ref) {
        if (ref.getPart(false) == AutomationView.this) updateButtonStates();
    }
};
```

- [ ] **Step 2: Replace duplicateStepItem with copyStepItem + pasteStepItem in createToolBar()**

Remove:
```java
duplicateStepItem = makeButton(bar, "Duplicate Step",
    shared.getImage(ISharedImages.IMG_TOOL_COPY),
    SelectionListener.widgetSelectedAdapter(e -> onDuplicate()));
```

Replace with:
```java
copyStepItem = makeButton(bar, "Copy Step(s)",
    shared.getImage(ISharedImages.IMG_TOOL_COPY),
    SelectionListener.widgetSelectedAdapter(e -> onCopy()));
pasteStepItem = makeButton(bar, "Paste Step(s)",
    shared.getImage(ISharedImages.IMG_TOOL_PASTE),
    SelectionListener.widgetSelectedAdapter(e -> onPaste()));
```

- [ ] **Step 3: Remove the duplicateStepItem field declaration**

Remove: `private ToolItem addStepItem, deleteStepItem, moveUpItem, moveDownItem, duplicateStepItem;`

Replace with: `private ToolItem addStepItem, deleteStepItem, moveUpItem, moveDownItem;`

- [ ] **Step 4: Add onCopy() method**

```java
@SuppressWarnings("unchecked")
private void onCopy() {
    if (currentWorkflow == null) return;
    List<Step> allSteps = currentWorkflow.getSteps();
    int[] indices = viewer.getTable().getSelectionIndices();
    if (indices.length == 0) return;
    Arrays.sort(indices);
    List<Step> selected = new ArrayList<>();
    for (int idx : indices) selected.add(allSteps.get(idx));
    String json = gson.toJson(selected);
    Clipboard cb = new Clipboard(viewer.getControl().getDisplay());
    try {
        cb.setContents(new Object[]{json}, new Transfer[]{TextTransfer.getInstance()});
    } finally {
        cb.dispose();
    }
    updateButtonStates();
}
```

- [ ] **Step 5: Add onPaste() method**

```java
private void onPaste() {
    if (currentWorkflow == null) return;
    List<Step> toPaste = clipboardSteps();
    if (toPaste == null) return;
    int lastIdx = lastSelectedIndex();
    int insertAt = lastIdx < 0 ? currentWorkflow.getSteps().size() : lastIdx + 1;
    currentWorkflow.getSteps().addAll(insertAt, toPaste);
    save();
    viewer.refresh();
    int[] newSel = new int[toPaste.size()];
    for (int i = 0; i < toPaste.size(); i++) newSel[i] = insertAt + i;
    viewer.getTable().setSelection(newSel);
    updateButtonStates();
}
```

- [ ] **Step 6: Add clipboardSteps() helper**

```java
private List<Step> clipboardSteps() {
    Clipboard cb = new Clipboard(viewer.getControl().getDisplay());
    try {
        String text = (String) cb.getContents(TextTransfer.getInstance());
        if (text == null) return null;
        try {
            java.lang.reflect.Type type = new TypeToken<List<Step>>(){}.getType();
            List<Step> steps = gson.fromJson(text, type);
            return (steps != null && !steps.isEmpty()) ? steps : null;
        } catch (Exception e) {
            return null;
        }
    } finally {
        cb.dispose();
    }
}
```

- [ ] **Step 7: Update updateButtonStates()**

Remove: `duplicateStepItem.setEnabled(!running && contiguous && selIndices.length > 0);`

Add:
```java
copyStepItem.setEnabled(!running && hasStep);
pasteStepItem.setEnabled(!running && hasWorkflow && clipboardSteps() != null);
```

Note: `clipboardSteps()` creates/disposes a Clipboard — this is fast and safe to call from the UI thread.

- [ ] **Step 8: Add Ctrl+C / Ctrl+V key listener in createTable()**

At the end of `createTable()`, after the existing `viewer.addDoubleClickListener(...)` call:

```java
viewer.getTable().addKeyListener(new KeyAdapter() {
    @Override
    public void keyPressed(KeyEvent e) {
        if ((e.stateMask & SWT.CTRL) != 0) {
            if (e.keyCode == 'c') onCopy();
            else if (e.keyCode == 'v') onPaste();
        }
    }
});
```

- [ ] **Step 9: Register and unregister IPartListener2**

In `createPartControl()`, at the end, add:
```java
getSite().getPage().addPartListener(partListener);
```

In `dispose()`, before `super.dispose()`, add:
```java
getSite().getPage().removePartListener(partListener);
```

- [ ] **Step 10: Delete onDuplicate() from AutomationView**

The `onDuplicate()` method (lines ~395-411 in `AutomationView.java`) is now dead code. Remove it entirely.

- [ ] **Step 11: Delete DuplicateStepTest.java**

```bash
git rm "com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/DuplicateStepTest.java"
```

- [ ] **Step 12: Create CopyPasteStepTest.java**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.ui.PlatformUI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.model.Step;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class CopyPasteStepTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF_ID = "copy-paste-test";
    private static final Gson GSON = new GsonBuilder().create();

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        Workflow wf = new Workflow(WF_ID, "Copy Paste Test", "");
        wf.getSteps().add(new Step("refresh-all"));
        wf.getSteps().add(new Step("refresh-project"));
        repo.save(wf);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
        loadWorkflow("Copy Paste Test");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF_ID);
    }

    private static void loadWorkflow(String displayName) {
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

    private static void setClipboard(String text) {
        PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
            Clipboard cb = new Clipboard(PlatformUI.getWorkbench().getDisplay());
            cb.setContents(new Object[]{text}, new Transfer[]{TextTransfer.getInstance()});
            cb.dispose();
        });
    }

    @Test
    public void copyButton_enabledWhenStepSelected() {
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        table.click(0, 1);
        assertTrue(bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Copy Step(s)").isEnabled());
    }

    @Test
    public void pasteButton_disabledWhenClipboardEmpty() {
        setClipboard("not json");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        table.click(0, 1);
        assertFalse(bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Paste Step(s)").isEnabled());
    }

    @Test
    public void copyThenPaste_insertsStepAfterSelection() {
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        int originalCount = table.rowCount();
        String originalName = table.cell(0, 1);

        // Copy row 0
        table.click(0, 1);
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Copy Step(s)").click();

        // Paste (row 0 still selected) → should insert at index 1
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Paste Step(s)").click();

        assertEquals("Row count must increase by 1", originalCount + 1, table.rowCount());
        assertEquals("Row 0 unchanged",  originalName, table.cell(0, 1));
        assertEquals("Row 1 is the copy", originalName, table.cell(1, 1));
    }
}
```

- [ ] **Step 13: Run CopyPasteStepTest and verify all tests pass**

- [ ] **Step 14: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/CopyPasteStepTest.java
git commit -m "feat: replace Duplicate with Copy+Paste using system clipboard"
```

---

## Task 9: Persist last opened workflow and restore on startup

**Spec:** When the user opens a workflow via "Open Workflow", save its ID to plugin preferences. On `createPartControl()`, if the saved ID matches a loaded workflow, select it instead of the first one.

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/preferences/AutomationPreferences.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java`

- [ ] **Step 1: Add KEY_LAST_WORKFLOW_ID to AutomationPreferences**

Add to `AutomationPreferences.java`:

```java
public static final String KEY_LAST_WORKFLOW_ID = "lastWorkflowId";
```

- [ ] **Step 2: Save last workflow ID in onOpenWorkflow()**

In `AutomationView.onOpenWorkflow()`, after `updateButtonStates()` (at the end of the `if (dialog.open() == Window.OK)` block), add:

```java
AutomationPreferences.store().setValue(
    AutomationPreferences.KEY_LAST_WORKFLOW_ID,
    currentWorkflow.getWorkflowId());
```

- [ ] **Step 3: Restore last workflow in loadWorkflows()**

In `loadWorkflows()`, after `currentWorkflow = workflows.get(0);`, add:

```java
String lastId = AutomationPreferences.store().getString(AutomationPreferences.KEY_LAST_WORKFLOW_ID);
if (!lastId.isBlank()) {
    workflows.stream()
        .filter(w -> lastId.equals(w.getWorkflowId()))
        .findFirst()
        .ifPresent(w -> currentWorkflow = w);
}
```

- [ ] **Step 4: Verify manually**

In Eclipse: open a workflow (not the first in the list). Restart Eclipse. The Automation view should automatically load the last opened workflow.

- [ ] **Step 5: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/preferences/AutomationPreferences.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java
git commit -m "feat: persist and restore last opened workflow across restarts"
```

---

## Task 10: SetXmlTagTextAction

**Spec:** Action that parses an XML file, navigates a slash-separated tag path, replaces the text content of all matching leaf nodes, and writes the file back. Fails if any tag in the path is not found. Comments preserved. `filePath` and `value` resolved via `EclipseVariables`; `tagPath` used verbatim.

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetXmlTagTextAction.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetXmlTagTextActionTest.java`
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml`

- [ ] **Step 1: Write SetXmlTagTextActionTest**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.SetXmlTagTextAction;

public class SetXmlTagTextActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File writeXml(String content) throws Exception {
        File f = tmp.newFile("test.xml");
        Files.writeString(f.toPath(), content);
        return f;
    }

    @Test
    public void replaceTagText_updatesContent() throws Exception {
        File f = writeXml("<root><child>old</child></root>");
        new SetXmlTagTextAction().updateXml(f.getAbsolutePath(), "/root/child", "new");
        String result = Files.readString(f.toPath());
        assertTrue("Must contain new value", result.contains("<child>new</child>") || result.contains(">new<"));
        assertFalse("Must not contain old value", result.contains("old"));
    }

    @Test
    public void preservesXmlComment() throws Exception {
        File f = writeXml("<root><!-- keep this --><child>old</child></root>");
        new SetXmlTagTextAction().updateXml(f.getAbsolutePath(), "/root/child", "new");
        String result = Files.readString(f.toPath());
        assertTrue("XML comment must be preserved", result.contains("<!-- keep this -->"));
    }

    @Test(expected = Exception.class)
    public void missingTag_throws() throws Exception {
        File f = writeXml("<root><child>old</child></root>");
        new SetXmlTagTextAction().updateXml(f.getAbsolutePath(), "/root/missing", "new");
    }

    @Test
    public void replacesAllMatchingLeafNodes() throws Exception {
        File f = writeXml("<root><item>a</item><item>b</item></root>");
        new SetXmlTagTextAction().updateXml(f.getAbsolutePath(), "/root/item", "x");
        String result = Files.readString(f.toPath());
        assertFalse("Old value 'a' must be gone", result.contains(">a<"));
        assertFalse("Old value 'b' must be gone", result.contains(">b<"));
    }

    @Test
    public void validate_requiresFilePath() {
        List<String> errors = new SetXmlTagTextAction().validate(
            Map.of("filePath", "", "tagPath", "/root/tag", "value", "v"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_requiresTagPath() {
        List<String> errors = new SetXmlTagTextAction().validate(
            Map.of("filePath", "f.xml", "tagPath", "", "value", "v"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void getId_returnsSetXmlTagText() {
        assertEquals("set-xml-tag-text", new SetXmlTagTextAction().getId());
    }
}
```

- [ ] **Step 2: Run the test and verify it fails** (class does not exist yet)

- [ ] **Step 3: Create SetXmlTagTextAction.java**

```java
package com.example.automation.actions;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.example.automation.EclipseVariables;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class SetXmlTagTextAction implements IAction {

    @Override public String getId()          { return "set-xml-tag-text"; }
    @Override public String getName()        { return "Set XML Tag Text"; }
    @Override public String getDescription() { return "Sets the text content of a tag in an XML file. The tag path uses slash-separated tag names (e.g. /root/settings/value). Fails if any tag is not found."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("filePath", "", "tagPath", "", "value", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("filePath", "").isBlank())
            errors.add("filePath must not be blank");
        if (config.getOrDefault("tagPath", "").isBlank())
            errors.add("tagPath must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String filePath = EclipseVariables.resolve(config.getOrDefault("filePath", ""));
        String tagPath  = config.getOrDefault("tagPath", "");
        String value    = EclipseVariables.resolve(config.getOrDefault("value", ""));
        context.setProgress(0);
        updateXml(filePath, tagPath, value);
        context.getStdout().println("Updated tag " + tagPath + " in " + filePath);
        context.setProgress(100);
    }

    /**
     * Parses {@code filePath} as XML, walks {@code tagPath} (slash-separated names),
     * replaces text content of all matching leaf nodes with {@code value}, writes back.
     * Public for testing.
     */
    public void updateXml(String filePath, String tagPath, String value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(filePath));

        String[] parts = tagPath.split("/");
        // Walk to the parent of the final tag
        org.w3c.dom.Node current = doc.getDocumentElement();
        String rootTagName = current.getNodeName();
        int startIndex = 0;
        // Skip the root element if tagPath starts with it (e.g. /root/child or root/child)
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) { startIndex = i; break; }
        }
        // If the first non-empty part matches the document root, skip it
        if (parts[startIndex].equals(rootTagName)) startIndex++;

        // Walk intermediate tags
        for (int i = startIndex; i < parts.length - 1; i++) {
            String tag = parts[i];
            if (tag.isEmpty()) continue;
            NodeList nl = ((org.w3c.dom.Element) current).getElementsByTagName(tag);
            if (nl.getLength() == 0)
                throw new Exception("Tag '" + tag + "' not found in " + filePath);
            current = nl.item(0);
        }

        // Replace all leaf nodes at the final path component
        String leafTag = parts[parts.length - 1];
        NodeList leaves = ((org.w3c.dom.Element) current).getElementsByTagName(leafTag);
        if (leaves.getLength() == 0)
            throw new Exception("Tag '" + leafTag + "' not found in " + filePath);
        for (int i = 0; i < leaves.getLength(); i++) {
            org.w3c.dom.Node node = leaves.item(i);
            // Remove all child text nodes, then add new text
            while (node.hasChildNodes()) node.removeChild(node.getFirstChild());
            node.appendChild(doc.createTextNode(value));
        }

        // Serialize back without re-indenting (preserves whitespace and comments)
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        Files.writeString(new File(filePath).toPath(), sw.toString(), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 4: Run SetXmlTagTextActionTest and verify all tests pass**

- [ ] **Step 5: Register in plugin.xml**

Add before the closing `</plugin>` tag:

```xml
  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.SetXmlTagTextAction"/>
  </extension>
```

- [ ] **Step 6: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetXmlTagTextAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetXmlTagTextActionTest.java
git add com.example.automation.parent/com.example.automation/plugin.xml
git commit -m "feat: add SetXmlTagText action"
```

---

## Task 11: SetMavenPreferencesAction

**Spec:** Action with three independent combo-selectable preferences (downloadSources, downloadJavadoc, updateIndexes). Empty string = "Do not change"; "true"/"false" are applied to the M2E preference node.

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetMavenPreferencesAction.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetMavenPreferencesActionTest.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml`

- [ ] **Step 1: Write SetMavenPreferencesActionTest**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.Before;
import org.junit.Test;

import com.example.automation.actions.SetMavenPreferencesAction;

public class SetMavenPreferencesActionTest {

    private static final String M2E_NODE = "org.eclipse.m2e.core";

    @Before
    public void resetPrefs() throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(M2E_NODE);
        prefs.remove("eclipse.m2.downloadSources");
        prefs.remove("eclipse.m2.downloadJavadoc");
        prefs.remove("eclipse.m2.updateIndexes");
        prefs.flush();
    }

    @Test
    public void setDownloadSources_true_writesPreference() throws Exception {
        new SetMavenPreferencesAction().execute(
            Map.of("downloadSources", "true", "downloadJavadoc", "", "updateIndexes", ""),
            nullCtx());
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(M2E_NODE);
        assertTrue(prefs.getBoolean("eclipse.m2.downloadSources", false));
    }

    @Test
    public void setDownloadSources_false_writesPreference() throws Exception {
        new SetMavenPreferencesAction().execute(
            Map.of("downloadSources", "false", "downloadJavadoc", "", "updateIndexes", ""),
            nullCtx());
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(M2E_NODE);
        assertFalse(prefs.getBoolean("eclipse.m2.downloadSources", true));
    }

    @Test
    public void emptyValue_doesNotChangePreference() throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(M2E_NODE);
        prefs.putBoolean("eclipse.m2.downloadSources", true);
        prefs.flush();

        new SetMavenPreferencesAction().execute(
            Map.of("downloadSources", "", "downloadJavadoc", "", "updateIndexes", ""),
            nullCtx());
        assertTrue("preference must be unchanged", prefs.getBoolean("eclipse.m2.downloadSources", false));
    }

    @Test
    public void validate_acceptsAllEmptyConfig() {
        List<String> errors = new SetMavenPreferencesAction().validate(
            Map.of("downloadSources", "", "downloadJavadoc", "", "updateIndexes", ""));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void getId_returnsSetMavenPreferences() {
        assertEquals("set-maven-preferences", new SetMavenPreferencesAction().getId());
    }

    private static com.example.automation.api.IActionContext nullCtx() {
        return new com.example.automation.api.IActionContext() {
            @Override public java.io.OutputStream getOutputStream() { return java.io.OutputStream.nullOutputStream(); }
            @Override public java.io.OutputStream getErrorStream()  { return java.io.OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p) {}
            @Override public boolean isCancelled() { return false; }
        };
    }
}
```

- [ ] **Step 2: Run the test — verify it fails**

- [ ] **Step 3: Create SetMavenPreferencesAction.java**

```java
package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class SetMavenPreferencesAction implements IAction {

    private static final String M2E_NODE = "org.eclipse.m2e.core";

    @Override public String getId()          { return "set-maven-preferences"; }
    @Override public String getName()        { return "Set Maven Preferences"; }
    @Override public String getDescription() { return "Configures Maven preferences: Download Artifact Sources, Download Artifact Javadoc, and repository index updates. Leave a field empty to keep its current value."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("downloadSources", "", "downloadJavadoc", "", "updateIndexes", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        return new ArrayList<>();
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(M2E_NODE);
        apply(prefs, "eclipse.m2.downloadSources",  config.getOrDefault("downloadSources", ""));
        apply(prefs, "eclipse.m2.downloadJavadoc",  config.getOrDefault("downloadJavadoc", ""));
        apply(prefs, "eclipse.m2.updateIndexes",    config.getOrDefault("updateIndexes", ""));
        prefs.flush();
        context.getStdout().println("Maven preferences updated.");
        context.setProgress(100);
    }

    private static void apply(IEclipsePreferences prefs, String key, String value) {
        if ("true".equals(value)) prefs.putBoolean(key, true);
        else if ("false".equals(value)) prefs.putBoolean(key, false);
        // empty string → do not change
    }
}
```

- [ ] **Step 4: Run SetMavenPreferencesActionTest — verify all pass**

- [ ] **Step 5: Add combo property descriptors in StepPropertySource**

In `StepPropertySource.createConfigDescriptor(String key)`, insert this block immediately before the `if (StepOperations.isDirField(key))` check:

```java
if ("set-maven-preferences".equals(step.getActionId())
        && (key.equals("downloadSources") || key.equals("downloadJavadoc") || key.equals("updateIndexes"))) {
    return new ComboBoxPropertyDescriptor(key, key,
        new String[]{"Do not change", "Yes", "No"});
}
if ("set-build-automatically".equals(step.getActionId()) && "enabled".equals(key)) {
    return new ComboBoxPropertyDescriptor(key, key, new String[]{"Yes", "No"});
}
```

Also update `getPropertyValue()` — after the existing `PROP_BOLD` check, before the final generic return:

```java
if ("set-maven-preferences".equals(step.getActionId())
        && id instanceof String key2
        && isMavenPrefKey(key2)) {
    String v = step.getConfig().getOrDefault(key2, "");
    if ("true".equals(v))  return 1; // Yes
    if ("false".equals(v)) return 2; // No
    return 0; // Do not change
}
if ("set-build-automatically".equals(step.getActionId()) && "enabled".equals(id)) {
    return "false".equals(step.getConfig().getOrDefault("enabled", "true")) ? 1 : 0;
}
```

And `setPropertyValue()` — add after the `PROP_RETRY_WAIT_SECONDS` block:

```java
if ("set-maven-preferences".equals(step.getActionId())
        && id instanceof String key2
        && isMavenPrefKey(key2)) {
    String val = switch (value instanceof Integer i ? i : 0) {
        case 1 -> "true";
        case 2 -> "false";
        default -> "";
    };
    step.getConfig().put(key2, val);
    save.run();
    return;
}
if ("set-build-automatically".equals(step.getActionId()) && "enabled".equals(id)) {
    step.getConfig().put("enabled", value instanceof Integer i && i == 0 ? "true" : "false");
    save.run();
    return;
}
```

Add the helper at the bottom of `StepPropertySource`:

```java
private static boolean isMavenPrefKey(String key) {
    return "downloadSources".equals(key) || "downloadJavadoc".equals(key) || "updateIndexes".equals(key);
}
```

- [ ] **Step 6: Register SetMavenPreferencesAction in plugin.xml**

```xml
  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.SetMavenPreferencesAction"/>
  </extension>
```

- [ ] **Step 7: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetMavenPreferencesAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetMavenPreferencesActionTest.java
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java
git add com.example.automation.parent/com.example.automation/plugin.xml
git commit -m "feat: add SetMavenPreferences action with Do not change / Yes / No combo"
```

---

## Task 12: SetBuildAutomaticallyAction

**Spec:** Action with a single "enabled" field (combo: Yes/No) that calls `IWorkspace.setDescription()` to enable or disable Eclipse's "Build Automatically" setting.

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetBuildAutomaticallyAction.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetBuildAutomaticallyActionTest.java`
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml`

Note: `StepPropertySource` combo support for `set-build-automatically` / `enabled` was already added in Task 11 Step 5.

- [ ] **Step 1: Write SetBuildAutomaticallyActionTest**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.Test;

import com.example.automation.actions.SetBuildAutomaticallyAction;

public class SetBuildAutomaticallyActionTest {

    private static com.example.automation.api.IActionContext nullCtx() {
        return new com.example.automation.api.IActionContext() {
            @Override public java.io.OutputStream getOutputStream() { return java.io.OutputStream.nullOutputStream(); }
            @Override public java.io.OutputStream getErrorStream()  { return java.io.OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p) {}
            @Override public boolean isCancelled() { return false; }
        };
    }

    @Test
    public void enableTrue_setsAutoBuilding() throws Exception {
        new SetBuildAutomaticallyAction().execute(Map.of("enabled", "true"), nullCtx());
        assertTrue(ResourcesPlugin.getWorkspace().getDescription().isAutoBuilding());
    }

    @Test
    public void enableFalse_clearsAutoBuilding() throws Exception {
        new SetBuildAutomaticallyAction().execute(Map.of("enabled", "false"), nullCtx());
        assertFalse(ResourcesPlugin.getWorkspace().getDescription().isAutoBuilding());
    }

    @Test
    public void validate_acceptsValidConfig() {
        List<String> errors = new SetBuildAutomaticallyAction().validate(Map.of("enabled", "true"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void getId_returnsSetBuildAutomatically() {
        assertEquals("set-build-automatically", new SetBuildAutomaticallyAction().getId());
    }
}
```

- [ ] **Step 2: Run the test — verify it fails**

- [ ] **Step 3: Create SetBuildAutomaticallyAction.java**

```java
package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class SetBuildAutomaticallyAction implements IAction {

    @Override public String getId()          { return "set-build-automatically"; }
    @Override public String getName()        { return "Set Build Automatically"; }
    @Override public String getDescription() { return "Enables or disables Project > Build Automatically in Eclipse."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("enabled", "true");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        return new ArrayList<>();
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        boolean enable = !"false".equals(config.getOrDefault("enabled", "true"));
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        IWorkspaceDescription desc = ws.getDescription();
        desc.setAutoBuilding(enable);
        ws.setDescription(desc);
        context.getStdout().println("Build automatically: " + (enable ? "enabled" : "disabled"));
        context.setProgress(100);
    }
}
```

- [ ] **Step 4: Run SetBuildAutomaticallyActionTest — verify all pass**

- [ ] **Step 5: Register in plugin.xml**

```xml
  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.SetBuildAutomaticallyAction"/>
  </extension>
```

- [ ] **Step 6: Commit**

```bash
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetBuildAutomaticallyAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetBuildAutomaticallyActionTest.java
git add com.example.automation.parent/com.example.automation/plugin.xml
git commit -m "feat: add SetBuildAutomatically action"
```

---

## Task 13: Version bump

**Spec:** Bump plugin version from 1.10.0 to 1.11.0 across all version files.

- [ ] **Step 1: Find all version files**

```bash
grep -r "1\.10\.0" --include="*.xml" --include="*.MF" -l .
```

- [ ] **Step 2: Update each file**

Replace `1.10.0` with `1.11.0` (and `1.10.0.qualifier` with `1.11.0.qualifier` in MANIFEST.MF files) in all files found in step 1.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: bump version to 1.11.0"
```
