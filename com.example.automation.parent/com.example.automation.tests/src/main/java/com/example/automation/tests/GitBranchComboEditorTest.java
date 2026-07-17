package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.example.automation.GitBranchComboEditor;

public class GitBranchComboEditorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void parseRemoteBranches_stripsRemotePrefix() {
        List<String> result = GitBranchComboEditor.parseRemoteBranches("  origin/main\n");
        assertEquals(List.of("main"), result);
    }

    @Test
    public void parseRemoteBranches_excludesHeadPointer() {
        List<String> result = GitBranchComboEditor.parseRemoteBranches(
            "  origin/HEAD -> origin/main\n  origin/main\n");
        assertEquals(List.of("main"), result);
    }

    @Test
    public void parseRemoteBranches_emptyOutput_returnsEmpty() {
        assertTrue(GitBranchComboEditor.parseRemoteBranches("").isEmpty());
    }

    @Test
    public void parseRemoteBranches_preservesSubpathInBranchName() {
        List<String> result = GitBranchComboEditor.parseRemoteBranches("  origin/feature/foo\n");
        assertEquals(List.of("feature/foo"), result);
    }

    @Test
    public void parseRemoteBranches_deduplicatesAcrossRemotes() {
        List<String> result = GitBranchComboEditor.parseRemoteBranches(
            "  origin/main\n  upstream/main\n");
        assertEquals(List.of("main"), result);
    }

    @Test
    public void parseRemoteBranches_sortsAlphabetically() {
        List<String> result = GitBranchComboEditor.parseRemoteBranches(
            "  origin/main\n  origin/develop\n");
        assertEquals(List.of("develop", "main"), result);
    }

    @Test
    public void parseRemoteBranches_nullInput_returnsEmpty() {
        assertTrue(GitBranchComboEditor.parseRemoteBranches(null).isEmpty());
    }

    @Test
    public void combo_showsConfigurePlaceholder_whenRepoDirBlank() {
        boolean[] found = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            GitBranchComboEditor editor = new GitBranchComboEditor(shell, () -> "");
            editor.setFocus();
            found[0] = Arrays.asList(((CCombo) editor.getControl()).getItems())
                             .contains("(configure repoDir first)");
            shell.dispose();
        });
        assertTrue("Blank repoDir must show configure placeholder", found[0]);
    }

    @Test
    public void combo_showsNotFoundPlaceholder_whenRepoDirNotGitRepo() throws Exception {
        File plain = tmp.newFolder("notgit");
        boolean[] found = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            GitBranchComboEditor editor = new GitBranchComboEditor(shell,
                () -> plain.getAbsolutePath());
            editor.setFocus();
            found[0] = Arrays.asList(((CCombo) editor.getControl()).getItems())
                             .contains("(no remote branches found)");
            shell.dispose();
        });
        assertTrue("Non-git dir must show not-found placeholder", found[0]);
    }

    @Test
    public void combo_emptyAtCreation_populatedOnSetFocus() {
        boolean[] emptyAtCreation = {false};
        boolean[] hasItemsAfterFocus = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            GitBranchComboEditor editor = new GitBranchComboEditor(shell, () -> "");
            emptyAtCreation[0] = ((CCombo) editor.getControl()).getItemCount() == 0;
            editor.setFocus();
            hasItemsAfterFocus[0] = ((CCombo) editor.getControl()).getItemCount() > 0;
            shell.dispose();
        });
        assertTrue("Combo must have no items at creation to avoid oversized computeSize()", emptyAtCreation[0]);
        assertTrue("Combo must have items after setFocus()", hasItemsAfterFocus[0]);
    }

    @Test
    public void parseLsRemoteBranches_extractsBranchNames() {
        String output = "abc123\trefs/heads/main\ndef456\trefs/heads/develop\n";
        List<String> result = GitBranchComboEditor.parseLsRemoteBranches(output);
        assertEquals(List.of("develop", "main"), result);
    }

    @Test
    public void parseLsRemoteBranches_emptyOutput_returnsEmpty() {
        assertTrue(GitBranchComboEditor.parseLsRemoteBranches("").isEmpty());
    }

    @Test
    public void parseLsRemoteBranches_nullInput_returnsEmpty() {
        assertTrue(GitBranchComboEditor.parseLsRemoteBranches(null).isEmpty());
    }

    @Test
    public void combo_preservesCurrentValueAfterSetFocus() {
        String[] result = {null};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            GitBranchComboEditor editor = new GitBranchComboEditor(shell, () -> "");
            editor.setValue("develop");
            editor.setFocus();
            result[0] = (String) editor.getValue();
            shell.dispose();
        });
        assertEquals("develop", result[0]);
    }
}
