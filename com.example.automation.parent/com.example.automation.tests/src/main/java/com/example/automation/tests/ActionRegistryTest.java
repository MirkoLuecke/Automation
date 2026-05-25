package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.OutputStream;
import java.util.*;
import org.junit.Test;
import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class ActionRegistryTest {

    private static IAction stub(String id) {
        return new IAction() {
            public String getId() { return id; }
            public String getName() { return "name"; }
            public String getDescription() { return "desc"; }
            public Map<String, String> getDefaultConfig() { return Collections.emptyMap(); }
            public List<String> validate(Map<String, String> cfg) { return Collections.emptyList(); }
            public void execute(Map<String, String> cfg, IActionContext ctx) {}
        };
    }

    @Test
    public void getAllActionsReturnsBoth() {
        IAction a = stub("a"), b = stub("b");
        ActionRegistry r = new ActionRegistry(Arrays.asList(a, b));
        List<IAction> all = r.getAllActions();
        assertEquals(2, all.size());
        assertTrue(all.contains(a));
        assertTrue(all.contains(b));
    }

    @Test
    public void getActionFindsById() {
        IAction a = stub("known-id");
        ActionRegistry r = new ActionRegistry(Arrays.asList(a));
        assertSame(a, r.getAction("known-id"));
    }

    @Test
    public void getActionReturnsNullForUnknown() {
        ActionRegistry r = new ActionRegistry(Collections.emptyList());
        assertNull(r.getAction("missing"));
    }

    @Test
    public void duplicateIdLastWins() {
        IAction first = stub("dup"), last = stub("dup");
        ActionRegistry r = new ActionRegistry(Arrays.asList(first, last));
        assertSame(last, r.getAction("dup"));
    }
}
