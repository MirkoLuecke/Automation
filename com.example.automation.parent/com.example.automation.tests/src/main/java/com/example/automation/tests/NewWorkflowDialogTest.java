package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

import com.example.automation.NewWorkflowDialog;

public class NewWorkflowDialogTest {

    @Test
    public void deriveId_basic() {
        assertEquals("my-workflow", NewWorkflowDialog.deriveId("My Workflow", Set.of()));
    }

    @Test
    public void deriveId_specialChars() {
        assertEquals("build-release", NewWorkflowDialog.deriveId("Build & Release!!", Set.of()));
    }

    @Test
    public void deriveId_leadingTrailingSpecial() {
        assertEquals("hello", NewWorkflowDialog.deriveId("  !!hello!!", Set.of()));
    }

    @Test
    public void deriveId_conflict() {
        assertEquals("my-workflow-2",
            NewWorkflowDialog.deriveId("My Workflow", Set.of("my-workflow")));
    }

    @Test
    public void deriveId_multipleConflicts() {
        assertEquals("my-workflow-3",
            NewWorkflowDialog.deriveId("My Workflow", Set.of("my-workflow", "my-workflow-2")));
    }
}
