package com.example.automation;

import java.util.Arrays;
import java.util.TreeSet;

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
 * project names, sorted alphabetically. The list is rebuilt on every focus
 * event so it is never stale. SWT.READ_ONLY avoids the GTK bug where clicking
 * the dropdown arrow fires a spurious focusLost that would deactivate the editor.
 */
public class ProjectComboBoxCellEditor extends CellEditor {

    private Combo combo;
    private String lastValue = "";

    public ProjectComboBoxCellEditor(Composite parent) {
        create(parent);
    }

    @Override
    protected Control createControl(Composite parent) {
        combo = new Combo(parent, SWT.READ_ONLY);
        populateItems();
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
    protected void doSetFocus() {
        populateItems();
        combo.setFocus();
    }

    private void populateItems() {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        TreeSet<String> names = new TreeSet<>();
        Arrays.stream(projects).map(IProject::getName).forEach(names::add);
        if (!lastValue.isBlank()) names.add(lastValue);
        combo.setItems(names.toArray(new String[0]));
        combo.setText(lastValue);
    }

    @Override
    protected Object doGetValue() {
        String text = combo.getText();
        // With READ_ONLY, setText() is a no-op when the value is not in the list.
        // Fall back to lastValue so saved project names survive populate cycles.
        return text.isEmpty() ? lastValue : text;
    }

    @Override
    protected void doSetValue(Object value) {
        lastValue = value instanceof String s ? s : "";
        combo.setText(lastValue);
    }
}
