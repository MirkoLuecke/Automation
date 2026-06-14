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

    @Test
    public void description_mentionsPowershell() {
        String desc = new ShellCommandAction().getDescription().toLowerCase();
        assertTrue(desc.contains("powershell"));
    }

    @Test
    public void buildCommand_onCurrentOs_firstElementIsShellExecutable() {
        List<String> cmd = ShellCommandAction.buildCommand("echo hi");
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            assertEquals("powershell.exe", cmd.get(0));
            assertEquals("-NonInteractive", cmd.get(1));
            assertEquals("-Command", cmd.get(2));
            assertEquals("echo hi", cmd.get(3));
        } else {
            assertEquals("sh", cmd.get(0));
            assertEquals("-c", cmd.get(1));
            assertEquals("echo hi", cmd.get(2));
        }
    }

    @Test
    public void getId_returnsShellCommand() {
        assertEquals("shell-command", new ShellCommandAction().getId());
    }

    @Test
    public void getName_returnsShellCommand() {
        assertEquals("Shell Command", new ShellCommandAction().getName());
    }
}
