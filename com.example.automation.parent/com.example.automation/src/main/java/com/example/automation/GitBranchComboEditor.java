package com.example.automation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public class GitBranchComboEditor extends CellEditor {

    private final Supplier<String> repoDirSupplier;
    private Combo combo;

    public GitBranchComboEditor(Composite parent, Supplier<String> repoDirSupplier) {
        super(parent);
        this.repoDirSupplier = repoDirSupplier;
    }

    @Override
    protected Control createControl(Composite parent) {
        combo = new Combo(parent, SWT.DROP_DOWN);
        return combo;
    }

    @Override protected void doSetFocus()              { combo.setFocus(); }
    @Override protected Object doGetValue()            { return combo.getText(); }
    @Override protected void doSetValue(Object value)  { combo.setText(value instanceof String s ? s : ""); }

    public static List<String> parseRemoteBranches(String gitOutput) {
        if (gitOutput == null || gitOutput.isBlank()) return List.of();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Arrays.stream(gitOutput.split("\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.contains(" -> "))
            .map(line -> { int slash = line.indexOf('/'); return slash >= 0 ? line.substring(slash + 1) : line; })
            .sorted()
            .forEach(seen::add);
        return new ArrayList<>(seen);
    }
}
