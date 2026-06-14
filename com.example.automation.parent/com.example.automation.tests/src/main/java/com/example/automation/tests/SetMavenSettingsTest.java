package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.SetMavenSettingsAction;

public class SetMavenSettingsTest {

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
}
