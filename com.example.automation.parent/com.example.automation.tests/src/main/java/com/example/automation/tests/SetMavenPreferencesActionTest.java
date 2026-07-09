package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.Before;
import org.junit.Test;

import com.example.automation.actions.SetMavenPreferencesAction;

public class SetMavenPreferencesActionTest {

    private static final String M2E_NODE = "org.eclipse.m2e.core";

    @Before
    public void resetPrefs() throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(M2E_NODE);
        prefs.remove("eclipse.m2.downloadSources");
        prefs.remove("eclipse.m2.downloadJavadoc");
        prefs.remove("eclipse.m2.updateIndexes");
        prefs.flush();
    }

    @Test
    public void setDownloadSources_true_writesPreference() throws Exception {
        new SetMavenPreferencesAction().execute(
            Map.of("downloadSources", "true", "downloadJavadoc", "", "updateIndexes", ""),
            nullCtx());
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(M2E_NODE);
        assertTrue(prefs.getBoolean("eclipse.m2.downloadSources", false));
    }

    @Test
    public void setDownloadSources_false_writesPreference() throws Exception {
        new SetMavenPreferencesAction().execute(
            Map.of("downloadSources", "false", "downloadJavadoc", "", "updateIndexes", ""),
            nullCtx());
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(M2E_NODE);
        assertFalse(prefs.getBoolean("eclipse.m2.downloadSources", true));
    }

    @Test
    public void emptyValue_doesNotChangePreference() throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(M2E_NODE);
        prefs.putBoolean("eclipse.m2.downloadSources", true);
        prefs.flush();

        new SetMavenPreferencesAction().execute(
            Map.of("downloadSources", "", "downloadJavadoc", "", "updateIndexes", ""),
            nullCtx());
        assertTrue("preference must be unchanged", prefs.getBoolean("eclipse.m2.downloadSources", false));
    }

    @Test
    public void validate_acceptsAllEmptyConfig() {
        List<String> errors = new SetMavenPreferencesAction().validate(
            Map.of("downloadSources", "", "downloadJavadoc", "", "updateIndexes", ""));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void getId_returnsSetMavenPreferences() {
        assertEquals("set-maven-preferences", new SetMavenPreferencesAction().getId());
    }

    private static com.example.automation.api.IActionContext nullCtx() {
        return new com.example.automation.api.IActionContext() {
            @Override public java.io.OutputStream getOutputStream() { return java.io.OutputStream.nullOutputStream(); }
            @Override public java.io.OutputStream getErrorStream()  { return java.io.OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p) {}
            @Override public boolean isCancelled() { return false; }
        };
    }
}
