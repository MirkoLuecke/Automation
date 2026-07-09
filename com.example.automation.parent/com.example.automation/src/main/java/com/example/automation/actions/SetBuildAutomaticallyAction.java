package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class SetBuildAutomaticallyAction implements IAction {

    @Override public String getId()          { return "set-build-automatically"; }
    @Override public String getName()        { return "Set Build Automatically"; }
    @Override public String getDescription() { return "Enables or disables Project > Build Automatically in Eclipse."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("enabled", "true");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        return new ArrayList<>();
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        boolean enable = !"false".equals(config.getOrDefault("enabled", "true"));
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        IWorkspaceDescription desc = ws.getDescription();
        desc.setAutoBuilding(enable);
        ws.setDescription(desc);
        context.getStdout().println("Build automatically: " + (enable ? "enabled" : "disabled"));
        context.setProgress(100);
    }
}
