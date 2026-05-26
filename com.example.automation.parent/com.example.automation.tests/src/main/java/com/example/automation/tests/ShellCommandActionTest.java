package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.ShellCommandAction;

public class ShellCommandActionTest {

    @Test
    public void validate_rejectsBlankCommand() {
        List<String> errors = new ShellCommandAction().validate(Map.of("command", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankCommand() {
        List<String> errors = new ShellCommandAction().validate(Map.of("command", "echo hello"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_containsCommandAndWorkingDirKeys() {
        Map<String, String> cfg = new ShellCommandAction().getDefaultConfig();
        assertTrue(cfg.containsKey("command"));
        assertTrue(cfg.containsKey("workingDir"));
    }
}
