package com.example.automation.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import com.example.automation.Activator;

/**
 * Registers default values for the Automation plugin's Eclipse preferences when the
 * plugin is first activated in a workspace.
 */
public class AutomationPreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault(AutomationPreferences.KEY_DEFAULT_WORKING_DIR,
                AutomationPreferences.DEFAULT_WORKING_DIR);
        store.setDefault(AutomationPreferences.KEY_WORKFLOW_STORAGE,
                AutomationPreferences.DEFAULT_WORKFLOW_STORAGE);
        store.setDefault(AutomationPreferences.KEY_WORKFLOWS_DEPLOYED, false);
    }
}
