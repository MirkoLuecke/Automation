package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.example.automation.actions.SetSaveActionsAction;
import com.example.automation.api.IActionContext;

public class SetSaveActionsActionTest {

    private static final String PREF_NODE = "org.eclipse.jdt.ui";
    private static final String[] PREF_KEYS = {
        "sp_cleanup.on_save_use_additional_actions",
        "sp_cleanup.organize_imports",
        "sp_cleanup.format_source_code",
        "sp_cleanup.format_source_code_changes_only"
    };

    private Map<String, String> originalValues;

    @Before
    public void captureOriginals() throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        originalValues = new HashMap<>();
        for (String key : PREF_KEYS)
            originalValues.put(key, prefs.get(key, null));
    }

    @After
    public void restorePreferences() throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        for (String key : PREF_KEYS) {
            String original = originalValues.get(key);
            if (original == null)
                prefs.remove(key);
            else
                prefs.put(key, original);
        }
        prefs.flush();
    }

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()           { return false; }
        };
    }

    private static IEclipsePreferences prefs() {
        return InstanceScope.INSTANCE.getNode(PREF_NODE);
    }

    @Test
    public void apply_defaults_enablesBothFeatures() throws Exception {
        new SetSaveActionsAction().execute(
            Map.of("organizeImports", "true", "formatEditedLines", "true"), nullCtx());

        assertEquals("true", prefs().get("sp_cleanup.on_save_use_additional_actions", null));
        assertEquals("true", prefs().get("sp_cleanup.organize_imports", null));
        assertEquals("true", prefs().get("sp_cleanup.format_source_code", null));
        assertEquals("true", prefs().get("sp_cleanup.format_source_code_changes_only", null));
    }

    @Test
    public void apply_organizeImportsOnly() throws Exception {
        new SetSaveActionsAction().execute(
            Map.of("organizeImports", "true", "formatEditedLines", "false"), nullCtx());

        assertEquals("true",  prefs().get("sp_cleanup.on_save_use_additional_actions", null));
        assertEquals("true",  prefs().get("sp_cleanup.organize_imports", null));
        assertEquals("false", prefs().get("sp_cleanup.format_source_code", null));
        assertEquals("false", prefs().get("sp_cleanup.format_source_code_changes_only", null));
    }

    @Test
    public void apply_bothDisabled_masterSwitchOff() throws Exception {
        new SetSaveActionsAction().execute(
            Map.of("organizeImports", "false", "formatEditedLines", "false"), nullCtx());

        assertEquals("false", prefs().get("sp_cleanup.on_save_use_additional_actions", null));
        assertEquals("false", prefs().get("sp_cleanup.organize_imports", null));
        assertEquals("false", prefs().get("sp_cleanup.format_source_code", null));
        assertEquals("false", prefs().get("sp_cleanup.format_source_code_changes_only", null));
    }

    @Test
    public void validate_alwaysEmpty() {
        assertTrue(new SetSaveActionsAction().validate(Map.of()).isEmpty());
        assertTrue(new SetSaveActionsAction().validate(
            Map.of("organizeImports", "false", "formatEditedLines", "false")).isEmpty());
    }
}
