package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.ImportMavenProjectAction;
import com.example.automation.actions.MavenUpdateProjectAction;
import com.example.automation.api.IActionContext;

public class MavenUpdateProjectActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }

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

    @Test
    public void execute_updatesExistingMavenProject_doesNotThrow() throws Exception {
        String projectName = "maven-update-test";
        IProject existing = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (existing.exists()) existing.delete(true, true, null);

        File dir = tmp.newFolder(projectName);
        String pom =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project>\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.example.test</groupId>\n" +
            "  <artifactId>" + projectName + "</artifactId>\n" +
            "  <version>0.0.1-SNAPSHOT</version>\n" +
            "  <packaging>jar</packaging>\n" +
            "</project>\n";
        Files.writeString(new File(dir, "pom.xml").toPath(), pom, StandardCharsets.UTF_8);
        new ImportMavenProjectAction().execute(
            Map.of("pomPath", dir.getAbsolutePath()), nullCtx());

        try {
            new MavenUpdateProjectAction().execute(
                Map.of("projectName", projectName), nullCtx());
        } finally {
            IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
            if (p.exists()) p.delete(true, true, null);
        }
    }
}
