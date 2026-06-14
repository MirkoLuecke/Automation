package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.ExecuteRunConfigAction;

public class ExecuteRunConfigActionTest {

    @Test
    public void validate_rejectsBlankConfigName() {
        List<String> errors = new ExecuteRunConfigAction().validate(Map.of("configName", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankConfigName() {
        List<String> errors = new ExecuteRunConfigAction().validate(Map.of("configName", "My Build"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_modeIsRun() {
        assertEquals("run", new ExecuteRunConfigAction().getDefaultConfig().get("mode"));
    }

    @Test
    public void getId_returnsExecuteRunConfig() {
        assertEquals("execute-run-config", new ExecuteRunConfigAction().getId());
    }

    @Test
    public void getName_returnsExecuteRunConfig() {
        assertEquals("Execute Run Configuration", new ExecuteRunConfigAction().getName());
    }
}
