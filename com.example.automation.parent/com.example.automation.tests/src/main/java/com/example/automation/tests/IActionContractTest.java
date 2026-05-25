package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class IActionContractTest {

    private static IActionContext fakeContext(boolean cancelled) {
        return new IActionContext() {
            public void setProgress(int p) {}
            public boolean isCancelled() { return cancelled; }
            public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            public OutputStream getErrorStream() { return OutputStream.nullOutputStream(); }
        };
    }

    private static final IAction VALID_ACTION = new IAction() {
        public String getId() { return "test.id"; }
        public String getName() { return "Test"; }
        public String getDescription() { return "A test action"; }
        public Map<String, String> getDefaultConfig() { return Map.of("key", "value"); }
        public List<String> validate(Map<String, String> cfg) {
            return cfg.containsKey("key") ? Collections.emptyList() : List.of("key is required");
        }
        public void execute(Map<String, String> cfg, IActionContext ctx) throws Exception {
            ctx.setProgress(50);
        }
    };

    @Test
    public void defaultConfigNotNull() {
        assertNotNull(VALID_ACTION.getDefaultConfig());
    }

    @Test
    public void validateEmptyListForValidConfig() {
        List<String> errors = VALID_ACTION.validate(Map.of("key", "value"));
        assertTrue("Expected no errors for valid config", errors.isEmpty());
    }

    @Test
    public void validateNonEmptyForInvalidConfig() {
        List<String> errors = VALID_ACTION.validate(Collections.emptyMap());
        assertFalse("Expected errors for invalid config", errors.isEmpty());
    }

    @Test
    public void executeChecksIsCancelled() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        IAction action = new IAction() {
            public String getId() { return "cancel-test"; }
            public String getName() { return "Cancel Test"; }
            public String getDescription() { return "Tests cancellation"; }
            public Map<String, String> getDefaultConfig() { return Collections.emptyMap(); }
            public List<String> validate(Map<String, String> cfg) { return Collections.emptyList(); }
            public void execute(Map<String, String> cfg, IActionContext ctx) throws Exception {
                if (ctx.isCancelled()) return;
                executed.set(true);
            }
        };
        action.execute(Collections.emptyMap(), fakeContext(true));
        assertFalse("execute() should return early when cancelled", executed.get());
    }

    @Test
    public void executeReportsProgress() throws Exception {
        AtomicBoolean progressReported = new AtomicBoolean(false);
        IAction action = new IAction() {
            public String getId() { return "progress-test"; }
            public String getName() { return "Progress Test"; }
            public String getDescription() { return "Tests progress reporting"; }
            public Map<String, String> getDefaultConfig() { return Collections.emptyMap(); }
            public List<String> validate(Map<String, String> cfg) { return Collections.emptyList(); }
            public void execute(Map<String, String> cfg, IActionContext ctx) throws Exception {
                ctx.setProgress(100);
            }
        };
        IActionContext tracking = new IActionContext() {
            public void setProgress(int p) { progressReported.set(true); }
            public boolean isCancelled() { return false; }
            public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            public OutputStream getErrorStream() { return OutputStream.nullOutputStream(); }
        };
        action.execute(Collections.emptyMap(), tracking);
        assertTrue("execute() must call setProgress() at least once", progressReported.get());
    }
}
