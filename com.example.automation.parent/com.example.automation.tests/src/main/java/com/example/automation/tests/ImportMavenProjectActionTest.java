package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.ImportMavenProjectAction;
import com.example.automation.api.IActionContext;

public class ImportMavenProjectActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void cleanupWorkspaceBeforeTest() throws Exception {
        cleanupWorkspace();
    }

    @After
    public void cleanupWorkspace() throws Exception {
        IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject("test-import");
        if (p.exists()) p.delete(true, true, null);
    }

    @Test
    public void validate_blankPomPath_alwaysRejected() {
        List<String> errors = new ImportMavenProjectAction().validate(Map.of("pomPath", ""));
        assertTrue(errors.stream().anyMatch(e -> e.contains("pomPath")));
    }

    @Test
    public void defaultConfig_containsPomPathKey() {
        assertTrue(new ImportMavenProjectAction().getDefaultConfig().containsKey("pomPath"));
    }

    @Test
    public void execute_givesJavaNatureToImportedProject() throws Exception {
        File dir = tmp.newFolder("test-import");
        try (InputStream src = ImportMavenProjectActionTest.class
                .getResourceAsStream("/test-import-pom.xml")) {
            assertNotNull("test-import-pom.xml must be on classpath", src);
            Files.copy(src, new File(dir, "pom.xml").toPath());
        }

        new ImportMavenProjectAction().execute(
            Map.of("pomPath", dir.getAbsolutePath()), stubContext());

        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("test-import");
        assertTrue("project must exist in workspace", project.exists());
        assertTrue("project must be open", project.isOpen());
        assertTrue("project must have Java nature",
            project.hasNature("org.eclipse.jdt.core.javanature"));
    }

    @Test
    public void execute_reportsMonotonicProgress() throws Exception {
        File dir = tmp.newFolder("test-import-progress");
        try (InputStream src = ImportMavenProjectActionTest.class
                .getResourceAsStream("/test-import-pom.xml")) {
            assertNotNull("test-import-pom.xml must be on classpath", src);
            Files.copy(src, new File(dir, "pom.xml").toPath());
        }

        List<Integer> progressValues = new ArrayList<>();
        IActionContext trackingContext = new IActionContext() {
            @Override public OutputStream getOutputStream() { return System.out; }
            @Override public OutputStream getErrorStream()  { return System.err; }
            @Override public void setProgress(int p)        { progressValues.add(p); }
            @Override public boolean isCancelled()          { return false; }
        };

        new ImportMavenProjectAction().execute(
            Map.of("pomPath", dir.getAbsolutePath()), trackingContext);

        assertFalse("setProgress must be called at least once", progressValues.isEmpty());
        assertEquals("last setProgress call must be 100", 100,
            (int) progressValues.get(progressValues.size() - 1));
        assertTrue("importProjects bridge must emit intermediate values (not just 0 and 100)",
            progressValues.size() >= 3);
        for (int i = 1; i < progressValues.size(); i++)
            assertTrue("progress must never decrease: " + progressValues,
                progressValues.get(i) >= progressValues.get(i - 1));
    }

    private static IActionContext stubContext() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return System.out; }
            @Override public OutputStream getErrorStream()  { return System.err; }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }
}
