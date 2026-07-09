package com.example.automation.preferences;

import org.eclipse.jface.preference.IPreferenceStore;
import com.example.automation.Activator;

/**
 * Static facade for the Automation plugin's JFace preference store.
 * Provides typed accessors for all preference keys; all methods delegate to
 * {@link #store()}.
 */
public class AutomationPreferences {

    public static final String KEY_DEFAULT_WORKING_DIR  = "defaultWorkingDir";
    public static final String KEY_WORKFLOW_STORAGE     = "workflowStorage";
    public static final String KEY_WORKFLOWS_DEPLOYED   = "workflowsDeployed";
    public static final String KEY_LAST_WORKFLOW_ID     = "lastWorkflowId";

    public static final String DEFAULT_WORKING_DIR      = "${workspace_loc}/..";
    public static final String DEFAULT_WORKFLOW_STORAGE = "${workspace_loc}/../automation";

    /**
     * Returns the plugin's JFace preference store.
     *
     * @return the preference store; never null while the plugin is active
     */
    public static IPreferenceStore store() {
        return Activator.getDefault().getPreferenceStore();
    }

    /**
     * Returns the configured default working directory (may contain Eclipse variables).
     *
     * @return the raw preference value; never null
     */
    public static String getDefaultWorkingDir() {
        return store().getString(KEY_DEFAULT_WORKING_DIR);
    }

    /**
     * Returns the configured workflow storage path (may contain Eclipse variables).
     *
     * @return the raw preference value; never null
     */
    public static String getWorkflowStoragePath() {
        return store().getString(KEY_WORKFLOW_STORAGE);
    }

    /**
     * Returns {@code true} if bundled workflows have already been deployed to this workspace.
     *
     * @return true if the deploy flag has been set
     */
    public static boolean isWorkflowsDeployed() {
        return store().getBoolean(KEY_WORKFLOWS_DEPLOYED);
    }

    /**
     * Marks bundled workflows as deployed (or not) in the preference store.
     *
     * @param v {@code true} after a successful deployment
     */
    public static void setWorkflowsDeployed(boolean v) {
        store().setValue(KEY_WORKFLOWS_DEPLOYED, v);
    }
}
