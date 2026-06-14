package com.example.automation.tests;

import static org.junit.Assert.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.Test;
import com.example.automation.MultiLineTextCellEditor;

public class MultiLineTextCellEditorTest {

    @Test
    public void setValue_getValue_roundTrip() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            MultiLineTextCellEditor editor = new MultiLineTextCellEditor(shell);
            editor.setValue("hello\nworld");
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals("hello\nworld", result[0]);
    }

    @Test
    public void setValue_emptyString_roundTrips() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            MultiLineTextCellEditor editor = new MultiLineTextCellEditor(shell);
            editor.setValue("");
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals("", result[0]);
    }

    @Test
    public void setValue_multiLineWithTabs_roundTrips() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            MultiLineTextCellEditor editor = new MultiLineTextCellEditor(shell);
            editor.setValue("line1\n\tline2\n\tline3");
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals("line1\n\tline2\n\tline3", result[0]);
    }
}
