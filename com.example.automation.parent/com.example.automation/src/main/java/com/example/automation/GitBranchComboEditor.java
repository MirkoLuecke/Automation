package com.example.automation;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.Arrays;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public class GitBranchComboEditor extends CellEditor {

    private final Supplier<String> repoDirSupplier;
    private Combo combo;

    public GitBranchComboEditor(Composite parent, Supplier<String> repoDirSupplier) {
        this.repoDirSupplier = repoDirSupplier;
        create(parent);
    }

    @Override
    protected Control createControl(Composite parent) {
        combo = new Combo(parent, SWT.DROP_DOWN);
        populateItems();
        combo.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                GitBranchComboEditor.this.focusLost();
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

    @Override
    protected Object doGetValue() {
        return combo.getText();
    }

    @Override
    protected void doSetValue(Object value) {
        combo.setText(value instanceof String s ? s : "");
    }

    private void populateItems() {
        String current = combo.getText();
        List<String> branches = fetchBranches();
        String[] items = branches.toArray(new String[0]);
        combo.setItems(items);
        if (!current.isEmpty()) combo.setText(current);
    }

    private List<String> fetchBranches() {
        String repoDir = resolveRepoDir();
        if (repoDir == null) return Arrays.asList("(configure repoDir first)");
        try {
            Process proc = new ProcessBuilder("git", "branch", "-r")
                .directory(new File(repoDir))
                .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();
            List<String> branches = parseRemoteBranches(output);
            return branches.isEmpty() ? Arrays.asList("(no remote branches found)") : branches;
        } catch (Exception e) {
            return Arrays.asList("(no remote branches found)");
        }
    }

    private String resolveRepoDir() {
        String raw = repoDirSupplier.get();
        if (raw == null || raw.isBlank()) return null;
        try {
            IStringVariableManager mgr = VariablesPlugin.getDefault().getStringVariableManager();
            String resolved = mgr.performStringSubstitution(raw, false);
            return resolved.isBlank() ? null : resolved;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> parseRemoteBranches(String gitOutput) {
        if (gitOutput == null || gitOutput.isBlank()) return List.of();
        return Arrays.stream(gitOutput.split("\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.contains(" -> "))
            .map(line -> { int slash = line.indexOf('/'); return slash >= 0 ? line.substring(slash + 1) : line; })
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }
}
