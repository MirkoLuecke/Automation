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

    @SuppressWarnings("unchecked")
    private void onRunSelected() {
        if (currentWorkflow == null) return;
        List<Step> allSteps = currentWorkflow.getSteps();
        List<Object> rawList = (List<Object>) viewer.getStructuredSelection().toList();
        List<Step> ordered = rawList.stream()
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
        if (activeRunner != null) {
            activeRunner.cancel();
            stopItem.setEnabled(false);
        }
    }

    private void startRunner(List<Step> steps) {
        if (activeRunner != null) return;
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
