package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.TextPropertyDescriptor;
import org.junit.Test;

import com.example.automation.StepPropertySource;
import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;

public class StepPropertySourceTest {

    private static IAction stub(String id, Map<String, String> defaults) {
        return new IAction() {
            @Override public String getId()          { return id; }
            @Override public String getName()        { return id; }
            @Override public String getDescription() { return ""; }
            @Override public Map<String, String> getDefaultConfig() { return defaults; }
            @Override public List<String> validate(Map<String, String> c) { return List.of(); }
            @Override public void execute(Map<String, String> c, IActionContext ctx) {}
        };
    }

    private StepPropertySource src(Step step, ActionRegistry reg, boolean[] saved) {
        return new StepPropertySource(step, reg, () -> saved[0] = true);
    }

    @Test
    public void actionProperty_isReadOnly() {
        Step step = new Step("my.action");
        ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
        boolean[] saved = {false};

        StepPropertySource s = src(step, reg, saved);
        IPropertyDescriptor[] descs = s.getPropertyDescriptors();

        assertEquals(1, descs.length);
        assertEquals("action", descs[0].getId());
        assertFalse("action descriptor must not be a TextPropertyDescriptor",
            descs[0] instanceof TextPropertyDescriptor);
        assertEquals("my.action", s.getPropertyValue("action"));
        assertFalse(saved[0]);
    }

    @Test
    public void configProperty_returnsCurrentValue() {
        Step step = new Step("my.action");
        step.getConfig().put("timeout", "30");
        ActionRegistry reg = new ActionRegistry(List.of(
            stub("my.action", Map.of("timeout", "10"))));
        boolean[] saved = {false};

        assertEquals("30", src(step, reg, saved).getPropertyValue("timeout"));
        assertFalse(saved[0]);
    }

    @Test
    public void setPropertyValue_updatesConfigAndSaves() {
        Step step = new Step("my.action");
        ActionRegistry reg = new ActionRegistry(List.of(
            stub("my.action", Map.of("timeout", "10"))));
        boolean[] saved = {false};

        src(step, reg, saved).setPropertyValue("timeout", "60");

        assertEquals("60", step.getConfig().get("timeout"));
        assertTrue(saved[0]);
    }

    @Test
    public void resetToDefault_restoresDefaultAndSaves() {
        Step step = new Step("my.action");
        step.getConfig().put("timeout", "99");
        ActionRegistry reg = new ActionRegistry(List.of(
            stub("my.action", Map.of("timeout", "10"))));
        boolean[] saved = {false};

        src(step, reg, saved).resetPropertyValue("timeout");

        assertEquals("10", step.getConfig().get("timeout"));
        assertTrue(saved[0]);
    }

    @Test
    public void unknownAction_fallsBackToExistingConfig() {
        Step step = new Step("unknown");
        step.getConfig().put("foo", "bar");
        ActionRegistry reg = new ActionRegistry(List.of());
        boolean[] saved = {false};

        IPropertyDescriptor[] descs = src(step, reg, saved).getPropertyDescriptors();

        // "action" + "foo"
        assertEquals(2, descs.length);
        boolean foundFoo = false;
        for (IPropertyDescriptor d : descs) {
            if ("foo".equals(d.getId())) {
                assertTrue(d instanceof TextPropertyDescriptor);
                foundFoo = true;
            }
        }
        assertTrue("foo key must appear as an editable TextPropertyDescriptor", foundFoo);
    }

    @Test
    public void isPropertySet_falseWhenValueEqualsDefault() {
        Step step = new Step("my.action");
        step.getConfig().put("timeout", "10"); // same as default
        ActionRegistry reg = new ActionRegistry(List.of(
            stub("my.action", Map.of("timeout", "10"))));
        boolean[] saved = {false};

        assertFalse(src(step, reg, saved).isPropertySet("timeout"));
    }

    @Test
    public void isPropertySet_trueWhenValueDiffersFromDefault() {
        Step step = new Step("my.action");
        step.getConfig().put("timeout", "99"); // different from default
        ActionRegistry reg = new ActionRegistry(List.of(
            stub("my.action", Map.of("timeout", "10"))));
        boolean[] saved = {false};

        assertTrue(src(step, reg, saved).isPropertySet("timeout"));
    }

    @Test
    public void projectName_usesCustomDescriptor() {
        IAction refreshProject = stub("refresh-project", Map.of("projectName", ""));
        Step step = new Step("refresh-project");
        ActionRegistry reg = new ActionRegistry(List.of(refreshProject));
        boolean[] saved = {false};

        IPropertyDescriptor[] descs = src(step, reg, saved).getPropertyDescriptors();

        IPropertyDescriptor projectNameDesc = null;
        for (IPropertyDescriptor d : descs) {
            if ("projectName".equals(d.getId())) {
                projectNameDesc = d;
                break;
            }
        }
        assertNotNull("projectName descriptor must exist", projectNameDesc);
        assertFalse("projectName must not use TextPropertyDescriptor",
            projectNameDesc instanceof TextPropertyDescriptor);
    }

    @Test
    public void filePath_usesPathCellEditor() {
        IAction action = stub("set-code-formatter", Map.of("filePath", ""));
        Step step = new Step("set-code-formatter");
        ActionRegistry reg = new ActionRegistry(List.of(action));
        boolean[] saved = {false};

        IPropertyDescriptor[] descs = src(step, reg, saved).getPropertyDescriptors();

        IPropertyDescriptor filePathDesc = null;
        for (IPropertyDescriptor d : descs) {
            if ("filePath".equals(d.getId())) { filePathDesc = d; break; }
        }
        assertNotNull("filePath descriptor must exist", filePathDesc);
        assertFalse("filePath must not use TextPropertyDescriptor",
            filePathDesc instanceof TextPropertyDescriptor);
    }

    @Test
    public void workingDir_usesPathCellEditor() {
        IAction action = stub("shell-command", Map.of("command", "", "workingDir", ""));
        Step step = new Step("shell-command");
        ActionRegistry reg = new ActionRegistry(List.of(action));
        boolean[] saved = {false};

        IPropertyDescriptor[] descs = src(step, reg, saved).getPropertyDescriptors();

        IPropertyDescriptor workingDirDesc = null;
        for (IPropertyDescriptor d : descs) {
            if ("workingDir".equals(d.getId())) { workingDirDesc = d; break; }
        }
        assertNotNull("workingDir descriptor must exist", workingDirDesc);
        assertFalse("workingDir must not use TextPropertyDescriptor",
            workingDirDesc instanceof TextPropertyDescriptor);
    }
}
