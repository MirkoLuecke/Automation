package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.AddStepDialog;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;

public class AddStepDialogTest {

    private static IAction stubAction(String id, Map<String, String> defaults) {
        return new IAction() {
            @Override public String getId()          { return id; }
            @Override public String getName()        { return id; }
            @Override public String getDescription() { return ""; }
            @Override public Map<String, String> getDefaultConfig() { return defaults; }
            @Override public List<String> validate(Map<String, String> config) { return Collections.emptyList(); }
            @Override public void execute(Map<String, String> config, IActionContext context) {}
        };
    }

    @Test
    public void createStep_setsActionId() {
        IAction action = stubAction("shell-command", Collections.emptyMap());
        Step step = AddStepDialog.createStep(action);
        assertEquals("shell-command", step.getActionId());
    }

    @Test
    public void createStep_copiesDefaultConfig() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("command", "echo hi");
        IAction action = stubAction("shell-command", defaults);
        Step step = AddStepDialog.createStep(action);
        assertEquals("echo hi", step.getConfig().get("command"));
        assertNotSame(defaults, step.getConfig());
    }
}
