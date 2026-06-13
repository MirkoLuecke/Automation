package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.MavenUpdateRequest;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that triggers
 * <em>Maven &gt; Update Project</em> on a named Eclipse workspace project using M2E.
 *
 * <p>Config keys: {@code projectName} (required).
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
        MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(
            new MavenUpdateRequest(project, false, false),
            new NullProgressMonitor());
        context.setProgress(100);
    }
}
