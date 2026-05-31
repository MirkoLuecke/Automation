# Execution Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire Run / Run Selected / Stop buttons to a sequential background execution engine that updates Step statuses live in the table.

**Architecture:** A new `WorkflowRunner` class owns all execution logic — it takes the steps to run, a `Consumer<Runnable>` for UI dispatch, and callbacks; `AutomationView` holds one `WorkflowRunner` field and wires the three buttons. The runner is unit-testable without an Eclipse runtime by injecting `r -> r.run()` as the UI dispatcher.

**Tech Stack:** Java 17, Eclipse SWT/JFace, OSGi/Tycho, JUnit 4, SWTBot.

---

## File Structure

| File | Role |
|---|---|
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java` | **New.** Background execution engine. Runs steps sequentially, updates `Step.status`, calls `uiRunner` for UI refreshes. |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java` | **Modified.** Switch table to `SWT.MULTI`, add `activeRunner` field, wire `onRun()` / `onRunSelected()` / `onStop()`, update `updateButtonStates()`. |
| `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF` | **Modified.** Export `com.example.automation` package so tests can import `WorkflowRunner`. |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRunnerTest.java` | **New.** 5 plain JUnit 4 unit tests — no Eclipse runtime needed. |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/AutomationViewTest.java` | **Modified.** +1 SWTBot test for multi-select. |

---

### Task 1: Write failing WorkflowRunner unit tests

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRunnerTest.java`

- [ ] **Step 1: Create the test file**

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
        new WorkflowRunner(steps, registry, r -> r.run(), () -> {}, done::countDown).start();
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
        Step step = new Step("missing");
        run(List.of(step), new ActionRegistry(List.of()));
        assertEquals(StepStatus.RED, step.getStatus());
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
            r -> r.run(), () -> {}, done::countDown);
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
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
cd com.example.automation.parent
mvn verify -q
```

Expected: `BUILD FAILURE` — `cannot find symbol: class WorkflowRunner`.

- [ ] **Step 3: Commit the failing tests**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRunnerTest.java
git commit -m "test: add failing WorkflowRunner unit tests"
```

---

### Task 2: Implement WorkflowRunner and export the package

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java`
- Modify: `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`

- [ ] **Step 1: Create WorkflowRunner.java**

```java
package com.example.automation;

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

    private volatile boolean cancelled = false;

    public WorkflowRunner(List<Step> steps, ActionRegistry registry,
                          Consumer<Runnable> uiRunner, Runnable refresh, Runnable onDone) {
        this.steps    = steps;
        this.registry = registry;
        this.uiRunner = uiRunner;
        this.refresh  = refresh;
        this.onDone   = onDone;
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
                step.setStatus(StepStatus.RED);
                uiRunner.accept(refresh);
                break;
            }
        }
        uiRunner.accept(onDone);
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
        public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }

        @Override
        public OutputStream getErrorStream() { return OutputStream.nullOutputStream(); }
    }
}
```

- [ ] **Step 2: Export the com.example.automation package in MANIFEST.MF**

Current `Export-Package` in `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`:
```
Export-Package: com.example.automation.api,
 com.example.automation.model,
 com.example.automation.persistence
```

Replace with:
```
Export-Package: com.example.automation,
 com.example.automation.api,
 com.example.automation.model,
 com.example.automation.persistence
```

- [ ] **Step 3: Run to confirm the 5 new tests pass**

```
cd com.example.automation.parent
mvn verify -q
```

Expected output includes:
```
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
```

(22 existing + 5 new WorkflowRunner tests)

- [ ] **Step 4: Commit**

```
git add com.example.automation/src/main/java/com/example/automation/WorkflowRunner.java
git add com.example.automation/META-INF/MANIFEST.MF
git commit -m "feat: add WorkflowRunner execution engine"
```

---

### Task 3: Wire AutomationView and add multi-select SWTBot test

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/AutomationViewTest.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java`

- [ ] **Step 1: Add the failing SWTBot multi-select test to AutomationViewTest.java**

Add this import at the top of `AutomationViewTest.java` (after existing imports):
```java
import org.eclipse.swt.widgets.Display;
```

Add this test method inside `AutomationViewTest`:
```java
@Test
public void multipleStepsCanBeSelected() throws IOException {
    WorkflowRepository repo = new WorkflowRepository();
    Workflow wf = new Workflow("multi-select-test-wf", "Multi Select Test", "test");
    wf.getSteps().add(new Step("action.a"));
    wf.getSteps().add(new Step("action.b"));
    repo.save(wf);
    try {
        bot.viewById("com.example.automation.view").close();
        bot.menu("Project").menu("Automation").click();
        bot.viewById("com.example.automation.view").bot()
           .comboBox().setSelection("Multi Select Test");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        int[] selCount = {0};
        Display.getDefault().syncExec(() -> {
            table.widget.select(new int[]{0, 1});
            selCount[0] = table.widget.getSelectionCount();
        });
        assertEquals(2, selCount[0]);
    } finally {
        repo.delete("multi-select-test-wf");
    }
}
```

- [ ] **Step 2: Run to confirm the new test fails**

```
cd com.example.automation.parent
mvn verify -q
```

Expected: the `multipleStepsCanBeSelected` test errors or the selection count is 1 (not 2) because the table is currently `SWT.SINGLE`.

- [ ] **Step 3: Rewrite AutomationView.java with multi-select and Run/Stop wiring**

Replace the entire file content:

```java
package com.example.automation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.part.ViewPart;

