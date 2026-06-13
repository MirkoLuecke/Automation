package com.example.automation;

import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * Editable combo-box cell editor pre-populated with all Eclipse workspace
 * project names, sorted alphabetically. The user may also type a name that
 * is not in the list.
 */
public class ProjectComboBoxCellEditor extends CellEditor {

    private Combo combo;

    public ProjectComboBoxCellEditor(Composite parent) {
        super(parent);
    }

    @Override
    protected Control createControl(Composite parent) {
        combo = new Combo(parent, SWT.DROP_DOWN);
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        String[] names = Arrays.stream(projects)
            .map(IProject::getName)
            .sorted(Comparator.naturalOrder())
            .toArray(String[]::new);
        combo.setItems(names);

        combo.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                ProjectComboBoxCellEditor.this.focusLost();
            }
        });
        combo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                keyReleaseOccured(e);
            }
        });
        return combo;
    }

    @Override
    protected Object doGetValue() {
        return combo.getText();
    }

    @Override
    protected void doSetValue(Object value) {
        combo.setText(value instanceof String s ? s : "");
    }

    @Override
    protected void doSetFocus() {
        combo.setFocus();
    }
}
