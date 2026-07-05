package com.example.automation.tests;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;
import com.example.automation.GitBranchComboEditor;

public class GitBranchComboEditorTest {

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
}
