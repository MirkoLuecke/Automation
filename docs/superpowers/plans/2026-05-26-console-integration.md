# Console Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `IActionContext.getOutputStream()` and `getErrorStream()` to a live Eclipse Console so action stdout/stderr is visible during a workflow run.

**Architecture:** `WorkflowRunner` gains two `OutputStream` constructor params; `ActionContextImpl` returns them directly; the `finally` block closes both. `AutomationView.startRunner()` calls a new `openConsole()` helper that finds-or-creates a single "Automation" `MessageConsole`, clears it, activates it, and hands back two `MessageConsoleStream`s (stderr in red). Eclipse dependency stays confined to `AutomationView`.

**Tech Stack:** Java 17, Eclipse SWT/JFace, OSGi/Tycho, `org.eclipse.ui.console`, JUnit 4.

---

## File Structure

| File | Role |
|---|---|
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRunnerTest.java` | **Modified.** Two constructor call sites updated to pass `OutputStream.nullOutputStream()` twice. |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java` | **Modified.** Add `stdout`/`stderr` fields + params; update `ActionContextImpl`; add `closeQuietly`; update `finally` block. |
| `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF` | **Modified.** Add `org.eclipse.ui.console` to `Require-Bundle`. |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java` | **Modified.** Add console imports; add `openConsole()` method; update `startRunner()` to call it and pass streams. |

---

### Task 1: Update WorkflowRunnerTest to call the new 7-arg constructor

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRunnerTest.java`

- [ ] **Step 1: Replace the entire file**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.example.automation.WorkflowRunner;
import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;

public class WorkflowRunnerTest {

    @FunctionalInterface
    interface ThrowingConsumer {
        void accept(IActionContext ctx) throws Exception;
    }

    private static IAction stub(String id, ThrowingConsumer body) {
        return new IAction() {
            @Override public String getId() { return id; }
            @Override public String getName() { return id; }
            @Override public String getDescription() { return ""; }
            @Override public Map<String, String> getDefaultConfig() { return Map.of(); }
            @Override public List<String> validate(Map<String, String> config) { return List.of(); }
            @Override public void execute(Map<String, String> config, IActionContext ctx) throws Exception {
                body.accept(ctx);
            }
        };
    }

    private void run(List<Step> steps, ActionRegistry registry) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        new WorkflowRunner(steps, registry, r -> r.run(), () -> {}, done::countDown,
            OutputStream.nullOutputStream(), OutputStream.nullOutputStream()).start();
        assertTrue("Runner did not finish in 5 s", done.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void successfulStep_statusGreen() throws Exception {
        Step step = new Step("a");
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {}))));
        assertEquals(StepStatus.GREEN, step.getStatus());
    }

    @Test
    public void unknownActionId_statusRed() throws Exception {
        Step step1 = new Step("missing");
        Step step2 = new Step("ok");
        run(List.of(step1, step2), new ActionRegistry(List.of()));
        assertEquals(StepStatus.RED,   step1.getStatus());
        assertEquals(StepStatus.WHITE, step2.getStatus());
    }

    @Test
    public void throwingAction_statusRed_nextStepSkipped() throws Exception {
        Step step1 = new Step("fail");
        Step step2 = new Step("ok");
        ActionRegistry reg = new ActionRegistry(List.of(
            stub("fail", ctx -> { throw new RuntimeException("boom"); }),
            stub("ok",   ctx -> {})
        ));
        run(List.of(step1, step2), reg);
        assertEquals(StepStatus.RED,   step1.getStatus());
        assertEquals(StepStatus.WHITE, step2.getStatus());
    }

    @Test
    public void cancelBeforeStart_noStepsRun() throws Exception {
        Step step = new Step("a");
        CountDownLatch done = new CountDownLatch(1);
        WorkflowRunner runner = new WorkflowRunner(
            List.of(step),
            new ActionRegistry(List.of(stub("a", ctx -> {}))),
            r -> r.run(), () -> {}, done::countDown,
            OutputStream.nullOutputStream(), OutputStream.nullOutputStream());
        runner.cancel();
        runner.start();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(StepStatus.WHITE, step.getStatus());
    }

    @Test
    public void progressUpdatesStep() throws Exception {
        Step step = new Step("a");
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> ctx.setProgress(50)))));
        assertEquals(50, step.getProgress());
        assertEquals(StepStatus.GREEN, step.getStatus());
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
cd com.example.automation.parent
mvn verify -q
```

Expected: `BUILD FAILURE` — `WorkflowRunner` constructor still has 5 params, so `WorkflowRunnerTest` won't compile.

- [ ] **Step 3: Commit the failing tests**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRunnerTest.java
git commit -m "test: update WorkflowRunnerTest for 7-arg constructor (stdout/stderr)"
```

---

### Task 2: Add stdout/stderr to WorkflowRunner, wire console in AutomationView

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java`
- Modify: `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java`

- [ ] **Step 1: Replace WorkflowRunner.java**

```java
package com.example.automation;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;

public class WorkflowRunner {

