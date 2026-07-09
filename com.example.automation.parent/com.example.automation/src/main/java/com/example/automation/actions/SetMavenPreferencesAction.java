package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class SetMavenPreferencesAction implements IAction {

    private static final String M2E_NODE = "org.eclipse.m2e.core";

    @Override public String getId()          { return "set-maven-preferences"; }
    @Override public String getName()        { return "Set Maven Preferences"; }
    @Override public String getDescription() { return "Configures Maven preferences: Download Artifact Sources, Download Artifact Javadoc, and repository index updates. Leave a field empty to keep its current value."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("downloadSources", "", "downloadJavadoc", "", "updateIndexes", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        return new ArrayList<>();
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(M2E_NODE);
        apply(prefs, "eclipse.m2.downloadSources",  config.getOrDefault("downloadSources", ""));
        apply(prefs, "eclipse.m2.downloadJavadoc",  config.getOrDefault("downloadJavadoc", ""));
        apply(prefs, "eclipse.m2.updateIndexes",    config.getOrDefault("updateIndexes", ""));
        prefs.flush();
        context.getStdout().println("Maven preferences updated.");
        context.setProgress(100);
    }

    private static void apply(IEclipsePreferences prefs, String key, String value) {
        if ("true".equals(value)) prefs.putBoolean(key, true);
        else if ("false".equals(value)) prefs.putBoolean(key, false);
        // empty string → do not change
    }
}
