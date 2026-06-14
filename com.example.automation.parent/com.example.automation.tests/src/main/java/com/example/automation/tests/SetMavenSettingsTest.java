package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.m2e.core.MavenPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.SetMavenSettingsAction;
import com.example.automation.api.IActionContext;

public class SetMavenSettingsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private String originalSettings;

    @Before
    public void captureOriginalSettings() {
        originalSettings = MavenPlugin.getMavenConfiguration().getUserSettingsFile();
    }

    @After
    public void restoreOriginalSettings() throws Exception {
        MavenPlugin.getMavenConfiguration().setUserSettingsFile(originalSettings);
    }

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }

    @Test
    public void validate_rejectsBlankFilePath() {
        List<String> errors = new SetMavenSettingsAction().validate(Map.of("filePath", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankFilePath() {
        List<String> errors = new SetMavenSettingsAction().validate(
            Map.of("filePath", "/path/to/settings.xml"));
        assertTrue(errors.stream().noneMatch(e -> e.contains("filePath")));
    }

    @Test
    public void defaultConfig_containsFilePathKey() {
        Map<String, String> cfg = new SetMavenSettingsAction().getDefaultConfig();
        assertTrue(cfg.containsKey("filePath"));
    }

    @Test
    public void getId_returnsSetMavenSettings() {
        assertEquals("set-maven-settings", new SetMavenSettingsAction().getId());
    }

    @Test
    public void getName_returnsSetMavenSettings() {
        assertEquals("Set Maven Settings", new SetMavenSettingsAction().getName());
    }

    @Test
    public void execute_setsUserSettingsFile() throws Exception {
        File settings = tmp.newFile("settings.xml");
        new SetMavenSettingsAction().execute(
            Map.of("filePath", settings.getAbsolutePath()), nullCtx());
        assertEquals(settings.getAbsolutePath(),
            MavenPlugin.getMavenConfiguration().getUserSettingsFile());
    }
}
