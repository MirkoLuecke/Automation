package com.example.automation.tests;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.example.automation.ProjectComboBoxCellEditor;

public class ProjectComboBoxCellEditorTest {

    private static final String PROJECT_NAME = "TestProject_ComboEditor";
    private IProject project;

    @Before
    public void createProject() throws Exception {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
        if (!project.exists()) project.create(new NullProgressMonitor());
        if (!project.isOpen()) project.open(new NullProgressMonitor());
    }

    @After
    public void deleteProject() throws Exception {
        if (project != null && project.exists())
            project.delete(true, new NullProgressMonitor());
    }

    @Test
    public void comboListsOpenWorkspaceProjects() {
        boolean[] found = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            ProjectComboBoxCellEditor editor = new ProjectComboBoxCellEditor(shell);
            editor.setFocus();
            found[0] = Arrays.asList(((Combo) editor.getControl()).getItems())
                             .contains(PROJECT_NAME);
            shell.dispose();
        });
        assertTrue("Open workspace project must appear in combo", found[0]);
    }

    @Test
    public void setValue_getValue_roundTrip() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            ProjectComboBoxCellEditor editor = new ProjectComboBoxCellEditor(shell);
            editor.setValue(PROJECT_NAME);
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals(PROJECT_NAME, result[0]);
    }

    @Test
    public void setValue_textNotInList_stillRoundTrips() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            ProjectComboBoxCellEditor editor = new ProjectComboBoxCellEditor(shell);
            editor.setValue("custom-text-not-in-list");
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals("custom-text-not-in-list", result[0]);
    }

    @Test
    public void comboIsPopulatedWithoutExplicitSetFocus() {
        boolean[] found = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            ProjectComboBoxCellEditor editor = new ProjectComboBoxCellEditor(shell);
            // No setFocus() call — simulates Properties view creating the editor
            // without transferring focus (e.g. when the editor is just created).
            found[0] = Arrays.asList(((Combo) editor.getControl()).getItems())
                             .contains(PROJECT_NAME);
            shell.dispose();
        });
        assertTrue("Combo must list projects even without setFocus()", found[0]);
    }

    @Test
    public void setValueThenSetFocus_preservesCurrentValue() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            ProjectComboBoxCellEditor editor = new ProjectComboBoxCellEditor(shell);
            editor.setValue(PROJECT_NAME);
            editor.setFocus(); // Properties view calls setValue() then setFocus()
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals("setValue() value must survive setFocus()", PROJECT_NAME, result[0]);
    }
}
