package com.example.automation;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.DialogCellEditor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class MultiLineTextCellEditor extends DialogCellEditor {

    public MultiLineTextCellEditor(Composite parent) {
        super(parent);
    }

    @Override
    protected Object openDialogBox(Control cellEditorWindow) {
        CommandDialog d = new CommandDialog(cellEditorWindow.getShell(), (String) getValue());
        return d.open() == Window.OK ? d.result : getValue();
    }

    private static class CommandDialog extends Dialog {
        private final String initial;
        String result;
        private Text text;

        CommandDialog(Shell parent, String initial) {
            super(parent);
            this.initial = initial != null ? initial : "";
            setShellStyle(getShellStyle() | SWT.RESIZE);
        }

        @Override
        protected void configureShell(Shell shell) {
            super.configureShell(shell);
            shell.setText("Edit Command");
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite area = (Composite) super.createDialogArea(parent);
            text = new Text(area, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP);
            GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
            gd.widthHint = 400;
            gd.heightHint = 200;
            text.setLayoutData(gd);
            text.setText(initial);
            return area;
        }

        @Override
        protected void okPressed() {
            result = text.getText();
            super.okPressed();
        }
    }
}
