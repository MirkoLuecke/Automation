package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.RefreshAllAction;

public class RefreshAllActionTest {

    @Test
    public void defaultConfig_isEmpty() {
        assertTrue(new RefreshAllAction().getDefaultConfig().isEmpty());
    }

    @Test
    public void validate_alwaysReturnsEmpty() {
        assertTrue(new RefreshAllAction().validate(Map.of()).isEmpty());
    }
}
