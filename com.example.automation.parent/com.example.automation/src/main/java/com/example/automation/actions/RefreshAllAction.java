package com.example.automation.actions;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class RefreshAllAction implements IAction {

    @Override public String getId()          { return "refresh-all"; }
    @Override public String getName()        { return "Refresh All"; }
    @Override public String getDescription() { return "Refreshes all projects in the workspace."; }

    @Override public Map<String, String> getDefaultConfig() { return Map.of(); }
    @Override public List<String> validate(Map<String, String> config) { return List.of(); }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        context.setProgress(0);
        ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null);
        context.setProgress(100);
    }
}
