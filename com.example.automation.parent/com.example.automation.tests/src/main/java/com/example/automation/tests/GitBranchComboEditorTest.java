package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
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
            found[0] = Arrays.asList(((Combo) editor.getControl()).getItems())
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
            found[0] = Arrays.asList(((Combo) editor.getControl()).getItems())
                             .contains("(no remote branches found)");
            shell.dispose();
        });
        assertTrue("Non-git dir must show not-found placeholder", found[0]);
    }

    @Test
    public void combo_populatedWithoutExplicitSetFocus() {
        boolean[] hasItems = {false};
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault(), SWT.NONE);
            shell.open();
            GitBranchComboEditor editor = new GitBranchComboEditor(shell, () -> "");
            hasItems[0] = ((Combo) editor.getControl()).getItemCount() > 0;
            shell.dispose();
        });
        assertTrue("Combo must have items without calling setFocus()", hasItems[0]);
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
