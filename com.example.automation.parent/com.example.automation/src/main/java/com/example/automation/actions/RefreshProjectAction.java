package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

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
        ResourcesPlugin.getWorkspace().getRoot()
            .getProject(projectName)
            .refreshLocal(IResource.DEPTH_INFINITE, null);
        context.setProgress(100);
    }
}
