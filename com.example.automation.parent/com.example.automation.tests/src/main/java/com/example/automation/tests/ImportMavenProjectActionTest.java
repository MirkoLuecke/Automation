package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.ImportMavenProjectAction;

public class ImportMavenProjectActionTest {

    @Test
    public void validate_blankPomPath_alwaysRejected() {
        List<String> errors = new ImportMavenProjectAction().validate(Map.of("pomPath", ""));
        assertTrue(errors.stream().anyMatch(e -> e.contains("pomPath")));
    }

    @Test
    public void defaultConfig_containsPomPathKey() {
        assertTrue(new ImportMavenProjectAction().getDefaultConfig().containsKey("pomPath"));
    }
}
