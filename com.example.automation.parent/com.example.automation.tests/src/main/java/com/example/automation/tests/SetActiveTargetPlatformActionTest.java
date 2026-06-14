package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.SetActiveTargetPlatformAction;

public class SetActiveTargetPlatformActionTest {

    @Test
    public void validate_blankTargetFile_rejected() {
        List<String> errors = new SetActiveTargetPlatformAction()
            .validate(Map.of("targetFile", ""));
        assertTrue("expected targetFile error",
            errors.stream().anyMatch(e -> e.contains("targetFile")));
    }

    @Test
    public void defaultConfig_containsTargetFileKey() {
        assertTrue(new SetActiveTargetPlatformAction()
            .getDefaultConfig().containsKey("targetFile"));
    }

    @Test
    public void getId_returnsExpectedId() {
        assertEquals("set-active-target-platform",
            new SetActiveTargetPlatformAction().getId());
    }

    @Test
    public void getName_returnsSetActiveTargetPlatform() {
        assertEquals("Set Active Target Platform", new SetActiveTargetPlatformAction().getName());
    }
}
