package com.example.automation.tests;

import static org.junit.Assert.*;
import org.junit.Test;
import com.example.automation.StepLabelProvider;
import com.example.automation.model.Step;

public class StepLabelProviderTest {

    @Test
    public void Config_emptyConfig_returnsEmpty() {
        assertEquals("", new StepLabelProvider.Config().getText(new Step("a")));
    }

    @Test
    public void Config_singleEntry_returnsKeyEqualsValue() {
        Step step = new Step("a");
        step.getConfig().put("cmd", "echo hi");
        assertEquals("cmd=echo hi", new StepLabelProvider.Config().getText(step));
    }

    @Test
    public void Config_multipleEntries_sortedAlphabeticallyByKey() {
        Step step = new Step("a");
        step.getConfig().put("z", "1");
        step.getConfig().put("a", "2");
        assertEquals("a=2, z=1", new StepLabelProvider.Config().getText(step));
    }

    @Test
    public void Config_longText_truncatedAt80WithEllipsis() {
        Step step = new Step("a");
        step.getConfig().put("key", "x".repeat(100));
        String result = new StepLabelProvider.Config().getText(step);
        assertEquals(80, result.length());
        assertTrue(result.endsWith("..."));
    }

    @Test
    public void Name_customNameSet_returnsCustomName() {
        Step step = new Step("nonexistent.xyz");
        step.setName("My Custom Step");
        assertEquals("My Custom Step", new StepLabelProvider.Name().getText(step));
    }

    @Test
    public void Name_noCustomName_unknownAction_returnsActionId() {
        Step step = new Step("nonexistent.action.xyz.abc.123");
        // No name set, action not in registry → falls back to actionId
        assertEquals("nonexistent.action.xyz.abc.123",
            new StepLabelProvider.Name().getText(step));
    }
}
