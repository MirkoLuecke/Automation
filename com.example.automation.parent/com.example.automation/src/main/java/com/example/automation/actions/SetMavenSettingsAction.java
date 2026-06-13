package com.example.automation.actions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Platform;
import org.eclipse.m2e.core.MavenPlugin;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that updates M2E's Maven user settings
 * to a given {@code settings.xml} file path.
 *
 * <p>Config keys: {@code filePath} (required).
 */
public class SetMavenSettingsAction implements IAction {

    @Override public String getId()          { return "set-maven-settings"; }
    @Override public String getName()        { return "Set Maven Settings"; }
    @Override public String getDescription() {
        return "Sets the Maven user settings file in Eclipse's M2E configuration.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("filePath", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        Bundle m2e = Platform.getBundle("org.eclipse.m2e.core");
        if (m2e == null || m2e.getState() != Bundle.ACTIVE)
            errors.add("M2E (Maven Integration for Eclipse) is not installed or not active.");
        if (config.getOrDefault("filePath", "").isBlank())
            errors.add("filePath must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String filePath = config.getOrDefault("filePath", "");
        if (filePath.isBlank()) throw new IllegalArgumentException("filePath must not be blank");
        context.setProgress(0);
        MavenPlugin.getMavenConfiguration().setUserSettingsFile(filePath);
        context.getStdout().println("Maven user settings set to: "
            + Path.of(filePath).toAbsolutePath());
        context.setProgress(100);
    }
}
