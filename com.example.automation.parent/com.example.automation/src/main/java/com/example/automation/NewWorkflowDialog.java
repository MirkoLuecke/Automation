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
    private Text nameText;
    private Text descriptionText;
    private Label idPreviewLabel;
    private Workflow result;

    public NewWorkflowDialog(Shell parent, Set<String> existingIds) {
        super(parent);
        this.existingIds = existingIds;
    }

    @Override
    public void create() {
        super.create();
        setTitle("New Workflow");
        setMessage("Enter a display name.");
        getButton(OK).setEnabled(false);
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
                idPreviewLabel.setText("");
                getButton(OK).setEnabled(false);
            } else {
                String id = deriveId(name, existingIds);
                idPreviewLabel.setText("ID: " + id);
                setMessage("");
                getButton(OK).setEnabled(true);
            }
        });

        return area;
    }

    @Override
    protected void okPressed() {
        String name = nameText.getText().trim();
        String id = deriveId(name, existingIds);
        String description = descriptionText.getText().trim();
        result = new Workflow(id, name, description);
        super.okPressed();
    }

    public Workflow getResult() {
        return result;
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
