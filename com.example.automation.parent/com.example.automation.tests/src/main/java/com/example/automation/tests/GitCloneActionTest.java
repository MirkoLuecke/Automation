package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.GitCloneAction;

public class GitCloneActionTest {

    @Test
    public void validate_rejectsBlankUrl() {
        List<String> errors = new GitCloneAction().validate(
            Map.of("url", "", "targetDir", "/tmp/repo"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_rejectsBlankTargetDir() {
        List<String> errors = new GitCloneAction().validate(
            Map.of("url", "https://example.com/repo.git", "targetDir", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsUrlAndTargetDir() {
        List<String> errors = new GitCloneAction().validate(
            Map.of("url", "https://example.com/repo.git", "targetDir", "/tmp/repo"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void getId_returnsGitClone() {
        assertEquals("git-clone", new GitCloneAction().getId());
    }

    @Test
    public void getName_returnsGitClone() {
        assertEquals("Git Clone", new GitCloneAction().getName());
    }
}
