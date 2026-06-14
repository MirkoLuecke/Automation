package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.MavenProgressParser;
import com.example.automation.actions.MavenRunWithProgressAction;
import com.example.automation.api.IActionContext;

public class MavenRunWithProgressActionTest {

    private static final String ESC = "";

    @Test
    public void validate_rejectsBlankGoals() {
        List<String> errors = new MavenRunWithProgressAction().validate(Map.of("goals", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankGoals() {
        List<String> errors = new MavenRunWithProgressAction().validate(
            Map.of("goals", "clean install"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_containsGoalsAndWorkingDirKeys() {
        Map<String, String> cfg = new MavenRunWithProgressAction().getDefaultConfig();
        assertTrue(cfg.containsKey("goals"));
        assertTrue(cfg.containsKey("workingDir"));
    }

    // --- new tests ---

    @Test
    public void processOutputStream_stripsAnsiAndReportsProgress() throws Exception {
        List<Integer> seen = new ArrayList<>();
        // Simulates Maven/Jansi ANSI output: ESC[1m[INFO]ESC[0m Building foo 1.0.0 ESC[1m[2/5]ESC[0m
        // After ANSI stripping: "[INFO] Building foo 1.0.0 [2/5]"
        // MavenProgressParser matches [2/5] -> (2-1)*100/5 = 20
        String line = ESC + "[1m[INFO]" + ESC + "[0m Building foo 1.0.0 " + ESC + "[1m[2/5]" + ESC + "[0m";
        new MavenRunWithProgressAction().processOutputStream(
            asStream(line), stubContext(seen), new MavenProgressParser(), new boolean[]{false});
        assertEquals(List.of(20), seen);
    }

    @Test
    public void processOutputStream_detectsBuildFailureThroughAnsi() throws Exception {
        boolean[] failed = {false};
        // Simulates ANSI-coloured failure line: ESC[31m[INFO] BUILD FAILUREESCm[0m
        String line = ESC + "[31m[INFO] BUILD FAILURE" + ESC + "[0m";
        new MavenRunWithProgressAction().processOutputStream(
            asStream(line), stubContext(new ArrayList<>()), new MavenProgressParser(), failed);
        assertTrue(failed[0]);
    }

    // helpers

    private static InputStream asStream(String text) {
        return new ByteArrayInputStream((text + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static IActionContext stubContext(List<Integer> progress) {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        { progress.add(p); }
            @Override public boolean isCancelled()          { return false; }
        };
    }

    @Test
    public void getId_returnsMavenRunWithProgress() {
        assertEquals("maven-run-with-progress", new MavenRunWithProgressAction().getId());
    }

    @Test
    public void getName_returnsMavenRunWithProgress() {
        assertEquals("Maven Run with Progress", new MavenRunWithProgressAction().getName());
    }
}
