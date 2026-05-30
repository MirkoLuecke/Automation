package com.example.automation.preferences;

import org.eclipse.jface.preference.IPreferenceStore;
import com.example.automation.Activator;

public class AutomationPreferences {

    public static final String KEY_DEFAULT_WORKING_DIR  = "defaultWorkingDir";
    public static final String KEY_WORKFLOW_STORAGE     = "workflowStorage";
    public static final String KEY_WORKFLOWS_DEPLOYED   = "workflowsDeployed";

    public static final String DEFAULT_WORKING_DIR      = "${workspace_loc}/..";
    public static final String DEFAULT_WORKFLOW_STORAGE = "${workspace_loc}/../automation";

    public static IPreferenceStore store() {
        return Activator.getDefault().getPreferenceStore();
    }

    public static String getDefaultWorkingDir() {
        return store().getString(KEY_DEFAULT_WORKING_DIR);
    }

    public static String getWorkflowStoragePath() {
        return store().getString(KEY_WORKFLOW_STORAGE);
    }

    public static boolean isWorkflowsDeployed() {
        return store().getBoolean(KEY_WORKFLOWS_DEPLOYED);
    }

    public static void setWorkflowsDeployed(boolean v) {
        store().setValue(KEY_WORKFLOWS_DEPLOYED, v);
    }
}
