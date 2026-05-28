package com.example.automation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.part.ViewPart;

import com.example.automation.api.ActionRegistry;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;

public class AutomationView extends ViewPart {

    public static final String ID = "com.example.automation.view";

    private Label workflowNameLabel;
    private Label workflowDescLabel;
    private TableViewer viewer;
    private TableViewerColumn nameCol;

    private List<Workflow> workflows = Collections.emptyList();
    private Workflow currentWorkflow;

    private ToolItem newWorkflowItem, openWorkflowItem;
    private ToolItem addStepItem, deleteStepItem, moveUpItem, moveDownItem;
    private ToolItem runItem, runSelectedItem, stopItem;

    private WorkflowRunner activeRunner;
    private StepAdapterFactory adapterFactory;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));
        createHeader(parent);
        createToolBar(parent);
        createTable(parent);
        loadWorkflows();
        updateButtonStates();
        getSite().setSelectionProvider(viewer);
        adapterFactory = new StepAdapterFactory(this::save);
        Platform.getAdapterManager().registerAdapters(adapterFactory, Step.class);
    }

    private void createHeader(Composite parent) {
        workflowNameLabel = new Label(parent, SWT.NONE);
        workflowNameLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        workflowNameLabel.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));

        workflowDescLabel = new Label(parent, SWT.NONE);
        workflowDescLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        workflowDescLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
    }

    private void updateHeader() {
        if (currentWorkflow == null) {
            workflowNameLabel.setText("(no workflows)");
            workflowDescLabel.setText("");
        } else {
            workflowNameLabel.setText(currentWorkflow.getDisplayName());
            String desc = currentWorkflow.getDescription();
            workflowDescLabel.setText(desc == null ? "" : desc);
        }
        workflowNameLabel.getParent().layout();
    }

    private void createToolBar(Composite parent) {
        ISharedImages shared = PlatformUI.getWorkbench().getSharedImages();
        ToolBar bar = new ToolBar(parent, SWT.FLAT | SWT.WRAP);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        newWorkflowItem = makeButton(bar, "New Workflow",
            shared.getImage(ISharedImages.IMG_TOOL_NEW_WIZARD),
            SelectionListener.widgetSelectedAdapter(e -> onNew()));

        openWorkflowItem = makeButton(bar, "Open Workflow",
            shared.getImage(ISharedImages.IMG_OBJ_FOLDER),
            SelectionListener.widgetSelectedAdapter(e -> onOpenWorkflow()));

        new ToolItem(bar, SWT.SEPARATOR);

        addStepItem = makeButton(bar, "Add Step",
            shared.getImage(ISharedImages.IMG_OBJ_ADD),
            SelectionListener.widgetSelectedAdapter(e -> onAddStep()));
        deleteStepItem = makeButton(bar, "Delete Step",
            shared.getImage(ISharedImages.IMG_TOOL_DELETE),
            SelectionListener.widgetSelectedAdapter(e -> onDeleteStep()));
        moveUpItem = makeButton(bar, "Move Step Up",
            shared.getImage(ISharedImages.IMG_TOOL_UP),
            SelectionListener.widgetSelectedAdapter(e -> onMoveUp()));
        moveDownItem = makeButton(bar, "Move Step Down",
            shared.getImage(ISharedImages.IMG_TOOL_FORWARD),
            SelectionListener.widgetSelectedAdapter(e -> onMoveDown()));

        new ToolItem(bar, SWT.SEPARATOR);

        runItem = makeButton(bar, "Run Workflow",
            DebugUITools.getImage(IDebugUIConstants.IMG_ACT_RUN),
            SelectionListener.widgetSelectedAdapter(e -> onRun()));
        runSelectedItem = makeButton(bar, "Run Selected Steps",
            DebugUITools.getImage(IDebugUIConstants.IMG_ACT_RUN),
            SelectionListener.widgetSelectedAdapter(e -> onRunSelected()));
        stopItem = makeButton(bar, "Stop",
            shared.getImage(ISharedImages.IMG_ELCL_STOP),
            SelectionListener.widgetSelectedAdapter(e -> onStop()));
    }

    private ToolItem makeButton(ToolBar bar, String tooltip, Image image, SelectionListener listener) {
        ToolItem item = new ToolItem(bar, SWT.PUSH);
        item.setToolTipText(tooltip);
        item.setImage(image);
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
        if (workflows.isEmpty()) {
            currentWorkflow = null;
            viewer.setInput(Collections.emptyList());
        } else {
            currentWorkflow = workflows.get(0);
            viewer.setInput(currentWorkflow.getSteps());
        }
        updateHeader();
    }

    private void onOpenWorkflow() {
        WorkflowPickerDialog dialog = new WorkflowPickerDialog(getSite().getShell(), workflows);
        if (dialog.open() == Window.OK) {
            currentWorkflow = dialog.getResult();
            viewer.setInput(currentWorkflow.getSteps());
            updateHeader();
            updateButtonStates();
        }
    }

    private void updateButtonStates() {
        boolean hasWorkflow = currentWorkflow != null;
        IStructuredSelection sel = viewer.getStructuredSelection();
        boolean hasStep = !sel.isEmpty();
        int selCount    = sel.size();
        int selIdx      = viewer.getTable().getSelectionIndex();
        int stepCount   = hasWorkflow ? currentWorkflow.getSteps().size() : 0;
        boolean running = activeRunner != null;

        newWorkflowItem.setEnabled(!running);
        openWorkflowItem.setEnabled(!running && !workflows.isEmpty());
        addStepItem.setEnabled(!running && hasWorkflow);
        deleteStepItem.setEnabled(!running && hasStep);
        moveUpItem.setEnabled(!running && selCount == 1 && selIdx > 0);
        moveDownItem.setEnabled(!running && selCount == 1 && selIdx < stepCount - 1);
        runItem.setEnabled(!running && hasWorkflow && stepCount > 0);
        runSelectedItem.setEnabled(!running && hasStep);
        stopItem.setEnabled(running);
    }

    private void onNew() {
        Set<String> existingIds = workflows.stream()
            .map(Workflow::getWorkflowId)
            .collect(Collectors.toSet());
        NewWorkflowDialog dialog = new NewWorkflowDialog(getSite().getShell(), existingIds);
        if (dialog.open() == Window.OK) {
            saveNew(dialog.getResult());
        }
    }

    private void saveNew(Workflow wf) {
        try {
            new WorkflowRepository().save(wf);
        } catch (Exception e) {
            Platform.getLog(getClass()).error("Failed to save new workflow", e);
            MessageDialog.openError(getSite().getShell(), "Error", "Failed to save workflow: " + e.getMessage());
            return;
        }
        loadWorkflows();
        workflows.stream()
            .filter(w -> wf.getWorkflowId().equals(w.getWorkflowId()))
            .findFirst()
            .ifPresent(w -> {
                currentWorkflow = w;
                viewer.setInput(currentWorkflow.getSteps());
                updateHeader();
            });
        updateButtonStates();
    }

    private void onAddStep() {
        if (currentWorkflow == null) return;
        AddStepDialog dialog = new AddStepDialog(getSite().getShell(), ActionRegistry.getInstance());
        if (dialog.open() == Window.OK) {
            currentWorkflow.getSteps().add(dialog.getResult());
            save();
            viewer.refresh();
            updateButtonStates();
        }
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

    private void save() {
        if (currentWorkflow == null) return;
        try {
            new WorkflowRepository().save(currentWorkflow);
        } catch (Exception e) {
            Platform.getLog(getClass()).error("Failed to save workflow", e);
        }
    }

    @Override
    public void dispose() {
        if (adapterFactory != null) Platform.getAdapterManager().unregisterAdapters(adapterFactory);
        if (activeRunner != null) activeRunner.cancel();
        super.dispose();
    }

    @Override
    public void setFocus() {
        if (viewer != null) viewer.getControl().setFocus();
    }
}
