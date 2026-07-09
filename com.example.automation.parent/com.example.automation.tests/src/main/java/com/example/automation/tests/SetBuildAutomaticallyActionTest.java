package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.Test;

import com.example.automation.actions.SetBuildAutomaticallyAction;

public class SetBuildAutomaticallyActionTest {

    private static com.example.automation.api.IActionContext nullCtx() {
        return new com.example.automation.api.IActionContext() {
            @Override public java.io.OutputStream getOutputStream() { return java.io.OutputStream.nullOutputStream(); }
            @Override public java.io.OutputStream getErrorStream()  { return java.io.OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p) {}
            @Override public boolean isCancelled() { return false; }
        };
    }

    @Test
    public void enableTrue_setsAutoBuilding() throws Exception {
        new SetBuildAutomaticallyAction().execute(Map.of("enabled", "true"), nullCtx());
        assertTrue(ResourcesPlugin.getWorkspace().getDescription().isAutoBuilding());
    }

    @Test
    public void enableFalse_clearsAutoBuilding() throws Exception {
        new SetBuildAutomaticallyAction().execute(Map.of("enabled", "false"), nullCtx());
        assertFalse(ResourcesPlugin.getWorkspace().getDescription().isAutoBuilding());
    }

    @Test
    public void validate_acceptsValidConfig() {
        List<String> errors = new SetBuildAutomaticallyAction().validate(Map.of("enabled", "true"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void getId_returnsSetBuildAutomatically() {
        assertEquals("set-build-automatically", new SetBuildAutomaticallyAction().getId());
    }
}
