package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.example.automation.PathCellEditor;
import com.example.automation.model.Step;

public class PathCellEditorTest {

    private static final String PROJECT_NAME = "TestProject_PathCellEditor";
    private IProject testProject;

    @Before
    public void createProject() throws Exception {
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        testProject = ws.getRoot().getProject(PROJECT_NAME);
        if (!testProject.exists())
            testProject.create(new NullProgressMonitor());
        if (!testProject.isOpen())
            testProject.open(new NullProgressMonitor());
    }

    @After
    public void deleteProject() throws Exception {
        if (testProject != null && testProject.exists())
            testProject.delete(true, new NullProgressMonitor());
    }

    private Step stepWith(String projectName) {
        Step step = new Step("test");
        if (projectName != null) step.getConfig().put("projectName", projectName);
        return step;
    }

    @Test
    public void relativize_underProject_usesWorkspaceLocProjectVar() {
        String projectPath = testProject.getLocation().toOSString();
        String filePath = projectPath + File.separator + "src" + File.separator + "formatter.xml";

        String result = PathCellEditor.relativize(filePath, stepWith(PROJECT_NAME));

        assertEquals("${workspace_loc:/" + PROJECT_NAME + "}/src/formatter.xml", result);
    }

    @Test
    public void relativize_underWorkspace_usesWorkspaceLoc() {
        String wsPath = ResourcesPlugin.getWorkspace().getRoot()
            .getLocation().toOSString();
        String filePath = wsPath + File.separator + "OtherProject"
            + File.separator + "settings.xml";

        String result = PathCellEditor.relativize(filePath, stepWith(PROJECT_NAME));

        assertEquals("${workspace_loc}/OtherProject/settings.xml", result);
    }

    @Test
    public void relativize_outsideWorkspace_returnsAbsolute() {
        String wsPath = ResourcesPlugin.getWorkspace().getRoot()
            .getLocation().toOSString();
        java.nio.file.Path outside = java.nio.file.Paths.get(wsPath).getParent();
        if (outside == null) return;
        String filePath = outside.resolve("outside_file.xml").toString();

        String result = PathCellEditor.relativize(filePath, stepWith(PROJECT_NAME));

        assertEquals(filePath, result);
    }

    @Test
    public void relativize_noProjectName_usesWorkspaceLoc() {
        String wsPath = ResourcesPlugin.getWorkspace().getRoot()
            .getLocation().toOSString();
        String filePath = wsPath + File.separator + "sub" + File.separator + "file.xml";

        String result = PathCellEditor.relativize(filePath, stepWith(null));

        assertEquals("${workspace_loc}/sub/file.xml", result);
    }
}
