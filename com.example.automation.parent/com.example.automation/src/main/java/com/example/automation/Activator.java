package com.example.automation;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.example.automation.preferences.AutomationPreferences;

/**
 * OSGi bundle activator for {@code com.example.automation}. Registers the plugin's
 * image registry on startup and installs bundled workflow JSON files into the
 * configured storage directory on first launch.
 */
public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.example.automation";

    public static final String IMG_DOWN_NAV     = "down_nav";
    public static final String IMG_RUN_SELECTED = "run_selected";
    public static final String IMG_EDIT         = "edit";

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

    /**
     * Returns the shared plugin instance.
     *
     * @return the activator singleton, or {@code null} before activation
     */
    public static Activator getDefault() {
        return plugin;
    }

    @Override
    protected void initializeImageRegistry(ImageRegistry registry) {
        registry.put(IMG_DOWN_NAV,     imageDescriptorFromPlugin(PLUGIN_ID, "icons/down_nav.png"));
        registry.put(IMG_RUN_SELECTED, imageDescriptorFromPlugin(PLUGIN_ID, "icons/run_selected.png"));
        registry.put(IMG_EDIT,         imageDescriptorFromPlugin(PLUGIN_ID, "icons/edit.png"));
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
