package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2e.core.MavenPlugin;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that refreshes a single named project
 * in the Eclipse workspace.
 *
 * <p>Config keys: {@code projectName} (required).
 */
public class RefreshProjectAction implements IAction {

    @Override public String getId()          { return "refresh-project"; }
    @Override public String getName()        { return "Refresh Project"; }
    @Override public String getDescription() { return "Refreshes a specific project in the workspace."; }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("projectName", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("projectName", "").isBlank())
            errors.add("projectName must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String projectName = config.get("projectName");
        if (projectName == null || projectName.isBlank())
            throw new IllegalArgumentException("projectName must not be blank");

        context.setProgress(0);
        IJobManager jm = Job.getJobManager();
        // Disable auto-build before refreshLocal() so the filesystem scan does not trigger
        // concurrent Maven Project Builder runs for every module in a hierarchical project.
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
            ResourcesPlugin.getWorkspace().getRoot()
                .getProject(projectName)
                .refreshLocal(IResource.DEPTH_INFINITE, null);
            // Drain m2e UpdateProjectJobs triggered by refreshLocal(). After the workspace
            // resource tree is updated, m2e's ProjectRegistryRefreshJob fires and may schedule
            // UpdateProjectJobs that use TextFileChange. Those must complete while auto-build
            // is still disabled, or they race with the Java Builder's m2e build participant
            // on first-time loading of TextFileChange, causing NoClassDefFoundError.
            try {
                jm.join(MavenPlugin.getProjectConfigurationManager(), null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
