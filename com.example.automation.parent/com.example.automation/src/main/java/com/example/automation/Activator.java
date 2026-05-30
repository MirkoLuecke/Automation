package com.example.automation;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.example.automation.preferences.AutomationPreferences;

public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.example.automation";

    private static Activator plugin;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        tryInstallBundledWorkflows();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return plugin;
    }

    private void tryInstallBundledWorkflows() {
        try {
            IStringVariableManager svm =
                    VariablesPlugin.getDefault().getStringVariableManager();
            String resolved = svm.performStringSubstitution(
                    AutomationPreferences.getWorkflowStoragePath());
            BundledWorkflowInstaller.installIfNeeded(resolved);
        } catch (Exception e) {
            Platform.getLog(getClass()).warn(
                    "Could not auto-deploy bundled workflows: " + e.getMessage());
        }
    }
}
