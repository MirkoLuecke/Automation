package com.example.automation;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

public class MultiLineTextCellEditor extends CellEditor {

    private Text text;

    public MultiLineTextCellEditor(Composite parent) {
        super(parent);
    }

    @Override
    protected Control createControl(Composite parent) {
        text = new Text(parent, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.BORDER);
        text.addKeyListener(KeyListener.keyPressedAdapter(e -> {
            if (e.keyCode == SWT.CR && (e.stateMask & SWT.MOD1) != 0) {
                fireApplyEditorValue();
                deactivate();
            }
        }));
        return text;
    }

    @Override
    protected Object doGetValue() {
        return text == null ? "" : text.getText();
    }

    @Override
    protected void doSetValue(Object value) {
        if (text != null) text.setText(value == null ? "" : (String) value);
    }

    @Override
    protected void doSetFocus() {
        if (text != null) {
            text.selectAll();
            text.setFocus();
        }
    }
}
