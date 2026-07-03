package com.example.automation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.example.automation.RemoteWorkflow;
import com.example.automation.model.Workflow;

/**
 * Modal dialog that lists all available workflows and returns the one selected by
 * the user. Also displays the resolved workflow storage path in the title area.
 */
public class WorkflowPickerDialog extends TitleAreaDialog {

    // ── Entry types ───────────────────────────────────────────────────────────────

    public enum SourceType { LOCAL, ARTIFACTORY }

    public static class WorkflowEntry {
        public final Workflow workflow;
        public final SourceType source;
        public final String rawJson;   // null for LOCAL
        public final String filename;

        public WorkflowEntry(Workflow workflow, SourceType source, String rawJson, String filename) {
            this.workflow = workflow;
            this.source = source;
            this.rawJson = rawJson;
            this.filename = filename;
        }
    }

    public static List<WorkflowEntry> buildEntries(List<Workflow> local, List<RemoteWorkflow> remote) {
        List<WorkflowEntry> result = new ArrayList<>();
        for (Workflow wf : local)
            result.add(new WorkflowEntry(wf, SourceType.LOCAL, null, wf.getWorkflowId() + ".json"));
        for (RemoteWorkflow rw : remote)
            result.add(new WorkflowEntry(rw.workflow, SourceType.ARTIFACTORY, rw.rawJson, rw.filename));
        result.sort(Comparator
            .comparing((WorkflowEntry e) -> e.workflow.getDisplayName() == null ? "" : e.workflow.getDisplayName())
            .thenComparingInt(e -> e.source == SourceType.LOCAL ? 0 : 1));
        return result;
    }

    private final List<Workflow> workflows;
    private final String storagePath;
    private TableViewer viewer;
    private Workflow result;

    public WorkflowPickerDialog(Shell parent, List<Workflow> workflows, String storagePath) {
        super(parent);
        this.workflows = workflows;
        this.storagePath = storagePath;
    }

    @Override
    public void create() {
        super.create();
        setTitle("Open Workflow");
        setMessage("Workflows are loaded from: " + storagePath);
        getButton(OK).setEnabled(false);
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Open Workflow");
    }

    @Override
    protected boolean isResizable() { return true; }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        viewer = new TableViewer(area, SWT.FULL_SELECTION | SWT.BORDER | SWT.SINGLE);
        viewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        TableViewerColumn nameCol = new TableViewerColumn(viewer, SWT.NONE);
        nameCol.getColumn().setText("Name");
        nameCol.getColumn().setWidth(180);
        nameCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return ((Workflow) element).getDisplayName();
            }
        });

        TableViewerColumn descCol = new TableViewerColumn(viewer, SWT.NONE);
        descCol.getColumn().setText("Description");
        descCol.getColumn().setWidth(250);
        descCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                String desc = ((Workflow) element).getDescription();
                return desc == null ? "" : desc;
            }
        });

        viewer.setInput(workflows);

        viewer.addSelectionChangedListener(e ->
            getButton(OK).setEnabled(!viewer.getStructuredSelection().isEmpty()));

        viewer.addDoubleClickListener(e -> {
            if (!viewer.getStructuredSelection().isEmpty()) okPressed();
        });

        return area;
    }

    @Override
    protected void okPressed() {
        result = (Workflow) ((IStructuredSelection) viewer.getSelection()).getFirstElement();
        super.okPressed();
    }

    /**
     * Returns the workflow selected by the user.
     *
     * @return the selected workflow, or {@code null} if the dialog was cancelled
     */
    public Workflow getResult() { return result; }
}
