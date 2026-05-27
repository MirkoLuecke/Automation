package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.GitCheckoutAction;

public class GitCheckoutActionTest {

    @Test
    public void validate_rejectsBlankRepoDir() {
        List<String> errors = new GitCheckoutAction().validate(
            Map.of("repoDir", "", "branch", "main"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_rejectsBlankBranch() {
        List<String> errors = new GitCheckoutAction().validate(
            Map.of("repoDir", "/tmp/repo", "branch", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsRepoDirAndBranch() {
        List<String> errors = new GitCheckoutAction().validate(
            Map.of("repoDir", "/tmp/repo", "branch", "main"));
        assertTrue(errors.isEmpty());
    }
}
