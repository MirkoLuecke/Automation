package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.RefreshProjectAction;

public class RefreshProjectActionTest {

    @Test
    public void validate_rejectsBlankProjectName() {
        List<String> errors = new RefreshProjectAction().validate(Map.of("projectName", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankProjectName() {
        List<String> errors = new RefreshProjectAction().validate(Map.of("projectName", "my-project"));
        assertTrue(errors.isEmpty());
    }
}
