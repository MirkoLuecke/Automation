package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class SetActiveTargetPlatformAction implements IAction {

    @Override public String getId()          { return "set-active-target-platform"; }
    @Override public String getName()        { return "Set Active Target Platform"; }
    @Override public String getDescription() {
        return "Loads, resolves, and activates a .target file as the Eclipse workspace target platform.";
    }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("targetFile", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        Bundle pdeCore = Platform.getBundle("org.eclipse.pde.core");
        if (pdeCore == null || pdeCore.getState() != Bundle.ACTIVE)
            errors.add("PDE Core (org.eclipse.pde.core) is not installed or not active.");
        if (config.getOrDefault("targetFile", "").isBlank())
            errors.add("targetFile must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
