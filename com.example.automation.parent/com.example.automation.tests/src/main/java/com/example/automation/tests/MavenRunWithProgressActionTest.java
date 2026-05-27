package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.MavenRunWithProgressAction;

public class MavenRunWithProgressActionTest {

    @Test
    public void validate_rejectsBlankConfigName() {
        List<String> errors = new MavenRunWithProgressAction().validate(Map.of("configName", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankConfigName() {
        List<String> errors = new MavenRunWithProgressAction().validate(
            Map.of("configName", "My Maven Build"));
        assertTrue(errors.isEmpty());
    }
}
