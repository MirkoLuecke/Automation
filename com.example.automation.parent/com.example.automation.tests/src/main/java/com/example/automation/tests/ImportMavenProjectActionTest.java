package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.ImportMavenProjectAction;
import com.example.automation.api.IActionContext;

public class ImportMavenProjectActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

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

    private static IActionContext stubContext() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return System.out; }
            @Override public OutputStream getErrorStream()  { return System.err; }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }
}
