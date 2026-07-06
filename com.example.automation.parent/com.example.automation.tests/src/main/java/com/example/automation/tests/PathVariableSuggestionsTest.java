package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.example.automation.PathVariableSuggestions;
import com.example.automation.PathVariableSuggestions.Suggestion;

public class PathVariableSuggestionsTest {

    private static final String PROJECT_NAME = "TestProject_PathVarSugg";
    private IProject testProject;
    private IStringVariableManager mgr;

    @Before
    public void setUp() throws Exception {
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        testProject = ws.getRoot().getProject(PROJECT_NAME);
        if (!testProject.exists()) testProject.create(new NullProgressMonitor());
        if (!testProject.isOpen()) testProject.open(new NullProgressMonitor());
        mgr = VariablesPlugin.getDefault().getStringVariableManager();
    }

    @After
    public void tearDown() throws Exception {
        if (testProject != null && testProject.exists())
            testProject.delete(true, new NullProgressMonitor());
    }

    private String projectAbsPath() {
        return testProject.getLocation().toFile().getAbsolutePath();
    }

    private String wsAbsPath() {
        return ResourcesPlugin.getWorkspace().getRoot().getLocation()
            .toFile().getAbsolutePath();
    }

    @Test
    public void compute_pathUnderProject_returnsProjectVariableFirst() {
        String path = projectAbsPath() + File.separator + "src" + File.separator + "main";
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            path, new IProject[]{testProject}, mgr);
        assertFalse("Expected at least one suggestion", suggestions.isEmpty());
        assertEquals("${workspace_loc:/" + PROJECT_NAME + "}/src/main",
            suggestions.get(0).variableForm);
    }

    @Test
    public void compute_pathExactlyProject_returnsVariableWithNoSuffix() {
        String path = projectAbsPath();
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            path, new IProject[]{testProject}, mgr);
        assertFalse(suggestions.isEmpty());
        assertEquals("${workspace_loc:/" + PROJECT_NAME + "}",
            suggestions.get(0).variableForm);
    }

    @Test
    public void compute_pathUnderWorkspace_notUnderProject_returnsWorkspaceLocVar() {
        String path = wsAbsPath() + File.separator + "SomeFolder" + File.separator + "f.xml";
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            path, new IProject[0], mgr);
        assertTrue("Expected ${workspace_loc} suggestion",
            suggestions.stream().anyMatch(
                s -> s.variableForm.equals("${workspace_loc}/SomeFolder/f.xml")));
    }

    @Test
    public void compute_pathOutsideWorkspace_noWorkspaceLocSuggestion() {
        java.nio.file.Path outside = java.nio.file.Paths.get(wsAbsPath()).getParent();
        if (outside == null) return; // workspace is at filesystem root, skip
        String path = outside.resolve("outside.xml").toString();
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            path, new IProject[0], mgr);
        assertTrue("No workspace_loc suggestion expected for path outside workspace",
            suggestions.stream().noneMatch(
                s -> s.variableForm.startsWith("${workspace_loc}")));
    }

    @Test
    public void compute_absoluteAlwaysLast() {
        String path = projectAbsPath() + File.separator + "build.xml";
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            path, new IProject[]{testProject}, mgr);
        assertTrue(suggestions.size() >= 2);
        Suggestion last = suggestions.get(suggestions.size() - 1);
        assertFalse("Last suggestion must not be a variable form",
            last.variableForm.contains("${"));
    }

    @Test
    public void compute_emptyPath_returnsEmptyList() {
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            "", new IProject[0], mgr);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    public void compute_nullPath_returnsEmptyList() {
        List<Suggestion> suggestions = PathVariableSuggestions.compute(
            null, new IProject[0], mgr);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    public void compute_closedProject_notIncluded() {
        try {
            testProject.close(new NullProgressMonitor());
            String path = projectAbsPath() + File.separator + "pom.xml";
            List<Suggestion> suggestions = PathVariableSuggestions.compute(
                path, new IProject[]{testProject}, mgr);
            assertTrue("Closed project must not produce a suggestion",
                suggestions.stream().noneMatch(
                    s -> s.variableForm.contains(PROJECT_NAME)));
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }
}
