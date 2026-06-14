package com.example.automation.tests;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;
import com.example.automation.model.Step;
import com.example.automation.model.Workflow;

public class WorkflowTest {

    @Test
    public void constructor_setsWorkflowId() {
        assertEquals("my-id", new Workflow("my-id", "Name", "Desc").getWorkflowId());
    }

    @Test
    public void constructor_setsDisplayName() {
        assertEquals("Name", new Workflow("id", "Name", "Desc").getDisplayName());
    }

    @Test
    public void constructor_setsDescription() {
        assertEquals("Desc", new Workflow("id", "Name", "Desc").getDescription());
    }

    @Test
    public void constructor_initializesEmptyStepList() {
        assertTrue(new Workflow("id", "Name", "Desc").getSteps().isEmpty());
    }

    @Test
    public void setSteps_replacesStepList() {
        Workflow wf = new Workflow("id", "Name", "Desc");
        List<Step> steps = List.of(new Step("a"));
        wf.setSteps(steps);
        assertSame(steps, wf.getSteps());
    }

    @Test
    public void setDisplayName_updatesValue() {
        Workflow wf = new Workflow("id", "Old", "Desc");
        wf.setDisplayName("New");
        assertEquals("New", wf.getDisplayName());
    }

    @Test
    public void setDescription_updatesValue() {
        Workflow wf = new Workflow("id", "Name", "Old");
        wf.setDescription("New");
        assertEquals("New", wf.getDescription());
    }
}
