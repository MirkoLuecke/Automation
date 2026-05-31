package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.MavenRunWithProgressAction;

public class MavenRunWithProgressActionTest {

    @Test
    public void validate_rejectsBlankGoals() {
        List<String> errors = new MavenRunWithProgressAction().validate(Map.of("goals", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankGoals() {
        List<String> errors = new MavenRunWithProgressAction().validate(
            Map.of("goals", "clean install"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_containsGoalsAndWorkingDirKeys() {
        Map<String, String> cfg = new MavenRunWithProgressAction().getDefaultConfig();
        assertTrue(cfg.containsKey("goals"));
        assertTrue(cfg.containsKey("workingDir"));
    }
}
