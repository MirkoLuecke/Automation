package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.GitCheckoutAction;
import com.example.automation.api.IActionContext;

public class GitCheckoutActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File repo;

    @Before
    public void initRepo() throws Exception {
        repo = tmp.newFolder("repo");
        runGit(repo, "init");
        runGit(repo, "config", "user.email", "test@example.com");
        runGit(repo, "config", "user.name", "Test");
        runGit(repo, "commit", "--allow-empty", "-m", "init");
        runGit(repo, "branch", "feature");
    }

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

    @Test
    public void getId_returnsGitCheckout() {
        assertEquals("git-checkout", new GitCheckoutAction().getId());
    }

    @Test
    public void getName_returnsGitCheckout() {
        assertEquals("Git Checkout", new GitCheckoutAction().getName());
    }

    @Test
    public void execute_checkoutBranch_headPointsToFeature() throws Exception {
        new GitCheckoutAction().execute(
            Map.of("repoDir", repo.getAbsolutePath(), "branch", "feature"),
            nullCtx());
        String head = Files.readString(new File(repo, ".git/HEAD").toPath()).trim();
        assertEquals("ref: refs/heads/feature", head);
    }

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }

    private static void runGit(File dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(dir.getAbsolutePath());
        cmd.addAll(Arrays.asList(args));
        int exit = new ProcessBuilder(cmd).inheritIO().start().waitFor();
        if (exit != 0) throw new Exception("git " + Arrays.toString(args) + " exited with " + exit);
    }
}
