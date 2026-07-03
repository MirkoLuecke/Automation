package com.example.automation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;

/**
 * Modal dialog that lists all available workflows (local and Artifactory) and
 * returns the workflow selected by the user. Selecting an Artifactory workflow
 * downloads and saves it to the local storage directory first.
 */
public class WorkflowPickerDialog extends TitleAreaDialog {

    // ── Entry types ───────────────────────────────────────────────────────────

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

    // ── Fields ────────────────────────────────────────────────────────────────

    private final List<Workflow> localWorkflows;
    private final File storageDir;
    private TableViewer viewer;
    private Label warningLabel;
    private Workflow result;

    public WorkflowPickerDialog(Shell parent, List<Workflow> localWorkflows, File storageDir) {
        super(parent);
        this.localWorkflows = localWorkflows;
        this.storageDir = storageDir;
    }

    @Override
    public void create() {
        super.create();
        setTitle("Open Workflow");
        setMessage("Local: " + storageDir.getAbsolutePath());
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

        // Warning label — hidden until an Artifactory error occurs
        warningLabel = new Label(area, SWT.WRAP);
        warningLabel.setBackground(area.getDisplay().getSystemColor(SWT.COLOR_YELLOW));
        GridData warnGd = new GridData(SWT.FILL, SWT.TOP, true, false);
        warnGd.exclude = true;
        warningLabel.setLayoutData(warnGd);
        warningLabel.setVisible(false);

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
                Workflow wf = ((WorkflowEntry) element).workflow;
                return wf == null ? "" : (wf.getDisplayName() == null ? "" : wf.getDisplayName());
            }
        });

        TableViewerColumn descCol = new TableViewerColumn(viewer, SWT.NONE);
        descCol.getColumn().setText("Description");
        descCol.getColumn().setWidth(220);
        descCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                Workflow wf = ((WorkflowEntry) element).workflow;
                if (wf == null || wf.getDescription() == null) return "";
                return wf.getDescription();
            }
        });

        TableViewerColumn sourceCol = new TableViewerColumn(viewer, SWT.NONE);
        sourceCol.getColumn().setText("Source");
        sourceCol.getColumn().setWidth(100);
        sourceCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return ((WorkflowEntry) element).source == SourceType.LOCAL ? "Local" : "Artifactory";
            }
        });

        // Fetch Artifactory entries and populate the table
        List<RemoteWorkflow> remote = Collections.emptyList();
        try {
            remote = new ArtifactoryClient().listWorkflows();
        } catch (ArtifactoryException e) {
            showWarning(e.getMessage());
        }
        viewer.setInput(buildEntries(localWorkflows, remote));

        viewer.addSelectionChangedListener(e ->
            getButton(OK).setEnabled(!viewer.getStructuredSelection().isEmpty()));
        viewer.addDoubleClickListener(e -> {
            if (!viewer.getStructuredSelection().isEmpty()) okPressed();
        });

        return area;
    }

    @Override
    protected void okPressed() {
        WorkflowEntry entry = (WorkflowEntry)
            ((IStructuredSelection) viewer.getSelection()).getFirstElement();
        if (entry == null) return;

        if (entry.source == SourceType.ARTIFACTORY) {
            if (!downloadAndSave(entry)) return;  // abort; dialog stays open
            result = loadFromDisk(entry);
        } else {
            result = entry.workflow;
        }
        super.okPressed();
    }

    /** @return the workflow selected by the user, or {@code null} if cancelled. */
    public Workflow getResult() { return result; }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean downloadAndSave(WorkflowEntry entry) {
        File target = new File(storageDir, entry.filename);
        if (target.exists()) {
            boolean overwrite = MessageDialog.openQuestion(getShell(),
                "Overwrite existing workflow?",
                "A workflow named '" + entry.workflow.getDisplayName()
                    + "' already exists locally. Overwrite?");
            if (!overwrite) return false;
        }
        try {
            storageDir.mkdirs();
            try (FileWriter fw = new FileWriter(target, StandardCharsets.UTF_8)) {
                fw.write(entry.rawJson);
            }
        } catch (IOException e) {
            MessageDialog.openError(getShell(), "Download failed",
                "Failed to save workflow to local directory: " + e.getMessage());
            return false;
        }
        return true;
    }

    private Workflow loadFromDisk(WorkflowEntry entry) {
        try {
            List<Workflow> updated = new WorkflowRepository(storageDir).list();
            return updated.stream()
                .filter(w -> entry.filename.equals(w.getWorkflowId() + ".json"))
                .findFirst()
                .orElse(entry.workflow);
        } catch (Exception e) {
            return entry.workflow;
        }
    }

    private void showWarning(String message) {
        GridData gd = (GridData) warningLabel.getLayoutData();
        gd.exclude = false;
        warningLabel.setVisible(true);
        warningLabel.setText("⚠ " + message);
        warningLabel.getParent().layout(true, true);
    }
}
