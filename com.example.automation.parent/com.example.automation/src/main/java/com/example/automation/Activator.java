package com.example.automation;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.example.automation.preferences.AutomationPreferences;

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

    public static Activator getDefault() {
        return plugin;
    }

    @Override
    protected void initializeImageRegistry(ImageRegistry registry) {
        // Down arrow — Eclipse platform ships this but exposes no public constant
        ImageDescriptor down = imageDescriptorFromPlugin(
            "org.eclipse.ui", "icons/full/elcl16/down_nav.png");
        if (down != null) registry.put(IMG_DOWN_NAV, down);

        // Run-selected — uses the Debug UI "run object" icon (visually distinct from IMG_ACT_RUN)
        ImageDescriptor runSel = imageDescriptorFromPlugin(
            "org.eclipse.debug.ui", "icons/full/obj16/run_obj.png");
        if (runSel != null) registry.put(IMG_RUN_SELECTED, runSel);

        // Edit / pencil — try two common platform locations
        ImageDescriptor edit = imageDescriptorFromPlugin(
            "org.eclipse.ui", "icons/full/etool16/edit.png");
        if (edit == null) edit = imageDescriptorFromPlugin(
            "org.eclipse.ui.workbench", "icons/full/etool16/edit.png");
        if (edit != null) registry.put(IMG_EDIT, edit);
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
