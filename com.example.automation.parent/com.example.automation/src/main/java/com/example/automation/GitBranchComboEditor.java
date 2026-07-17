package com.example.automation;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public class GitBranchComboEditor extends CellEditor {

    private final Supplier<String> repoDirSupplier;
    private final boolean allowEmpty;
    private CCombo combo;
    private String lastValue = "";

    public GitBranchComboEditor(Composite parent, Supplier<String> repoDirSupplier) {
        this(parent, repoDirSupplier, false);
    }

    public GitBranchComboEditor(Composite parent, Supplier<String> repoDirSupplier, boolean allowEmpty) {
        this.repoDirSupplier = repoDirSupplier;
        this.allowEmpty = allowEmpty;
        create(parent);
    }

    @Override
    protected Control createControl(Composite parent) {
        combo = new CCombo(parent, SWT.NONE);
        // Do NOT call populateItems() here: branch names make computeSize() return a large
        // preferred width, which the Properties view uses to size the column — pushing the
        // arrow button off-screen. Items are loaded in doSetFocus() before user interaction.
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
        // Repopulates to pick up new branches since createControl(); accepts two git calls per activation.
        populateItems();
        combo.setFocus();
    }

    @Override
    protected Object doGetValue() {
        return combo.getText();
    }

    @Override
    protected void doSetValue(Object value) {
        lastValue = value instanceof String s ? s : "";
        combo.setText(lastValue);
    }

    private void populateItems() {
        List<String> fetched = fetchBranches();
        boolean hasPlaceholder = !fetched.isEmpty() && fetched.get(0).startsWith("(");

        List<String> items = new ArrayList<>();
        if (allowEmpty && !hasPlaceholder) {
            items.add("");
        }
        if (hasPlaceholder) {
            items.addAll(fetched);
        } else {
            // TreeSet gives deduplication + alphabetical order for free
            TreeSet<String> sorted = new TreeSet<>(fetched);
            if (!lastValue.isBlank() && !lastValue.startsWith("(")) {
                sorted.add(lastValue);
            }
            items.addAll(sorted);
        }
        combo.setItems(items.toArray(new String[0]));
        combo.setText(lastValue);
    }

    private List<String> fetchBranches() {
        String repoDir = resolveRepoDir();
        if (repoDir == null) return List.of("(configure repoDir first)");
        try {
            Process proc = new ProcessBuilder("git", "branch", "-r")
                .directory(new File(repoDir))
                .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();
            List<String> branches = parseRemoteBranches(output);
            return branches.isEmpty() ? List.of("(no remote branches found)") : branches;
        } catch (Exception e) {
            return List.of("(no remote branches found)");
        }
    }

    private String resolveRepoDir() {
        String raw = repoDirSupplier.get();
        if (raw == null || raw.isBlank()) return null;
        try {
            IStringVariableManager mgr = VariablesPlugin.getDefault().getStringVariableManager();
            // false = leave undefined variables as literals; they will fail the git call and show the not-found placeholder
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
