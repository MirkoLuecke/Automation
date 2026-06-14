package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.MavenUpdateProjectAction;

public class MavenUpdateProjectActionTest {

    @Test
    public void validate_blankProjectName_alwaysRejected() {
        List<String> errors = new MavenUpdateProjectAction().validate(Map.of("projectName", ""));
        assertTrue(errors.stream().anyMatch(e -> e.contains("projectName")));
    }

    @Test
    public void defaultConfig_containsProjectNameKey() {
        assertTrue(new MavenUpdateProjectAction().getDefaultConfig().containsKey("projectName"));
    }

    @Test
    public void getId_returnsMavenUpdateProject() {
        assertEquals("maven-update-project", new MavenUpdateProjectAction().getId());
    }

    @Test
    public void getName_returnsMavenUpdateProject() {
        assertEquals("Maven Update Project", new MavenUpdateProjectAction().getName());
    }

    @Test
    public void validate_acceptsNonBlankProjectName() {
        // M2E may or may not be active; only check there is no "projectName" error
        boolean projectNameError = new MavenUpdateProjectAction()
            .validate(java.util.Map.of("projectName", "MyProject"))
            .stream().anyMatch(e -> e.contains("projectName"));
        assertFalse(projectNameError);
    }
}
