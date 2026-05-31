package com.example.automation;

import java.util.Set;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.example.automation.model.Workflow;

public class NewWorkflowDialog extends TitleAreaDialog {

    private final Set<String> existingIds;
    private final Workflow toEdit;
    private Text nameText;
    private Text descriptionText;
    private Label idPreviewLabel;
    private Workflow result;

    public NewWorkflowDialog(Shell parent, Set<String> existingIds) {
        this(parent, existingIds, null);
    }

    public NewWorkflowDialog(Shell parent, Set<String> existingIds, Workflow toEdit) {
        super(parent);
        this.existingIds = existingIds;
        this.toEdit = toEdit;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(toEdit == null ? "New Workflow" : "Edit Workflow");
    }

    @Override
    public void create() {
        super.create();
        if (toEdit == null) {
            setTitle("New Workflow");
            setMessage("Enter a display name.");
            getButton(OK).setEnabled(false);
        } else {
            setTitle("Edit Workflow");
            setMessage("Edit the name and description of the workflow.");
            nameText.setText(toEdit.getDisplayName() != null ? toEdit.getDisplayName() : "");
            descriptionText.setText(toEdit.getDescription() != null ? toEdit.getDescription() : "");
            idPreviewLabel.setText("ID: " + toEdit.getWorkflowId() + " (unchanged)");
            getButton(OK).setEnabled(!nameText.getText().trim().isEmpty());
        }
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        container.setLayout(new GridLayout(2, false));

        new Label(container, SWT.NONE).setText("Name:");
        nameText = new Text(container, SWT.BORDER | SWT.SINGLE);
        nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(container, SWT.NONE); // spacer under "Name:" label
        idPreviewLabel = new Label(container, SWT.NONE);
        idPreviewLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        idPreviewLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

        new Label(container, SWT.NONE).setText("Description:");
        descriptionText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        GridData descData = new GridData(SWT.FILL, SWT.FILL, true, true);
        descData.heightHint = 60;
        descriptionText.setLayoutData(descData);

        nameText.addModifyListener(e -> {
            String name = nameText.getText().trim();
            if (name.isEmpty()) {
                setMessage("Enter a display name.");
                if (toEdit == null) idPreviewLabel.setText("");
                getButton(OK).setEnabled(false);
            } else {
                if (toEdit == null) {
                    String id = deriveId(name, existingIds);
                    idPreviewLabel.setText("ID: " + id);
                    setMessage("");
                }
                getButton(OK).setEnabled(true);
            }
        });

        return area;
    }

    @Override
    protected void okPressed() {
        String name = nameText.getText().trim();
        String description = descriptionText.getText().trim();
        if (toEdit != null) {
            result = applyEdits(toEdit, name, description);
        } else {
            String id = deriveId(name, existingIds);
            result = new Workflow(id, name, description);
        }
        super.okPressed();
    }

    public Workflow getResult() {
        return result;
    }

    public static Workflow applyEdits(Workflow toEdit, String name, String description) {
        toEdit.setDisplayName(name);
        toEdit.setDescription(description);
        return toEdit;
    }

    public static String deriveId(String displayName, Set<String> existingIds) {
        if (displayName == null || displayName.isBlank()) return "workflow";
        String base = displayName.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "workflow";
        }
        if (!existingIds.contains(base)) {
            return base;
        }
        int counter = 2;
        while (existingIds.contains(base + "-" + counter)) {
            counter++;
        }
        return base + "-" + counter;
    }
}
