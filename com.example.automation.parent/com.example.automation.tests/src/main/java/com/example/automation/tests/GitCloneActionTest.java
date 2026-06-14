package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.GitCloneAction;
import com.example.automation.api.IActionContext;

public class GitCloneActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File source;

    @Before
    public void createBareRepo() throws Exception {
        source = tmp.newFolder("source.git");
        runGit(source, "init", "--bare");
    }

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

    @Test
    public void execute_clonesLocalRepo_targetDirHasGitDirectory() throws Exception {
        File target = new File(tmp.getRoot(), "clone");
        String url = source.toURI().toString();
        // Fix Windows file:/ URIs to proper file:/// format for git compatibility
        if (url.startsWith("file:/") && !url.startsWith("file:///")) {
            url = "file:///" + url.substring(6);
        }
        new GitCloneAction().execute(
            Map.of("url", url,
                   "targetDir", target.getAbsolutePath(),
                   "branch", ""),
            nullCtx());
        assertTrue(".git directory must exist in cloned repo",
            new File(target, ".git").isDirectory());
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
