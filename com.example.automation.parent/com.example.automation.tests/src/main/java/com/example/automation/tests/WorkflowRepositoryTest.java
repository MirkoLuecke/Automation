package com.example.automation.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;

public class WorkflowRepositoryTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private WorkflowRepository repo() {
        return new WorkflowRepository(temp.getRoot());
    }

    @Test
    public void roundTrip() throws Exception {
        Workflow wf = new Workflow("my-wf", "My Workflow", "Does stuff");
        Step step = new Step("com.example.action.maven");
        Map<String, String> config = new HashMap<>();
        config.put("path", "/some/path");
        step.setConfig(config);
        wf.getSteps().add(step);

        WorkflowRepository repo = repo();
        repo.save(wf);
        Workflow loaded = repo.load("my-wf");

        assertEquals("my-wf", loaded.getWorkflowId());
        assertEquals("My Workflow", loaded.getDisplayName());
        assertEquals("Does stuff", loaded.getDescription());
        assertEquals(1, loaded.getSteps().size());
        assertEquals("com.example.action.maven", loaded.getSteps().get(0).getActionId());
        assertEquals("/some/path", loaded.getSteps().get(0).getConfig().get("path"));
    }

    @Test
    public void transientFieldsNotPersisted() throws Exception {
        Workflow wf = new Workflow("wf1", "WF1", "desc");
        Step step = new Step("action1");
        step.setStatus(StepStatus.RED);
        step.setProgress(75);
        wf.getSteps().add(step);

        WorkflowRepository repo = repo();
        repo.save(wf);
        Workflow loaded = repo.load("wf1");

        assertEquals(StepStatus.WHITE, loaded.getSteps().get(0).getStatus());
        assertEquals(0, loaded.getSteps().get(0).getProgress());
    }

    @Test
    public void listReturnsAllWorkflows() throws Exception {
        WorkflowRepository repo = repo();
        repo.save(new Workflow("wf-a", "A", "desc a"));
        repo.save(new Workflow("wf-b", "B", "desc b"));

        List<Workflow> list = repo.list();
        assertEquals(2, list.size());
    }

    @Test
    public void saveOverwritesExisting() throws Exception {
        WorkflowRepository repo = repo();
        repo.save(new Workflow("wf1", "Old Name", "desc"));
        repo.save(new Workflow("wf1", "New Name", "desc"));

        Workflow loaded = repo.load("wf1");
        assertEquals("New Name", loaded.getDisplayName());
    }

    @Test
    public void directoryAutoCreated() throws Exception {
        File subDir = new File(temp.getRoot(), "sub/auto");
        WorkflowRepository repo = new WorkflowRepository(subDir);
        repo.save(new Workflow("wf1", "WF1", "desc"));

        assertTrue(subDir.exists());
        assertTrue(new File(subDir, "wf1.json").exists());
    }

    @Test
    public void malformedFileSkipped() throws Exception {
        File bad = new File(temp.getRoot(), "bad.json");
        try (FileWriter w = new FileWriter(bad)) {
            w.write("NOT VALID JSON {{{{");
        }
        WorkflowRepository repo = repo();
        repo.save(new Workflow("good", "Good", "desc"));

        List<Workflow> list = repo.list();
        assertEquals(1, list.size());
        assertEquals("good", list.get(0).getWorkflowId());
    }

    @Test
    public void deleteRemovesFile() throws Exception {
        WorkflowRepository repo = repo();
        repo.save(new Workflow("to-delete", "To Delete", "desc"));
        assertTrue(new File(temp.getRoot(), "to-delete.json").exists());

        repo.delete("to-delete");

        assertFalse(new File(temp.getRoot(), "to-delete.json").exists());
    }

    @Test
    public void delete_nonExistent_returnsFalse() {
        assertFalse(repo().delete("never-saved-workflow"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void save_blankId_throwsIllegalArgument() throws Exception {
        repo().save(new Workflow("", "Name", "desc"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void save_idWithSlash_throwsIllegalArgument() throws Exception {
        repo().save(new Workflow("a/b", "Name", "desc"));
    }

    @Test(expected = java.io.IOException.class)
    public void load_nonExistent_throwsIOException() throws Exception {
        repo().load("no-such-workflow-xyz");
    }

    @Test
    public void list_emptyDir_returnsEmpty() throws Exception {
        assertTrue(repo().list().isEmpty());
    }
}
