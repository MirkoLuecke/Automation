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
}
