package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.eclipse.ui.views.properties.ComboBoxPropertyDescriptor;
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

        // action + name + bold = 3 (no config keys for this action)
        assertEquals(3, descs.length);
        // Find action descriptor
        IPropertyDescriptor actionDesc = null;
        for (IPropertyDescriptor d : descs) {
            if ("action".equals(d.getId())) { actionDesc = d; break; }
        }
        assertNotNull(actionDesc);
        assertFalse("action descriptor must not be a TextPropertyDescriptor",
            actionDesc instanceof TextPropertyDescriptor);
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

        // action + name + bold + "foo" = 4
        assertEquals(4, descs.length);
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

    @Test
    public void nameProperty_isTextDescriptor_inStepCategory() {
        Step step = new Step("my.action");
        ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
        boolean[] saved = {false};
        StepPropertySource s = src(step, reg, saved);

        IPropertyDescriptor nameDesc = null;
        for (IPropertyDescriptor d : s.getPropertyDescriptors()) {
            if ("name".equals(d.getId())) { nameDesc = d; break; }
        }
        assertNotNull("name property must exist", nameDesc);
        assertTrue("name must use TextPropertyDescriptor", nameDesc instanceof TextPropertyDescriptor);
    }

    @Test
    public void nameProperty_setAndGet() {
        Step step = new Step("my.action");
        ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
        boolean[] saved = {false};
        StepPropertySource s = src(step, reg, saved);

        s.setPropertyValue("name", "My Custom Name");

        assertEquals("My Custom Name", step.getName());
        assertTrue(saved[0]);
        assertEquals("My Custom Name", s.getPropertyValue("name"));
    }

    @Test
    public void nameProperty_setBlank_setsNull() {
        Step step = new Step("my.action");
        step.setName("Existing");
        ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
        boolean[] saved = {false};
        StepPropertySource s = src(step, reg, saved);

        s.setPropertyValue("name", "   ");

        assertNull("blank name must reset to null", step.getName());
        assertTrue(saved[0]);
    }

    @Test
    public void nameProperty_reset_setsNull() {
        Step step = new Step("my.action");
        step.setName("Custom");
        ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
        boolean[] saved = {false};
        StepPropertySource s = src(step, reg, saved);

        s.resetPropertyValue("name");

        assertNull(step.getName());
        assertTrue(saved[0]);
    }

    @Test
    public void boldProperty_isComboDescriptor_defaultNo() {
        Step step = new Step("my.action");
        ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
        boolean[] saved = {false};
        StepPropertySource s = src(step, reg, saved);

        IPropertyDescriptor boldDesc = null;
        for (IPropertyDescriptor d : s.getPropertyDescriptors()) {
            if ("bold".equals(d.getId())) { boldDesc = d; break; }
        }
        assertNotNull("bold property must exist", boldDesc);
        assertTrue("bold must use ComboBoxPropertyDescriptor",
            boldDesc instanceof ComboBoxPropertyDescriptor);
        assertEquals(0, s.getPropertyValue("bold")); // 0 = No
    }

    @Test
    public void boldProperty_setYes_setsTrue() {
        Step step = new Step("my.action");
        ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
        boolean[] saved = {false};
        StepPropertySource s = src(step, reg, saved);

        s.setPropertyValue("bold", 1); // 1 = Yes

        assertTrue(step.isBold());
        assertTrue(saved[0]);
        assertEquals(1, s.getPropertyValue("bold"));
    }

    @Test
    public void boldProperty_setNo_setsFalse() {
        Step step = new Step("my.action");
        step.setBold(true);
        ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
        boolean[] saved = {false};
        StepPropertySource s = src(step, reg, saved);

        s.setPropertyValue("bold", 0); // 0 = No

        assertFalse(step.isBold());
        assertTrue(saved[0]);
    }

    @Test
    public void boldProperty_reset_setsFalse() {
        Step step = new Step("my.action");
        step.setBold(true);
        ActionRegistry reg = new ActionRegistry(List.of(stub("my.action", Map.of())));
        boolean[] saved = {false};
        StepPropertySource s = src(step, reg, saved);

        s.resetPropertyValue("bold");

        assertFalse(step.isBold());
        assertTrue(saved[0]);
    }

    @Test
    public void resetDirField_withBlankDefault_usesWorkspaceParent() {
        // workingDir has a blank default — reset should give workspace parent
        IAction action = stub("shell-command", Map.of("command", "", "workingDir", ""));
        Step step = new Step("shell-command");
        step.getConfig().put("workingDir", "/some/old/path");
        ActionRegistry reg = new ActionRegistry(List.of(action));
        boolean[] saved = {false};
        StepPropertySource s = src(step, reg, saved);

        s.resetPropertyValue("workingDir");

        String wsParent = com.example.automation.StepOperations.workspaceParent();
        if (wsParent != null) {
            assertEquals("reset must apply workspace parent for blank-default dir field",
                wsParent, step.getConfig().get("workingDir"));
        }
        assertTrue(saved[0]);
    }
}