import com.example.automation.api.ActionRegistry;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;

public class AutomationView extends ViewPart {

    public static final String ID = "com.example.automation.view";

    private Combo workflowCombo;
    private TableViewer viewer;
    private TableViewerColumn nameCol;

    private List<Workflow> workflows = Collections.emptyList();
    private Workflow currentWorkflow;

    private ToolItem addStepItem, deleteStepItem, moveUpItem, moveDownItem;
    private ToolItem runItem, runSelectedItem, stopItem;

    private WorkflowRunner activeRunner;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));
        createCombo(parent);
        createToolBar(parent);
        createTable(parent);
        loadWorkflows();
        updateButtonStates();
    }

    private void createCombo(Composite parent) {
        workflowCombo = new Combo(parent, SWT.READ_ONLY);
        workflowCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        workflowCombo.addSelectionListener(
            SelectionListener.widgetSelectedAdapter(e -> onWorkflowSelected()));
    }

    private void createToolBar(Composite parent) {
        ToolBar bar = new ToolBar(parent, SWT.FLAT | SWT.WRAP);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        makeButton(bar, "New Workflow",  SelectionListener.widgetSelectedAdapter(e -> onNew()));

        new ToolItem(bar, SWT.SEPARATOR);

        addStepItem    = makeButton(bar, "Add Step",    SelectionListener.widgetSelectedAdapter(e -> onAddStep()));
        deleteStepItem = makeButton(bar, "Delete Step", SelectionListener.widgetSelectedAdapter(e -> onDeleteStep()));
        moveUpItem     = makeButton(bar, "Move Up",     SelectionListener.widgetSelectedAdapter(e -> onMoveUp()));
        moveDownItem   = makeButton(bar, "Move Down",   SelectionListener.widgetSelectedAdapter(e -> onMoveDown()));

        new ToolItem(bar, SWT.SEPARATOR);

        runItem         = makeButton(bar, "Run",          SelectionListener.widgetSelectedAdapter(e -> onRun()));
        runSelectedItem = makeButton(bar, "Run Selected", SelectionListener.widgetSelectedAdapter(e -> onRunSelected()));
        stopItem        = makeButton(bar, "Stop",         SelectionListener.widgetSelectedAdapter(e -> onStop()));
    }

    private ToolItem makeButton(ToolBar bar, String tooltip, SelectionListener listener) {
        ToolItem item = new ToolItem(bar, SWT.PUSH);
        item.setToolTipText(tooltip);
        item.setText(tooltip);
        item.addSelectionListener(listener);
        return item;
    }

    private void createTable(Composite parent) {
        viewer = new TableViewer(parent, SWT.FULL_SELECTION | SWT.BORDER | SWT.MULTI);
        viewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        TableViewerColumn statusCol = new TableViewerColumn(viewer, SWT.CENTER);
        statusCol.getColumn().setText("Status");
        statusCol.getColumn().setWidth(40);
        statusCol.setLabelProvider(new StepLabelProvider.Status());

        nameCol = new TableViewerColumn(viewer, SWT.NONE);
        nameCol.getColumn().setText("Name");
        nameCol.setLabelProvider(new StepLabelProvider.Name());

        TableViewerColumn configCol = new TableViewerColumn(viewer, SWT.NONE);
        configCol.getColumn().setText("Config");
        configCol.getColumn().setWidth(200);
        configCol.setLabelProvider(new StepLabelProvider.Config());

        viewer.getTable().addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                int total = viewer.getTable().getClientArea().width;
                int used  = statusCol.getColumn().getWidth()
                          + configCol.getColumn().getWidth();
                nameCol.getColumn().setWidth(Math.max(80, total - used));
            }
        });

        viewer.addSelectionChangedListener(e -> updateButtonStates());
    }

    private void loadWorkflows() {
        try {
            workflows = new WorkflowRepository().list();
        } catch (IOException e) {
            Platform.getLog(getClass()).error("Failed to load workflows", e);
            workflows = Collections.emptyList();
        }
        workflowCombo.removeAll();
        if (workflows.isEmpty()) {
            workflowCombo.add("(no workflows)");
            workflowCombo.select(0);
            workflowCombo.setEnabled(false);
            currentWorkflow = null;
            viewer.setInput(Collections.emptyList());
        } else {
            workflowCombo.setEnabled(true);
            for (Workflow wf : workflows) {
                workflowCombo.add(wf.getDisplayName());
            }
            workflowCombo.select(0);
            currentWorkflow = workflows.get(0);
            viewer.setInput(currentWorkflow.getSteps());
        }
    }

    private void onWorkflowSelected() {
        int idx = workflowCombo.getSelectionIndex();
        if (idx >= 0 && idx < workflows.size()) {
            currentWorkflow = workflows.get(idx);
            viewer.setInput(currentWorkflow.getSteps());
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasWorkflow = currentWorkflow != null;
        IStructuredSelection sel = viewer.getStructuredSelection();
        boolean hasStep   = !sel.isEmpty();
        int selCount      = sel.size();
        int selIdx        = viewer.getTable().getSelectionIndex();
        int stepCount     = hasWorkflow ? currentWorkflow.getSteps().size() : 0;
        boolean running   = activeRunner != null;

        addStepItem.setEnabled(!running && hasWorkflow);
        deleteStepItem.setEnabled(!running && hasStep);
        moveUpItem.setEnabled(!running && selCount == 1 && selIdx > 0);
        moveDownItem.setEnabled(!running && selCount == 1 && selIdx < stepCount - 1);
        runItem.setEnabled(!running && hasWorkflow && stepCount > 0);
        runSelectedItem.setEnabled(!running && hasStep);
        stopItem.setEnabled(running);
    }

    private void onNew() {
        Platform.getLog(getClass()).info("New workflow: not yet implemented (sub-project 7)");
    }

    private void onAddStep() {
        if (currentWorkflow == null) return;
        currentWorkflow.getSteps().add(new Step(""));
        save();
        viewer.refresh();
        updateButtonStates();
    }

    private void onDeleteStep() {
        IStructuredSelection sel = viewer.getStructuredSelection();
        if (sel.isEmpty()) return;
        currentWorkflow.getSteps().remove(sel.getFirstElement());
        save();
        viewer.refresh();
        updateButtonStates();
    }

    private void onMoveUp() {
        int idx = viewer.getTable().getSelectionIndex();
        if (idx <= 0) return;
        Collections.swap(currentWorkflow.getSteps(), idx, idx - 1);
        save();
        viewer.refresh();
        viewer.getTable().select(idx - 1);
        updateButtonStates();
    }

    private void onMoveDown() {
        List<Step> steps = currentWorkflow.getSteps();
        int idx = viewer.getTable().getSelectionIndex();
        if (idx < 0 || idx >= steps.size() - 1) return;
        Collections.swap(steps, idx, idx + 1);
        save();
        viewer.refresh();
        viewer.getTable().select(idx + 1);
        updateButtonStates();
    }

    private void onRun() {
        if (currentWorkflow == null) return;
        List<Step> steps = currentWorkflow.getSteps();
        steps.forEach(s -> { s.setStatus(StepStatus.WHITE); s.setProgress(0); });
        viewer.refresh();
        startRunner(new ArrayList<>(steps));
    }

    private void onRunSelected() {
        if (currentWorkflow == null) return;
        List<Step> allSteps = currentWorkflow.getSteps();
        List<Step> ordered = viewer.getStructuredSelection().toList().stream()
            .filter(o -> o instanceof Step)
            .map(o -> (Step) o)
            .sorted(Comparator.comparingInt(allSteps::indexOf))
            .collect(Collectors.toList());
        if (ordered.isEmpty()) return;
        ordered.forEach(s -> { s.setStatus(StepStatus.WHITE); s.setProgress(0); });
        viewer.refresh();
        startRunner(ordered);
    }

    private void onStop() {
        if (activeRunner != null) activeRunner.cancel();
    }

    private void startRunner(List<Step> steps) {
        Runnable onDone = () -> {
            activeRunner = null;
            updateButtonStates();
            viewer.refresh();
        };
        activeRunner = new WorkflowRunner(
            steps,
            ActionRegistry.getInstance(),
            viewer.getControl().getDisplay()::asyncExec,
            viewer::refresh,
            onDone);
        updateButtonStates();
        activeRunner.start();
    }

    private void save() {
        try {
            new WorkflowRepository().save(currentWorkflow);
        } catch (Exception e) {
            Platform.getLog(getClass()).error("Failed to save workflow", e);
        }
    }

    @Override
    public void setFocus() {
        if (viewer != null) viewer.getControl().setFocus();
    }
}
```

- [ ] **Step 4: Run all tests**

```
cd com.example.automation.parent
mvn verify -q
```

Expected:
```
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
```

(27 from Task 2 + 1 new SWTBot test)

- [ ] **Step 5: Commit**

```
git add com.example.automation/src/main/java/com/example/automation/AutomationView.java
git add com.example.automation.tests/src/main/java/com/example/automation/tests/AutomationViewTest.java
git commit -m "feat: wire Run/RunSelected/Stop and switch table to multi-select"
```
