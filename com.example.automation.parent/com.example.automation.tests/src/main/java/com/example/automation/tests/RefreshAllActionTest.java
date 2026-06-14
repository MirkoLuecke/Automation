package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.OutputStream;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.RefreshAllAction;
import com.example.automation.api.IActionContext;

public class RefreshAllActionTest {

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int percent)  {}
            @Override public boolean isCancelled()          { return false; }
        };
    }

    @Test
    public void defaultConfig_isEmpty() {
        assertTrue(new RefreshAllAction().getDefaultConfig().isEmpty());
    }

    @Test
    public void validate_alwaysReturnsEmpty() {
        assertTrue(new RefreshAllAction().validate(Map.of()).isEmpty());
    }

    @Test
    public void getId_returnsExpectedId() {
        assertEquals("refresh-all", new RefreshAllAction().getId());
    }

    @Test
    public void getName_returnsExpectedName() {
        assertEquals("Refresh All", new RefreshAllAction().getName());
    }

    @Test
    public void execute_doesNotThrow() throws Exception {
        new RefreshAllAction().execute(Map.of(), nullCtx());
    }
}
