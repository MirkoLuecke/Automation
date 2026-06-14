package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.example.automation.actions.RefreshProjectAction;
import com.example.automation.api.IActionContext;

public class RefreshProjectActionTest {

    private IProject project;

    @Before
    public void createProject() throws Exception {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject("RefreshProjectTest");
        if (!project.exists()) project.create(null);
        if (!project.isOpen()) project.open(null);
    }

    @After
    public void deleteProject() throws Exception {
        if (project != null && project.exists()) project.delete(true, null);
    }

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int percent)  {}
            @Override public boolean isCancelled()          { return false; }
        };
    }

    @Test
    public void validate_rejectsBlankProjectName() {
        List<String> errors = new RefreshProjectAction().validate(Map.of("projectName", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankProjectName() {
        List<String> errors = new RefreshProjectAction().validate(Map.of("projectName", "my-project"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void execute_existingProject_doesNotThrow() throws Exception {
        new RefreshProjectAction().execute(Map.of("projectName", "RefreshProjectTest"), nullCtx());
    }

    @Test(expected = IllegalArgumentException.class)
    public void execute_blankProjectName_throwsIllegalArgument() throws Exception {
        new RefreshProjectAction().execute(Map.of("projectName", ""), nullCtx());
    }
}
