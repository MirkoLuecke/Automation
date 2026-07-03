package com.example.automation.tests;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import com.example.automation.RemoteWorkflow;
import com.example.automation.WorkflowPickerDialog;
import com.example.automation.WorkflowPickerDialog.SourceType;
import com.example.automation.WorkflowPickerDialog.WorkflowEntry;
import com.example.automation.model.Workflow;

public class WorkflowPickerDialogMergeTest {

    private static Workflow wf(String id, String name) {
        return new Workflow(id, name, "");
    }

    private static RemoteWorkflow remote(String filename, String id, String name) {
        return new RemoteWorkflow(filename, wf(id, name), "{}");
    }

    @Test
    public void buildEntries_containsBothSources() {
        List<Workflow> local = Collections.singletonList(wf("local-wf", "Alpha"));
        List<RemoteWorkflow> remote = Collections.singletonList(remote("beta.json", "beta", "Beta"));
        List<WorkflowEntry> entries = WorkflowPickerDialog.buildEntries(local, remote);
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.source == SourceType.LOCAL));
        assertTrue(entries.stream().anyMatch(e -> e.source == SourceType.ARTIFACTORY));
    }

    @Test
    public void buildEntries_sourceLabelsCorrect() {
        List<Workflow> local = Collections.singletonList(wf("a", "A"));
        List<RemoteWorkflow> remote = Collections.singletonList(remote("b.json", "b", "B"));
        List<WorkflowEntry> entries = WorkflowPickerDialog.buildEntries(local, remote);
        WorkflowEntry localEntry = entries.stream()
            .filter(e -> e.source == SourceType.LOCAL).findFirst().orElseThrow();
        assertNull(localEntry.rawJson);
        WorkflowEntry remoteEntry = entries.stream()
            .filter(e -> e.source == SourceType.ARTIFACTORY).findFirst().orElseThrow();
        assertNotNull(remoteEntry.rawJson);
    }

    @Test
    public void buildEntries_localFirstForSameName() {
        List<Workflow> local = Collections.singletonList(wf("wf", "Alpha"));
        List<RemoteWorkflow> remote = Collections.singletonList(remote("wf.json", "wf", "Alpha"));
        List<WorkflowEntry> entries = WorkflowPickerDialog.buildEntries(local, remote);
        assertEquals(2, entries.size());
        assertEquals(SourceType.LOCAL, entries.get(0).source);
        assertEquals(SourceType.ARTIFACTORY, entries.get(1).source);
    }

    @Test
    public void buildEntries_emptyRemote_onlyLocal() {
        List<Workflow> local = Arrays.asList(wf("a", "A"), wf("b", "B"));
        List<WorkflowEntry> entries = WorkflowPickerDialog.buildEntries(local, Collections.emptyList());
        assertEquals(2, entries.size());
        entries.forEach(e -> assertEquals(SourceType.LOCAL, e.source));
    }

    @Test
    public void buildEntries_emptyLocal_onlyRemote() {
        List<RemoteWorkflow> remote = Collections.singletonList(remote("x.json", "x", "X"));
        List<WorkflowEntry> entries = WorkflowPickerDialog.buildEntries(Collections.emptyList(), remote);
        assertEquals(1, entries.size());
        assertEquals(SourceType.ARTIFACTORY, entries.get(0).source);
    }
}