    private final List<Step> steps;
    private final ActionRegistry registry;
    private final Consumer<Runnable> uiRunner;
    private final Runnable refresh;
    private final Runnable onDone;
    private final OutputStream stdout;
    private final OutputStream stderr;

    private volatile boolean cancelled = false;

    public WorkflowRunner(List<Step> steps, ActionRegistry registry,
                          Consumer<Runnable> uiRunner, Runnable refresh, Runnable onDone,
                          OutputStream stdout, OutputStream stderr) {
        this.steps    = steps;
        this.registry = registry;
        this.uiRunner = uiRunner;
        this.refresh  = refresh;
        this.onDone   = onDone;
        this.stdout   = stdout;
        this.stderr   = stderr;
    }

    public Thread start() {
        Thread t = new Thread(this::execute, "WorkflowRunner");
        t.setDaemon(true);
        t.start();
        return t;
    }

    public void cancel() {
        cancelled = true;
    }

    private void execute() {
        try {
            for (Step step : steps) {
                if (cancelled) break;

                step.setStatus(StepStatus.YELLOW);
                step.setProgress(0);
                uiRunner.accept(refresh);

                IAction action = registry.getAction(step.getActionId());
                if (action == null) {
                    step.setStatus(StepStatus.RED);
                    uiRunner.accept(refresh);
                    break;
                }

                try {
                    action.execute(step.getConfig(), new ActionContextImpl(step));
                    if (!cancelled) {
                        step.setStatus(StepStatus.GREEN);
                        uiRunner.accept(refresh);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    step.setStatus(StepStatus.RED);
                    uiRunner.accept(refresh);
                    break;
                }
            }
        } finally {
            closeQuietly(stdout);
            closeQuietly(stderr);
            uiRunner.accept(onDone);
        }
    }

    private static void closeQuietly(OutputStream s) {
        try { s.close(); } catch (IOException ignored) {}
    }

    private class ActionContextImpl implements IActionContext {

        private final Step step;

        ActionContextImpl(Step step) { this.step = step; }

        @Override
        public void setProgress(int percent) {
            step.setProgress(percent);
            uiRunner.accept(refresh);
        }

        @Override
        public boolean isCancelled() { return cancelled; }

        @Override
        public OutputStream getOutputStream() { return stdout; }

        @Override
        public OutputStream getErrorStream() { return stderr; }
    }
}
```

- [ ] **Step 2: Add `org.eclipse.ui.console` to MANIFEST.MF**

File: `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`

Replace the `Require-Bundle` block (currently ends with `org.eclipse.swt`) with:

```
Require-Bundle: org.eclipse.ui,
 org.eclipse.core.runtime,
 org.eclipse.core.commands,
 org.eclipse.jface,
 org.eclipse.swt,
 org.eclipse.ui.console
```

The full file after the change:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Automation
Bundle-SymbolicName: com.example.automation;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Activator: com.example.automation.Activator
Require-Bundle: org.eclipse.ui,
 org.eclipse.core.runtime,
 org.eclipse.core.commands,
 org.eclipse.jface,
 org.eclipse.swt,
 org.eclipse.ui.console
Bundle-RequiredExecutionEnvironment: JavaSE-17
Bundle-ActivationPolicy: lazy
Bundle-ClassPath: .,
 lib/gson-2.10.1.jar
Export-Package: com.example.automation,
 com.example.automation.api,
 com.example.automation.model,
 com.example.automation.persistence

```

- [ ] **Step 3: Update AutomationView.java — add imports**

After the existing import block (after line `import com.example.automation.persistence.WorkflowRepository;`), add:

```java
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
```

- [ ] **Step 4: Update AutomationView.java — replace startRunner() and add openConsole()**

Replace the existing `startRunner()` method:

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
    activeRunner = new WorkflowRunner(
        steps,
        ActionRegistry.getInstance(),
        viewer.getControl().getDisplay()::asyncExec,
        safeRefresh,
        onDone,
        stdout,
        stderr);
    updateButtonStates();
    activeRunner.start();
}
```

Add the new `openConsole()` method directly after `startRunner()` (before `save()`):

```java
private MessageConsole openConsole() {
    IConsoleManager mgr = ConsolePlugin.getDefault().getConsoleManager();
    for (IConsole c : mgr.getConsoles()) {
        if (c instanceof MessageConsole mc && "Automation".equals(c.getName())) {
            mc.clearConsole();
            mgr.showConsoleView(mc);
            return mc;
        }
    }
    MessageConsole mc = new MessageConsole("Automation", null);
    mgr.addConsoles(new IConsole[] { mc });
    mgr.showConsoleView(mc);
    return mc;
}
```

- [ ] **Step 5: Run all tests**

```
cd com.example.automation.parent
mvn verify -q
```

Expected:
```
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
```

- [ ] **Step 6: Commit**

```
git add com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java
git add com.example.automation/META-INF/MANIFEST.MF
git add com.example.automation/src/main/java/com/example/automation/AutomationView.java
git commit -m "feat: wire action stdout/stderr to Eclipse Console view"
```
