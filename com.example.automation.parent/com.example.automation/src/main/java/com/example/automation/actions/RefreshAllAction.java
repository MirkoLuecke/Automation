package com.example.automation.actions;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that refreshes every project in the
 * Eclipse workspace (equivalent to selecting all projects and pressing F5).
 *
 * <p>No config keys.
 */
public class RefreshAllAction implements IAction {

    @Override public String getId()          { return "refresh-all"; }
    @Override public String getName()        { return "Refresh All"; }
    @Override public String getDescription() { return "Refreshes all projects in the workspace."; }

    @Override public Map<String, String> getDefaultConfig() { return Map.of(); }
    @Override public List<String> validate(Map<String, String> config) { return List.of(); }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        context.setProgress(0);
        IJobManager jm = Job.getJobManager();
        // Disable auto-build before refreshLocal() so the filesystem scan does not trigger
        // concurrent Maven Project Builder runs for every module in hierarchical projects.
        // Those concurrent runs race on Plexus classloading and produce
        // TextFileChange NoClassDefFoundError dialogs (one per module).
        IWorkspaceDescription wsDesc = ResourcesPlugin.getWorkspace().getDescription();
        boolean wasAutoBuilding = wsDesc.isAutoBuilding();
        if (wasAutoBuilding) {
            wsDesc.setAutoBuilding(false);
            ResourcesPlugin.getWorkspace().setDescription(wsDesc);
        }
        try {
            jm.join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null);
        } finally {
            if (wasAutoBuilding) {
                IWorkspaceDescription freshDesc = ResourcesPlugin.getWorkspace().getDescription();
                freshDesc.setAutoBuilding(true);
                ResourcesPlugin.getWorkspace().setDescription(freshDesc);
            }
        }
        // Wait for the single clean auto-build that fires now that all resource changes
        // are batched and auto-build is restored.
        try {
            jm.join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        context.setProgress(100);
    }
}
