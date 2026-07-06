package com.example.automation.tests;

import static org.junit.Assert.*;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.Test;

import com.example.automation.PathCellEditor.PathType;
import com.example.automation.PathPickerDialog;

public class PathPickerDialogTest {

    @Test
    public void dialog_opensWithoutError() {
        boolean[] created = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            PathPickerDialog d = new PathPickerDialog(shell, "", PathType.DIRECTORY);
            d.setBlockOnOpen(false);
            d.open();
            created[0] = d.getShell() != null && !d.getShell().isDisposed();
            d.close();
            shell.dispose();
        });
        assertTrue("Dialog shell must be created", created[0]);
    }

    @Test
    public void dialog_cancelYieldsNullResult() {
        String[] result = {"not-null"};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            PathPickerDialog d = new PathPickerDialog(shell, "/some/path", PathType.DIRECTORY);
            d.setBlockOnOpen(false);
            d.open();
            d.close(); // close without pressing OK — result stays null
            result[0] = d.getResult();
            shell.dispose();
        });
        assertNull("Closing without OK must yield null result", result[0]);
    }

    @Test
    public void dialog_opensWithVariableValueWithoutError() {
        boolean[] created = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            // Initial value contains a variable — must not throw during suggestions refresh
            PathPickerDialog d = new PathPickerDialog(
                shell, "${workspace_loc}/myrepo", PathType.DIRECTORY);
            d.setBlockOnOpen(false);
            d.open();
            created[0] = d.getShell() != null && !d.getShell().isDisposed();
            d.close();
            shell.dispose();
        });
        assertTrue("Dialog must open cleanly even with variable initial value", created[0]);
    }

    @Test
    public void dialog_suggestionSelection_updatesTextField() {
        boolean[] selectionWorked = {false};
        Display.getDefault().syncExec(() -> {
            // Use a path under workspace so PathVariableSuggestions produces at least one variable suggestion
            String wsPath = ResourcesPlugin.getWorkspace()
                .getRoot().getLocation().toFile().getAbsolutePath();
            String testPath = wsPath + java.io.File.separator + "TestFolder";

            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            PathPickerDialog d = new PathPickerDialog(shell, testPath, PathType.DIRECTORY);
            d.setBlockOnOpen(false);
            d.open();

            // At this point the dialog has computed suggestions for testPath.
            // The table should have at least 2 rows (workspace_loc suggestion + absolute).
            // Select row 0 (the variable-form suggestion) and verify the text field updates.
            d.selectSuggestionAt(0);
            String textAfterSelection = d.getCurrentTextValue();

            // The selected suggestion's variableForm starts with "${" (it's a variable, not absolute)
            selectionWorked[0] = textAfterSelection != null && textAfterSelection.startsWith("${");

            d.close();
            shell.dispose();
        });
        assertTrue("Selecting a suggestion row must copy its variable form into the text field",
            selectionWorked[0]);
    }
}
