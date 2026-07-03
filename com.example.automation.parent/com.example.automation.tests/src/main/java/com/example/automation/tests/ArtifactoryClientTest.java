package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import com.example.automation.ArtifactoryClient;
import com.example.automation.ArtifactoryException;
import com.example.automation.RemoteWorkflow;

public class ArtifactoryClientTest {

    private static ArtifactoryClient clientWith(String listBody, int listStatus,
                                                 String fileBody, int fileStatus) {
        return new ArtifactoryClient("USER", "KEY", (url, auth) -> {
            if (url.contains("/api/storage/"))
                return new ArtifactoryClient.HttpResponse(listStatus, listBody);
            return new ArtifactoryClient.HttpResponse(fileStatus, fileBody);
        });
    }

    private static final String FOLDER_JSON =
        "{\"children\":[" +
        "{\"uri\":\"/wf1.json\",\"folder\":false}," +
        "{\"uri\":\"/wf2.json\",\"folder\":false}," +
        "{\"uri\":\"/sub\",\"folder\":true}," +
        "{\"uri\":\"/readme.txt\",\"folder\":false}" +
        "]}";

    private static final String WORKFLOW_JSON =
        "{\"workflowId\":\"wf1\",\"displayName\":\"WF One\",\"description\":\"desc\",\"steps\":[]}";

    @Test
    public void listWorkflows_parsesJsonFiles() throws Exception {
        ArtifactoryClient client = clientWith(FOLDER_JSON, 200, WORKFLOW_JSON, 200);
        List<RemoteWorkflow> result = client.listWorkflows();
        assertEquals(2, result.size());
        assertEquals("wf1.json", result.get(0).filename);
        assertEquals("WF One", result.get(0).workflow.getDisplayName());
    }

    @Test
    public void listWorkflows_filtersOutFolders() throws Exception {
        ArtifactoryClient client = clientWith(FOLDER_JSON, 200, WORKFLOW_JSON, 200);
        List<RemoteWorkflow> result = client.listWorkflows();
        result.forEach(e -> assertFalse(e.filename + " should not be a folder",
                                        e.filename.equals("sub")));
    }

    @Test
    public void listWorkflows_filtersOutNonJson() throws Exception {
        ArtifactoryClient client = clientWith(FOLDER_JSON, 200, WORKFLOW_JSON, 200);
        List<RemoteWorkflow> result = client.listWorkflows();
        result.forEach(e -> assertTrue(e.filename + " should end with .json",
                                       e.filename.endsWith(".json")));
    }

    @Test(expected = ArtifactoryException.class)
    public void listWorkflows_throwsOnMissingUser() throws Exception {
        new ArtifactoryClient("", "KEY", (url, auth) ->
            new ArtifactoryClient.HttpResponse(200, FOLDER_JSON)).listWorkflows();
    }

    @Test(expected = ArtifactoryException.class)
    public void listWorkflows_throwsOnMissingApiKey() throws Exception {
        new ArtifactoryClient("USER", "", (url, auth) ->
            new ArtifactoryClient.HttpResponse(200, FOLDER_JSON)).listWorkflows();
    }

    @Test(expected = ArtifactoryException.class)
    public void listWorkflows_throwsOn401() throws Exception {
        clientWith("{}", 401, "", 200).listWorkflows();
    }

    @Test
    public void listWorkflows_401_messageContainsAuthFailed() {
        try {
            clientWith("{}", 401, "", 200).listWorkflows();
            fail("expected exception");
        } catch (ArtifactoryException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("authentication failed"));
        }
    }

    @Test(expected = ArtifactoryException.class)
    public void listWorkflows_throwsOn500() throws Exception {
        clientWith("{}", 500, "", 200).listWorkflows();
    }

    @Test
    public void listWorkflows_500_messageContainsHttpStatus() {
        try {
            clientWith("{}", 500, "", 200).listWorkflows();
            fail("expected exception");
        } catch (ArtifactoryException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("500"));
        }
    }

    @Test(expected = ArtifactoryException.class)
    public void listWorkflows_throwsOnIoException() throws Exception {
        new ArtifactoryClient("USER", "KEY", (url, auth) -> {
            throw new IOException("timeout");
        }).listWorkflows();
    }

    @Test
    public void listWorkflows_ioException_messageContainsReason() {
        try {
            new ArtifactoryClient("USER", "KEY", (url, auth) -> {
                throw new IOException("timeout");
            }).listWorkflows();
            fail("expected exception");
        } catch (ArtifactoryException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("timeout"));
        }
    }

    @Test
    public void downloadWorkflow_returnsRawJson() throws Exception {
        ArtifactoryClient client = clientWith(FOLDER_JSON, 200, WORKFLOW_JSON, 200);
        String raw = client.downloadWorkflow("wf1.json");
        assertEquals(WORKFLOW_JSON, raw);
    }
}
