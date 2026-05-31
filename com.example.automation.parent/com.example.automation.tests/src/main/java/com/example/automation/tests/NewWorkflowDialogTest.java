package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

import com.example.automation.NewWorkflowDialog;
import com.example.automation.model.Workflow;

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

    @Test
    public void deriveId_allSpecialChars_fallsBackToWorkflow() {
        assertEquals("workflow", NewWorkflowDialog.deriveId("!!!", Set.of()));
    }

    @Test
    public void deriveId_fillsGap() {
        assertEquals("my-workflow-2",
            NewWorkflowDialog.deriveId("My Workflow", Set.of("my-workflow", "my-workflow-3")));
    }

    @Test
    public void applyEdits_updatesNameAndDescriptionKeepsId() {
        Workflow wf = new Workflow("my-id", "Old Name", "Old Desc");
        NewWorkflowDialog.applyEdits(wf, "New Name", "New Desc");
        assertEquals("my-id", wf.getWorkflowId());
        assertEquals("New Name", wf.getDisplayName());
        assertEquals("New Desc", wf.getDescription());
    }

    @Test
    public void applyEdits_emptyDescription_setsEmptyString() {
        Workflow wf = new Workflow("wf-1", "Name", "Some desc");
        NewWorkflowDialog.applyEdits(wf, "Name", "");
        assertEquals("", wf.getDescription());
        assertEquals("wf-1", wf.getWorkflowId());
    }
}
