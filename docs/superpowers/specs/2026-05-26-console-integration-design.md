# Console Integration — Design Spec

**Date:** 2026-05-26
**Status:** Approved
**Sub-project:** 5 of 8 (Workflow Automation)

---

## Overview

Wire `IActionContext.getOutputStream()` and `getErrorStream()` to a live Eclipse Console so that action stdout and stderr are visible during a workflow run. One shared "Automation" console is cleared and activated at the start of each run. Stderr appears in red; stdout uses the default console colour.

---

## Architecture

Four files change:

| File | Change |
|---|---|
| `com.example.automation/META-INF/MANIFEST.MF` | Add `org.eclipse.ui.console` to `Require-Bundle` |
| `com.example.automation/…/WorkflowRunner.java` | Add `OutputStream stdout, stderr` constructor params; `ActionContextImpl` returns them; `finally` closes both before `onDone` |
| `com.example.automation/…/AutomationView.java` | Add private `openConsole()` helper; `startRunner()` calls it and passes the two streams |
| `com.example.automation.tests/…/WorkflowRunnerTest.java` | Update constructor calls to pass `OutputStream.nullOutputStream()` twice |

`IActionContext`, `StepLabelProvider`, the model, and the persistence layer are untouched. The `org.eclipse.ui.console` dependency stays confined to `AutomationView` — `WorkflowRunner` imports nothing Eclipse-specific and remains unit-testable without an Eclipse runtime.

---

## WorkflowRunner Changes

### Constructor

```java
public WorkflowRunner(
    List<Step> steps,
    ActionRegistry registry,
    Consumer<Runnable> uiRunner,
    Runnable refresh,
    Runnable onDone,
    OutputStream stdout,
    OutputStream stderr)
```

`stdout` and `stderr` are stored as `private final` fields.

### ActionContextImpl

```java
@Override public OutputStream getOutputStream() { return stdout; }
@Override public OutputStream getErrorStream()  { return stderr; }
```

### execute() finally block

Streams are closed before `onDone` fires, so the console page marks the stream closed before the run finishes in the UI:

```java
} finally {
    closeQuietly(stdout);
    closeQuietly(stderr);
    uiRunner.accept(onDone);
}

private static void closeQuietly(OutputStream s) {
    try { s.close(); } catch (IOException ignored) {}
}
```

---

## AutomationView Changes

### openConsole()

Finds or creates the "Automation" `MessageConsole`, clears it, and brings the Console view to front. Runs on the UI thread (called from `startRunner()`, which is always invoked from an SWT selection listener).

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

### startRunner()

```java
private void startRunner(List<Step> steps) {
    if (activeRunner != null) return;
    MessageConsole console = openConsole();
    MessageConsoleStream stdout = console.newMessageStream();
    MessageConsoleStream stderr = console.newMessageStream();
    stderr.setColor(viewer.getControl().getDisplay().getSystemColor(SWT.COLOR_RED));
    // existing safeRefresh / onDone lambdas unchanged
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

`startRunner()` does not close the streams — `WorkflowRunner`'s `finally` block owns their lifecycle.

### New imports

```java
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.swt.SWT;
```

(`SWT` is already imported for table style constants.)

---

## Console Lifecycle

```
Run clicked
  └─ AutomationView.startRunner()
       └─ openConsole() → finds/creates "Automation" console, clears, activates
       └─ newMessageStream() × 2 → stdout (default), stderr (red)
       └─ WorkflowRunner created with streams

WorkflowRunner (background thread)
  └─ per step: action.execute(config, ctx)
       └─ ctx.getOutputStream() → stdout stream
       └─ ctx.getErrorStream()  → stderr stream
  └─ finally:
       └─ closeQuietly(stdout)
       └─ closeQuietly(stderr)
       └─ uiRunner.accept(onDone)

Next run clicked
  └─ openConsole() clears the same console and reactivates it
```

---

## Test Coverage

### WorkflowRunnerTest (existing — 2 constructor call sites updated)

Both the `run()` helper and `cancelBeforeStart_noStepsRun` pass `OutputStream.nullOutputStream()` for stdout and stderr. The 5 existing tests cover all execution paths and remain valid with no behavior change.

### AutomationViewTest (no new test)

Verifying that a specific Eclipse Console page appears via SWTBot is fragile and adds little value. The wiring is simple and confirmed by the existing 28-test suite passing after the change.

---

## What Is NOT in Scope

- Properties View on row selection — sub-project 6
- New Workflow dialog — sub-project 7
- Built-in action implementations — sub-project 8
- Progress bar rendered in the Status column
- Per-step console pages (one shared console per run)
- Console output when no run is active
