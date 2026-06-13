package com.example.automation.preferences;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.example.automation.BundledWorkflowInstaller;

/**
 * Eclipse preference page ({@code Preferences > Automation}) that configures the
 * default working directory, workflow storage location, and provides a button to
 * deploy bundled workflows to the configured storage folder.
 */
public class AutomationPreferencePage extends FieldEditorPreferencePage
        implements IWorkbenchPreferencePage {

    private StringFieldEditor storageEditor;

    public AutomationPreferencePage() {
        super(GRID);
        setPreferenceStore(AutomationPreferences.store());
        setDescription("Configure Automation plugin settings.");
    }

    @Override
    public void init(IWorkbench workbench) {
    }

    @Override
    protected void createFieldEditors() {
        addField(new StringFieldEditor(
                AutomationPreferences.KEY_DEFAULT_WORKING_DIR,
                "Default working directory:",
                getFieldEditorParent()));

        storageEditor = new StringFieldEditor(
                AutomationPreferences.KEY_WORKFLOW_STORAGE,
                "Workflow storage location:",
                getFieldEditorParent());
        addField(storageEditor);

        Button deployButton = new Button(getFieldEditorParent(), SWT.PUSH);
        deployButton.setText("Deploy bundled workflows");
        deployButton.setToolTipText(
                "Copies the workflows bundled with this plugin into the configured\n" +
                "workflow storage folder. Existing files with the same name are overwritten.\n" +
                "Use this after changing the storage location.");
        GridData gd = new GridData();
        gd.horizontalSpan = 2;
        deployButton.setLayoutData(gd);
        deployButton.addListener(SWT.Selection, e -> onDeploy());
    }

    private void onDeploy() {
        String raw = storageEditor.getStringValue();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        try {
            String resolved = svm.performStringSubstitution(raw);
            BundledWorkflowInstaller.install(resolved);
            setMessage("Bundled workflows deployed to: " + resolved, INFORMATION);
            setErrorMessage(null);
        } catch (CoreException e) {
            setErrorMessage("Could not resolve path: " + e.getMessage());
        } catch (Exception e) {
            setErrorMessage("Deploy failed: " + e.getMessage());
        }
    }
}
