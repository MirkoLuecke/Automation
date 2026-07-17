package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that triggers
 * <em>Maven &gt; Update Project</em> on a named Eclipse workspace project and all of
 * its Maven sub-modules, matching the Eclipse UI dialog which pre-selects every project
 * whose location is inside the named project's directory.
 *
 * <p>Config keys: {@code projectName} (required — name of the root/parent project).
 */
public class MavenUpdateProjectAction implements IAction {

    @Override public String getId()          { return "maven-update-project"; }
    @Override public String getName()        { return "Maven Update Project"; }
    @Override public String getDescription() { return "Updates Maven project configuration (Maven > Update Project)."; }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("projectName", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        Bundle m2e = Platform.getBundle("org.eclipse.m2e.core");
        if (m2e == null || m2e.getState() != Bundle.ACTIVE)
            errors.add("M2E (Maven Integration for Eclipse) is not installed or not active.");
        if (config.getOrDefault("projectName", "").isBlank())
            errors.add("projectName must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String projectName = config.get("projectName");
        if (projectName == null || projectName.isBlank())
            throw new IllegalArgumentException("projectName must not be blank");

        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (!project.exists())
            throw new Exception("Project not found in workspace: " + projectName);

        context.setProgress(0);
        IJobManager jm = Job.getJobManager();
        // Disable Eclipse auto-build first, then drain jobs already scheduled before the
        // disable took effect. Reversing this order (drain → disable) leaves a race window:
        // new auto-build jobs can be scheduled between the join returning and
        // setAutoBuilding(false) taking effect, running concurrently with M2E's
        // updateProjectConfiguration and causing TextFileChange NoClassDefFoundError.
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
            // Ensure MavenExecutionRequestPopulator and other Plexus components are loaded
            // before updateProjectConfiguration() needs them. Same race as importProjects().
            MavenPlugin.getMaven().execute((ctx, mon) -> {
                ctx.getExecutionRequest();
                return null;
            }, new NullProgressMonitor());
            // Collect the root project and every Maven project whose disk location sits
            // inside the root's directory — these are its sub-modules as Eclipse projects.
            IPath rootLocation = project.getLocation();
            List<IProject> toUpdate = new ArrayList<>();
            for (IMavenProjectFacade facade : MavenPlugin.getMavenProjectRegistry().getProjects()) {
                IPath loc = facade.getProject().getLocation();
                if (loc != null && rootLocation != null && rootLocation.isPrefixOf(loc))
                    toUpdate.add(facade.getProject());
            }
            if (toUpdate.isEmpty()) toUpdate.add(project); // fallback: root only
            // Pre-initialize per-project Plexus containers on this thread (where OSGi classloading
            // is fully set up) so the background Maven Builder reuses the cached container rather
            // than creating one on a job thread where MavenExecutionRequestPopulator is missing.
            for (IMavenProjectFacade facade : MavenPlugin.getMavenProjectRegistry().getProjects()) {
                IPath loc = facade.getProject().getLocation();
                if (loc != null && rootLocation != null && rootLocation.isPrefixOf(loc))
                    facade.getComponentLookup();
            }
            context.getStdout().println("Updating " + toUpdate.size() + " Maven project(s).");
            // updateProjectConfiguration(MavenUpdateRequest, IProgressMonitor) only accepts
            // a request with exactly one project; call per-project to update all sub-modules.
            // Wrap in AVOID_UPDATE to suppress MavenProjectBuilder auto-builds between
            // individual calls — without it, builds race on the M2E mutable project registry
            // and throw StaleMutableProjectRegistryException as an unhandled error dialog.
            ResourcesPlugin.getWorkspace().run(mon -> {
                for (IProject p : toUpdate)
                    MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(p, new NullProgressMonitor());
            }, null, IWorkspace.AVOID_UPDATE, new NullProgressMonitor());
            try {
                jm.join(MavenPlugin.getProjectConfigurationManager(), null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            project.refreshLocal(IResource.DEPTH_INFINITE, null);
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
        context.setProgress(100);
    }
}
