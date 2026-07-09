package com.example.automation;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
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
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.part.ViewPart;

import com.example.automation.api.ActionRegistry;
import com.example.automation.preferences.AutomationPreferences;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Eclipse view (ID: {@value #ID}) that displays and runs automation workflows.
 * Provides a toolbar for workflow management (new, edit, open) and step management
 * (add, delete, move), plus run/stop controls. Step status is visualised with coloured
 * squares; output is streamed to the "Automation" console.
 */
public class AutomationView extends ViewPart {

    public static final String ID = "com.example.automation.view";

    private Label workflowNameLabel;
    private Label workflowDescLabel;
    private TableViewer viewer;
    private TableViewerColumn nameCol;

    private List<Workflow> workflows = Collections.emptyList();
    private Workflow currentWorkflow;

    private ToolItem newWorkflowItem, editWorkflowItem, openWorkflowItem;
    private ToolItem addStepItem, deleteStepItem, moveUpItem, moveDownItem;
    private ToolItem copyStepItem, pasteStepItem;
    private ToolItem runItem, runSelectedItem, stopItem;

    private final Gson gson = new GsonBuilder().create();
    private final IPartListener2 partListener = new IPartListener2() {
        @Override
        public void partActivated(IWorkbenchPartReference ref) {
            if (ref.getPart(false) == AutomationView.this) updateButtonStates();
        }
    };

    private WorkflowJob activeRunner;
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
        adapterFactory = new StepAdapterFactory(() -> { save(); viewer.refresh(); });
        Platform.getAdapterManager().registerAdapters(adapterFactory, Step.class);
        getSite().getPage().addPartListener(partListener);
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
            workflowDescLabel.setToolTipText(null);
        } else {
            workflowNameLabel.setText(currentWorkflow.getDisplayName());
            String desc = currentWorkflow.getDescription();
            if (desc == null) desc = "";
            String displayed = desc.length() > 100 ? desc.substring(0, 97) + "…" : desc;
            workflowDescLabel.setText(displayed);
            workflowDescLabel.setToolTipText(desc.isEmpty() ? null : desc);
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

        editWorkflowItem = makeButton(bar, "Edit Workflow",
            Activator.getDefault().getImageRegistry().get(Activator.IMG_EDIT),
            SelectionListener.widgetSelectedAdapter(e -> onEditWorkflow()));

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
            Activator.getDefault().getImageRegistry().get(Activator.IMG_UP_NAV),
            SelectionListener.widgetSelectedAdapter(e -> onMoveUp()));
        moveDownItem = makeButton(bar, "Move Step Down",
            Activator.getDefault().getImageRegistry().get(Activator.IMG_DOWN_NAV),
            SelectionListener.widgetSelectedAdapter(e -> onMoveDown()));
        copyStepItem = makeButton(bar, "Copy Step(s)",
            shared.getImage(ISharedImages.IMG_TOOL_COPY),
            SelectionListener.widgetSelectedAdapter(e -> onCopy()));
        pasteStepItem = makeButton(bar, "Paste Step(s)",
            shared.getImage(ISharedImages.IMG_TOOL_PASTE),
            SelectionListener.widgetSelectedAdapter(e -> onPaste()));

        new ToolItem(bar, SWT.SEPARATOR);

        runItem = makeButton(bar, "Run Workflow",
            DebugUITools.getImage(IDebugUIConstants.IMG_ACT_RUN),
            SelectionListener.widgetSelectedAdapter(e -> onRun()));
        runSelectedItem = makeButton(bar, "Run Selected Steps",
            Activator.getDefault().getImageRegistry().get(Activator.IMG_RUN_SELECTED),
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
        statusCol.getColumn().setWidth(60);
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

        viewer.addDoubleClickListener(e -> {
            try {
                getSite().getPage().showView("org.eclipse.ui.views.PropertySheet");
            } catch (PartInitException ex) {
                Platform.getLog(getClass()).error("Failed to show Properties view", ex);
            }
        });

        viewer.getTable().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.stateMask & SWT.CTRL) != 0) {
                    if (e.keyCode == 'c') onCopy();
                    else if (e.keyCode == 'v') onPaste();
                }
            }
        });
    }

    private void loadWorkflows() {
        try {
            workflows = repository().list();
        } catch (Exception e) {
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
        File storageDir = null;
        try { storageDir = resolvedStorageDir(); } catch (Exception e) {}
        if (storageDir == null) storageDir = new File(System.getProperty("user.home"));
        try { workflows = repository().list(); } catch (Exception e) {
            Platform.getLog(getClass()).error("Failed to reload workflows", e);
        }
        WorkflowPickerDialog dialog = new WorkflowPickerDialog(getSite().getShell(), workflows, storageDir);
        if (dialog.open() == Window.OK) {
            currentWorkflow = dialog.getResult();
            viewer.setInput(currentWorkflow.getSteps());
            updateHeader();
            updateButtonStates();
        }
    }

    private void onEditWorkflow() {
        if (currentWorkflow == null) return;
        Set<String> existingIds = workflows.stream()
            .map(Workflow::getWorkflowId)
            .filter(id -> !id.equals(currentWorkflow.getWorkflowId()))
            .collect(Collectors.toSet());
        NewWorkflowDialog dialog = new NewWorkflowDialog(getSite().getShell(), existingIds, currentWorkflow);
        if (dialog.open() == Window.OK) {
            try {
                repository().save(currentWorkflow);
            } catch (Exception e) {
                Platform.getLog(getClass()).error("Failed to save workflow", e);
                MessageDialog.openError(getSite().getShell(), "Error",
                    "Failed to save workflow: " + e.getMessage());
                return;
            }
            viewer.refresh();
            updateHeader();
            updateButtonStates();
        }
    }

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
        copyStepItem.setEnabled(!running && hasStep);
        pasteStepItem.setEnabled(!running && hasWorkflow && clipboardSteps() != null);
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
            repository().save(wf);
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
            int lastIdx = lastSelectedIndex();
            if (lastIdx < 0) {
                currentWorkflow.getSteps().add(step);
            } else {
                currentWorkflow.getSteps().add(lastIdx + 1, step);
            }
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

    private List<Step> clipboardSteps() {
        Clipboard cb = new Clipboard(viewer.getControl().getDisplay());
        try {
            String text = (String) cb.getContents(TextTransfer.getInstance());
            if (text == null) return null;
            try {
                Type type = new TypeToken<List<Step>>(){}.getType();
                List<Step> steps = gson.fromJson(text, type);
                return (steps != null && !steps.isEmpty()) ? steps : null;
            } catch (Exception e) {
                return null;
            }
        } finally {
            cb.dispose();
        }
    }

    /** Returns the highest selected row index, or -1 if nothing is selected. */
    private int lastSelectedIndex() {
        int[] indices = viewer.getTable().getSelectionIndices();
        if (indices.length == 0) return -1;
        Arrays.sort(indices);
        return indices[indices.length - 1];
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
        allSteps.forEach(s -> { s.setStatus(StepStatus.WHITE); s.setProgress(0); });
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
        String name = currentWorkflow != null ? currentWorkflow.getDisplayName() : "Workflow";
        activeRunner = new WorkflowJob(name, steps, ActionRegistry.getInstance(),
            viewer.getControl().getDisplay()::asyncExec, safeRefresh, onDone, stdout, stderr);
        updateButtonStates();
        activeRunner.schedule();
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

    private File resolvedStorageDir() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String resolved = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        return new File(resolved).getCanonicalFile();
    }

    private WorkflowRepository repository() throws Exception {
        return new WorkflowRepository(resolvedStorageDir());
    }

    private void save() {
        if (currentWorkflow == null) return;
        try {
            repository().save(currentWorkflow);
        } catch (Exception e) {
            Platform.getLog(getClass()).error("Failed to save workflow", e);
        }
    }

    @Override
    public void dispose() {
        getSite().getPage().removePartListener(partListener);
        if (adapterFactory != null) Platform.getAdapterManager().unregisterAdapters(adapterFactory);
        if (activeRunner != null) activeRunner.cancel();
        super.dispose();
    }

    @Override
    public void setFocus() {
        if (viewer != null) viewer.getControl().setFocus();
    }
}
