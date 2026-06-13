package com.example.automation.actions;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that configures Eclipse workspace-level
 * JDT save actions: organize imports and format edited lines.
 *
 * <p>Config keys: {@code organizeImports} (default {@code "true"}),
 * {@code formatEditedLines} (default {@code "true"}).
 */
public class SetSaveActionsAction implements IAction {

    private static final String PREF_NODE = "org.eclipse.jdt.ui";

    @Override public String getId()          { return "set-save-actions"; }
    @Override public String getName()        { return "Set Save Actions"; }
    @Override public String getDescription() {
        return "Configures Eclipse save actions: organize imports and format edited lines.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("organizeImports", "true", "formatEditedLines", "true");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        return List.of();
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        context.setProgress(0);

        boolean organizeImports   = Boolean.parseBoolean(config.getOrDefault("organizeImports",   "true"));
        boolean formatEditedLines = Boolean.parseBoolean(config.getOrDefault("formatEditedLines", "true"));
        boolean master            = organizeImports || formatEditedLines;

        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        prefs.put("sp_cleanup.on_save_use_additional_actions",  String.valueOf(master));
        prefs.put("sp_cleanup.organize_imports",                String.valueOf(organizeImports));
        prefs.put("sp_cleanup.format_source_code",              String.valueOf(formatEditedLines));
        prefs.put("sp_cleanup.format_source_code_changes_only", String.valueOf(formatEditedLines));
        prefs.flush();

        context.getStdout().println("Save actions configured:"
            + " organizeImports=" + organizeImports
            + ", formatEditedLines=" + formatEditedLines);
        context.setProgress(100);
    }
}
