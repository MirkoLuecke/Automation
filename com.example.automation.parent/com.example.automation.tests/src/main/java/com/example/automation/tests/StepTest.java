package com.example.automation.tests;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.google.gson.Gson;

public class StepTest {

    @Test
    public void constructor_setsActionId() {
        assertEquals("my.action", new Step("my.action").getActionId());
    }

    @Test
    public void defaultStatus_isWhite() {
        assertEquals(StepStatus.WHITE, new Step("a").getStatus());
    }

    @Test
    public void defaultProgress_isZero() {
        assertEquals(0, new Step("a").getProgress());
    }

    @Test
    public void defaultConfig_isEmptyMap() {
        assertNotNull(new Step("a").getConfig());
        assertTrue(new Step("a").getConfig().isEmpty());
    }

    @Test
    public void setName_getNameRoundTrip() {
        Step step = new Step("a");
        step.setName("My Step");
        assertEquals("My Step", step.getName());
    }

    @Test
    public void setStatus_getStatusRoundTrip() {
        Step step = new Step("a");
        step.setStatus(StepStatus.GREEN);
        assertEquals(StepStatus.GREEN, step.getStatus());
    }

    @Test
    public void setProgress_getProgressRoundTrip() {
        Step step = new Step("a");
        step.setProgress(42);
        assertEquals(42, step.getProgress());
    }

    @Test
    public void setConfig_replacesConfig() {
        Step step = new Step("a");
        Map<String, String> cfg = new HashMap<>();
        cfg.put("key", "val");
        step.setConfig(cfg);
        assertSame(cfg, step.getConfig());
    }

    @Test
    public void bold_defaultsFalse() {
        assertFalse(new Step("a").isBold());
    }

    @Test
    public void bold_setterGetterRoundTrip() {
        Step step = new Step("a");
        step.setBold(true);
        assertTrue(step.isBold());
        step.setBold(false);
        assertFalse(step.isBold());
    }

    @Test
    public void bold_gsonRoundTrip_true() {
        Step step = new Step("a");
        step.setBold(true);
        String json = new Gson().toJson(step);
        Step loaded = new Gson().fromJson(json, Step.class);
        assertTrue(loaded.isBold());
    }

    @Test
    public void bold_gsonRoundTrip_missingFieldDefaultsFalse() {
        // Simulates loading an old workflow JSON that has no "bold" field
        String json = "{\"actionId\":\"a\"}";
        Step loaded = new Gson().fromJson(json, Step.class);
        assertFalse("missing bold field must default to false", loaded.isBold());
    }
}
