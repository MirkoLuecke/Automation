package com.example.automation;

import org.eclipse.jface.viewers.DialogCellEditor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

public class PathCellEditor extends DialogCellEditor {

    public enum PathType { FILE, DIRECTORY }

    private final PathType pathType;
    private Label label;

    public PathCellEditor(Composite parent, PathType pathType) {
        super(parent);
        this.pathType = pathType;
    }

    @Override
    protected Control createContents(Composite cell) {
        label = new Label(cell, SWT.LEFT);
        return label;
    }

    @Override
    protected void updateContents(Object value) {
        label.setText(value instanceof String s ? s : "");
    }

    @Override
    protected Object openDialogBox(Control cellEditorWindow) {
        PathPickerDialog dialog = new PathPickerDialog(
            cellEditorWindow.getShell(), (String) getValue(), pathType);
        if (dialog.open() == Window.OK) return dialog.getResult();
        return null;
    }
}
