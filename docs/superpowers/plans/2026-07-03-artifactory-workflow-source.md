# Artifactory Workflow Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the Open Workflow dialog to show workflows from both the local directory and a company-internal JFrog Artifactory folder, with a Source column and automatic download-on-open for remote entries.

**Architecture:** A new `ArtifactoryClient` fetches workflow metadata and content from Artifactory using the JFrog Storage REST API, authenticated via `ARTIFACTORY_USER` / `ARTIFACTORY_API_KEY` env vars. `WorkflowPickerDialog` is extended to merge local and remote entries into a single list, show a warning label on failure, and download+save an Artifactory workflow to the local directory when the user selects one. The Artifactory base URL is XOR-obfuscated as a `byte[]` literal in `ArtifactoryConfig`.

**Tech Stack:** Java 17, JUnit 4, Gson (already bundled), `HttpURLConnection` (JDK — no new dependencies)

---

## File Structure

| File | Role |
|------|------|
| `com.example.automation/…/ArtifactoryException.java` | Typed exception for all Artifactory errors |
| `com.example.automation/…/ArtifactoryConfig.java` | XOR-obfuscated folder URL; single `folderUrl()` method |
| `com.example.automation/…/RemoteWorkflow.java` | Data class: filename + parsed `Workflow` + raw JSON |
| `com.example.automation/…/ArtifactoryClient.java` | REST client: `listWorkflows()` + `downloadWorkflow()` |
| `com.example.automation/…/WorkflowPickerDialog.java` | Extended dialog with Source column, warning label, download flow |
| `com.example.automation/…/AutomationView.java` | Update `onOpenWorkflow()` to pass `File storageDir` |
| `com.example.automation.tests/…/ArtifactoryConfigTest.java` | XOR round-trip + sanity check on `folderUrl()` |
| `com.example.automation.tests/…/ArtifactoryClientTest.java` | Unit tests via injectable `Fetcher` |
| `com.example.automation.tests/…/WorkflowPickerDialogMergeTest.java` | Unit tests for `buildEntries()` merge logic |

All source files live under:
`com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/`

All test files live under:
`com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/`

---

## Task 1: ArtifactoryException

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ArtifactoryException.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ArtifactoryExceptionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import org.junit.Test;
import com.example.automation.ArtifactoryException;

public class ArtifactoryExceptionTest {

    @Test
    public void constructor_setsMessage() {
        ArtifactoryException ex = new ArtifactoryException("oops");
        assertEquals("oops", ex.getMessage());
    }

    @Test
    public void constructor_withCause_setsBoth() {
        Throwable cause = new RuntimeException("root");
        ArtifactoryException ex = new ArtifactoryException("oops", cause);
        assertEquals("oops", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn test -pl com.example.automation.parent/com.example.automation.tests -Dtest=ArtifactoryExceptionTest -q 2>&1 | tail -5
```

Expected: compilation error — `ArtifactoryException` does not exist.

- [ ] **Step 3: Implement ArtifactoryException**

```java
package com.example.automation;

public class ArtifactoryException extends Exception {
    public ArtifactoryException(String message) { super(message); }
    public ArtifactoryException(String message, Throwable cause) { super(message, cause); }
}
```

- [ ] **Step 4: Run test to verify it passes**

```
mvn test -pl com.example.automation.parent/com.example.automation.tests -Dtest=ArtifactoryExceptionTest -q 2>&1 | tail -5
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ArtifactoryException.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ArtifactoryExceptionTest.java
git commit -m "feat: add ArtifactoryException"
```

---

## Task 2: ArtifactoryConfig (XOR-obfuscated URL)

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ArtifactoryConfig.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ArtifactoryConfigTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import com.example.automation.ArtifactoryConfig;

public class ArtifactoryConfigTest {

    @Test
    public void xorDecrypt_roundTrip() {
        byte[] key = { 1, 2, 3 };
        byte[] plain = "hello-world".getBytes(StandardCharsets.UTF_8);
        byte[] enc = new byte[plain.length];
        for (int i = 0; i < plain.length; i++)
            enc[i] = (byte) (plain[i] ^ key[i % key.length]);
        byte[] dec = new byte[enc.length];
        for (int i = 0; i < enc.length; i++)
            dec[i] = (byte) (enc[i] ^ key[i % key.length]);
        assertEquals("hello-world", new String(dec, StandardCharsets.UTF_8));
    }

    @Test
    public void folderUrl_isValidHttpsUrl() {
        String url = ArtifactoryConfig.folderUrl();
        assertTrue("Expected https URL, got: " + url, url.startsWith("https://"));
        assertFalse(url.isEmpty());
    }

    @Test
    public void listingUrl_containsApiStorage() {
        String url = ArtifactoryConfig.listingUrl();
        assertTrue(url.contains("/api/storage/"));
        assertTrue(url.startsWith("https://"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: compilation error — `ArtifactoryConfig` does not exist.

- [ ] **Step 3: Generate the encrypted URL bytes using JShell**

Open a terminal and run `jshell` (ships with JDK 17):

```
jshell
```

Paste this snippet into JShell:

```java
String url = "https://artifactory.surv-xc-de.cap.saab.se/artifactory/abew-smr-sync/tools/eclipse/automation-workflows";
byte[] key = {(byte)0x4A,(byte)0x3F,(byte)0x92,(byte)0xB1,(byte)0x5C,(byte)0xE7,(byte)0x28,(byte)0x9D,(byte)0x61,(byte)0xF4};
byte[] enc = new byte[url.length()];
for (int i = 0; i < url.length(); i++) enc[i] = (byte)(url.charAt(i) ^ key[i % key.length]);
StringBuilder sb = new StringBuilder("{ ");
for (int i = 0; i < enc.length; i++) { if (i > 0) sb.append(", "); sb.append(String.format("(byte)0x%02X", enc[i] & 0xFF)); }
sb.append(" }");
System.out.println(sb);
```

JShell prints a single line of the form `{ (byte)0xXX, (byte)0xXX, ... }`. Copy it — this is the value for `ENC` in the next step.

Exit JShell: `/exit`

- [ ] **Step 4: Implement ArtifactoryConfig**

Replace `/* paste ENC bytes here */` with the line copied from JShell.

```java
package com.example.automation;

import java.nio.charset.StandardCharsets;

final class ArtifactoryConfig {

    private static final byte[] KEY = {
        (byte)0x4A,(byte)0x3F,(byte)0x92,(byte)0xB1,(byte)0x5C,
        (byte)0xE7,(byte)0x28,(byte)0x9D,(byte)0x61,(byte)0xF4
    };

    // Generated by XOR-encoding the folder URL with KEY (see plan Task 2 Step 3)
    private static final byte[] ENC = /* paste ENC bytes here */;

    /** Returns the plain Artifactory folder URL. */
    static String folderUrl() {
        byte[] out = new byte[ENC.length];
        for (int i = 0; i < ENC.length; i++)
            out[i] = (byte) (ENC[i] ^ KEY[i % KEY.length]);
        return new String(out, StandardCharsets.UTF_8);
    }

    /**
     * Returns the Artifactory Storage API URL for listing the folder contents.
     * Derived by inserting "/api/storage" after "/artifactory" in the folder URL.
     */
    static String listingUrl() {
        String folder = folderUrl();
        // folder = https://host/artifactory/repo/path
        // listing = https://host/artifactory/api/storage/repo/path
        int idx = folder.indexOf("/artifactory/") + "/artifactory".length();
        return folder.substring(0, idx) + "/api/storage" + folder.substring(idx);
    }

    private ArtifactoryConfig() {}
}
```

- [ ] **Step 5: Run tests to verify they pass**

```
mvn test -pl com.example.automation.parent/com.example.automation.tests -Dtest=ArtifactoryConfigTest -q 2>&1 | tail -5
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ArtifactoryConfig.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ArtifactoryConfigTest.java
git commit -m "feat: add ArtifactoryConfig with XOR-obfuscated URL"
```

---

## Task 3: RemoteWorkflow Data Class

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/RemoteWorkflow.java`

No unit test needed — pure data class, tested implicitly through `ArtifactoryClient`.

- [ ] **Step 1: Create RemoteWorkflow**

```java
package com.example.automation;

import com.example.automation.model.Workflow;

/** A workflow entry retrieved from Artifactory, including its raw JSON for later save. */
class RemoteWorkflow {

    final String filename;   // e.g. "my-workflow.json"
    final Workflow workflow;
    final String rawJson;

    RemoteWorkflow(String filename, Workflow workflow, String rawJson) {
        this.filename = filename;
        this.workflow = workflow;
        this.rawJson = rawJson;
    }
}
```

- [ ] **Step 2: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/RemoteWorkflow.java
git commit -m "feat: add RemoteWorkflow data class"
```

---

## Task 4: ArtifactoryClient

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ArtifactoryClient.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ArtifactoryClientTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import com.example.automation.ArtifactoryClient;
import com.example.automation.ArtifactoryException;
import com.example.automation.RemoteWorkflow;

public class ArtifactoryClientTest {

    // ── helpers ──────────────────────────────────────────────────────────────

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

    // ── listWorkflows ─────────────────────────────────────────────────────────

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

    // ── downloadWorkflow ──────────────────────────────────────────────────────

    @Test
    public void downloadWorkflow_returnsRawJson() throws Exception {
        ArtifactoryClient client = clientWith(FOLDER_JSON, 200, WORKFLOW_JSON, 200);
        String raw = client.downloadWorkflow("wf1.json");
        assertEquals(WORKFLOW_JSON, raw);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: compilation errors — `ArtifactoryClient` does not exist.

- [ ] **Step 3: Implement ArtifactoryClient**

```java
package com.example.automation;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.example.automation.model.Workflow;

/**
 * Fetches workflow listings and content from a JFrog Artifactory generic repository
 * using the Artifactory Storage REST API.
 */
class ArtifactoryClient {

    // ── Injectable HTTP layer (for testing) ───────────────────────────────────

    @FunctionalInterface
    interface Fetcher {
        HttpResponse fetch(String url, String authHeader) throws IOException;
    }

    static final class HttpResponse {
        final int status;
        final String body;
        HttpResponse(int status, String body) { this.status = status; this.body = body; }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String user;
    private final String apiKey;
    private final Fetcher fetcher;
    private final Gson gson = new Gson();

    /** Production constructor — reads credentials from environment variables. */
    ArtifactoryClient() {
        this(System.getenv("ARTIFACTORY_USER"),
             System.getenv("ARTIFACTORY_API_KEY"),
             ArtifactoryClient::httpFetch);
    }

    /** Test constructor — injects credentials and a fake fetcher. */
    ArtifactoryClient(String user, String apiKey, Fetcher fetcher) {
        this.user = user == null ? "" : user;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.fetcher = fetcher;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Lists all .json workflow files in the configured Artifactory folder.
     * Downloads and parses each file to read displayName and description.
     *
     * @throws ArtifactoryException on credential, network, or HTTP errors
     */
    List<RemoteWorkflow> listWorkflows() throws ArtifactoryException {
        String auth = basicAuth();
        String listingUrl = ArtifactoryConfig.listingUrl();
        HttpResponse resp = doFetch(listingUrl, auth);
        checkStatus(resp.status);

        JsonObject root = gson.fromJson(resp.body, JsonObject.class);
        JsonArray children = root.getAsJsonArray("children");
        List<RemoteWorkflow> result = new ArrayList<>();
        for (JsonElement el : children) {
            JsonObject child = el.getAsJsonObject();
            if (child.get("folder").getAsBoolean()) continue;
            String uri = child.get("uri").getAsString();          // e.g. "/wf1.json"
            String filename = uri.startsWith("/") ? uri.substring(1) : uri;
            if (!filename.endsWith(".json")) continue;

            String raw = downloadWorkflow(filename);
            Workflow wf = gson.fromJson(raw, Workflow.class);
            result.add(new RemoteWorkflow(filename, wf, raw));
        }
        return result;
    }

    /**
     * Downloads the raw JSON of a single workflow file.
     *
     * @param filename the filename within the configured folder (e.g. "my-wf.json")
     * @throws ArtifactoryException on credential, network, or HTTP errors
     */
    String downloadWorkflow(String filename) throws ArtifactoryException {
        String url = ArtifactoryConfig.folderUrl() + "/" + filename;
        HttpResponse resp = doFetch(url, basicAuth());
        checkStatus(resp.status);
        return resp.body;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String basicAuth() throws ArtifactoryException {
        if (user.isBlank() || apiKey.isBlank())
            throw new ArtifactoryException(
                "Artifactory credentials not configured (ARTIFACTORY_USER / ARTIFACTORY_API_KEY missing)");
        String token = Base64.getEncoder().encodeToString(
            (user + ":" + apiKey).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private HttpResponse doFetch(String url, String auth) throws ArtifactoryException {
        try {
            return fetcher.fetch(url, auth);
        } catch (IOException e) {
            throw new ArtifactoryException("Could not reach Artifactory: " + e.getMessage(), e);
        }
    }

    private static void checkStatus(int status) throws ArtifactoryException {
        if (status == 401 || status == 403)
            throw new ArtifactoryException("Artifactory authentication failed (HTTP " + status + ")");
        if (status >= 400)
            throw new ArtifactoryException("Artifactory returned HTTP " + status);
    }

    private static HttpResponse httpFetch(String urlStr, String authHeader) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("Authorization", authHeader);
        int status = conn.getResponseCode();
        try (InputStream is = status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
            String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new HttpResponse(status, body);
        } finally {
            conn.disconnect();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
mvn test -pl com.example.automation.parent/com.example.automation.tests -Dtest=ArtifactoryClientTest -q 2>&1 | tail -5
```

Expected: `Tests run: 11, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ArtifactoryClient.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ArtifactoryClientTest.java
git commit -m "feat: add ArtifactoryClient with injectable Fetcher for testability"
```

---

## Task 5: WorkflowPickerDialog — buildEntries merge logic + test

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowPickerDialog.java` (add `WorkflowEntry`, `SourceType`, `buildEntries()`)
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowPickerDialogMergeTest.java`

- [ ] **Step 1: Write the failing tests**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Expected: compilation errors — `WorkflowEntry`, `SourceType`, `buildEntries` don't exist yet.

- [ ] **Step 3: Add WorkflowEntry, SourceType, buildEntries to WorkflowPickerDialog**

Add these inside `WorkflowPickerDialog.java`, after the class-level fields and before the constructor:

```java
// ── Entry types (package-private for testing) ─────────────────────────────────

enum SourceType { LOCAL, ARTIFACTORY }

static class WorkflowEntry {
    final Workflow workflow;
    final SourceType source;
    final String rawJson;   // null for LOCAL; Artifactory JSON body for ARTIFACTORY
    final String filename;  // "workflowId.json" for LOCAL; server filename for ARTIFACTORY

    WorkflowEntry(Workflow workflow, SourceType source, String rawJson, String filename) {
        this.workflow = workflow;
        this.source = source;
        this.rawJson = rawJson;
        this.filename = filename;
    }
}

/**
 * Merges local and remote workflow lists into a single sorted entry list.
 * Entries are sorted by display name; LOCAL entries sort before ARTIFACTORY
 * entries with the same display name.
 */
static List<WorkflowEntry> buildEntries(List<Workflow> local, List<RemoteWorkflow> remote) {
    List<WorkflowEntry> result = new ArrayList<>();
    for (Workflow wf : local)
        result.add(new WorkflowEntry(wf, SourceType.LOCAL, null, wf.getWorkflowId() + ".json"));
    for (RemoteWorkflow rw : remote)
        result.add(new WorkflowEntry(rw.workflow, SourceType.ARTIFACTORY, rw.rawJson, rw.filename));
    result.sort(Comparator
        .comparing((WorkflowEntry e) -> e.workflow.getDisplayName() == null ? "" : e.workflow.getDisplayName())
        .thenComparingInt(e -> e.source == SourceType.LOCAL ? 0 : 1));
    return result;
}
```

Add these imports at the top of `WorkflowPickerDialog.java`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import com.example.automation.RemoteWorkflow;
```

- [ ] **Step 4: Run tests to verify they pass**

```
mvn test -pl com.example.automation.parent/com.example.automation.tests -Dtest=WorkflowPickerDialogMergeTest -q 2>&1 | tail -5
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowPickerDialog.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowPickerDialogMergeTest.java
git commit -m "feat: add WorkflowEntry, SourceType, buildEntries to WorkflowPickerDialog"
```

---

## Task 6: WorkflowPickerDialog — full UI overhaul

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowPickerDialog.java`

No SWT unit tests — the Artifactory URL is not reachable in the test environment.

- [ ] **Step 1: Replace WorkflowPickerDialog with the full implementation**

Replace the entire file content with:

```java
package com.example.automation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.example.automation.model.Workflow;

/**
 * Modal dialog that lists all available workflows (local and Artifactory) and
 * returns the workflow selected by the user. Selecting an Artifactory workflow
 * downloads and saves it to the local storage directory first.
 */
public class WorkflowPickerDialog extends TitleAreaDialog {

    // ── Entry types ───────────────────────────────────────────────────────────

    enum SourceType { LOCAL, ARTIFACTORY }

    static class WorkflowEntry {
        final Workflow workflow;
        final SourceType source;
        final String rawJson;   // null for LOCAL
        final String filename;

        WorkflowEntry(Workflow workflow, SourceType source, String rawJson, String filename) {
            this.workflow = workflow;
            this.source = source;
            this.rawJson = rawJson;
            this.filename = filename;
        }
    }

    static List<WorkflowEntry> buildEntries(List<Workflow> local, List<RemoteWorkflow> remote) {
        List<WorkflowEntry> result = new ArrayList<>();
        for (Workflow wf : local)
            result.add(new WorkflowEntry(wf, SourceType.LOCAL, null, wf.getWorkflowId() + ".json"));
        for (RemoteWorkflow rw : remote)
            result.add(new WorkflowEntry(rw.workflow, SourceType.ARTIFACTORY, rw.rawJson, rw.filename));
        result.sort(Comparator
            .comparing((WorkflowEntry e) -> e.workflow.getDisplayName() == null ? "" : e.workflow.getDisplayName())
            .thenComparingInt(e -> e.source == SourceType.LOCAL ? 0 : 1));
        return result;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final List<Workflow> localWorkflows;
    private final File storageDir;
    private TableViewer viewer;
    private Label warningLabel;
    private Workflow result;

    public WorkflowPickerDialog(Shell parent, List<Workflow> localWorkflows, File storageDir) {
        super(parent);
        this.localWorkflows = localWorkflows;
        this.storageDir = storageDir;
    }

    @Override
    public void create() {
        super.create();
        setTitle("Open Workflow");
        setMessage("Local: " + storageDir.getAbsolutePath());
        getButton(OK).setEnabled(false);
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Open Workflow");
    }

    @Override
    protected boolean isResizable() { return true; }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);

        // Warning label — hidden until an Artifactory error occurs
        warningLabel = new Label(area, SWT.WRAP);
        warningLabel.setBackground(area.getDisplay().getSystemColor(SWT.COLOR_YELLOW));
        GridData warnGd = new GridData(SWT.FILL, SWT.TOP, true, false);
        warnGd.exclude = true;
        warningLabel.setLayoutData(warnGd);
        warningLabel.setVisible(false);

        viewer = new TableViewer(area, SWT.FULL_SELECTION | SWT.BORDER | SWT.SINGLE);
        viewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        TableViewerColumn nameCol = new TableViewerColumn(viewer, SWT.NONE);
        nameCol.getColumn().setText("Name");
        nameCol.getColumn().setWidth(180);
        nameCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                Workflow wf = ((WorkflowEntry) element).workflow;
                return wf == null ? "" : (wf.getDisplayName() == null ? "" : wf.getDisplayName());
            }
        });

        TableViewerColumn descCol = new TableViewerColumn(viewer, SWT.NONE);
        descCol.getColumn().setText("Description");
        descCol.getColumn().setWidth(220);
        descCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                Workflow wf = ((WorkflowEntry) element).workflow;
                if (wf == null || wf.getDescription() == null) return "";
                return wf.getDescription();
            }
        });

        TableViewerColumn sourceCol = new TableViewerColumn(viewer, SWT.NONE);
        sourceCol.getColumn().setText("Source");
        sourceCol.getColumn().setWidth(100);
        sourceCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return ((WorkflowEntry) element).source == SourceType.LOCAL ? "Local" : "Artifactory";
            }
        });

        // Fetch Artifactory entries and populate the table
        List<RemoteWorkflow> remote = Collections.emptyList();
        try {
            remote = new ArtifactoryClient().listWorkflows();
        } catch (ArtifactoryException e) {
            showWarning(e.getMessage());
        }
        viewer.setInput(buildEntries(localWorkflows, remote));

        viewer.addSelectionChangedListener(e ->
            getButton(OK).setEnabled(!viewer.getStructuredSelection().isEmpty()));
        viewer.addDoubleClickListener(e -> {
            if (!viewer.getStructuredSelection().isEmpty()) okPressed();
        });

        return area;
    }

    @Override
    protected void okPressed() {
        WorkflowEntry entry = (WorkflowEntry)
            ((IStructuredSelection) viewer.getSelection()).getFirstElement();
        if (entry == null) return;

        if (entry.source == SourceType.ARTIFACTORY) {
            if (!downloadAndSave(entry)) return;  // abort; dialog stays open
        }

        result = entry.workflow;
        super.okPressed();
    }

    /** @return the workflow selected by the user, or {@code null} if cancelled. */
    public Workflow getResult() { return result; }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean downloadAndSave(WorkflowEntry entry) {
        File target = new File(storageDir, entry.filename);
        if (target.exists()) {
            boolean overwrite = MessageDialog.openQuestion(getShell(),
                "Overwrite existing workflow?",
                "A workflow named '" + entry.workflow.getDisplayName()
                    + "' already exists locally. Overwrite?");
            if (!overwrite) return false;
        }
        try {
            storageDir.mkdirs();
            try (FileWriter fw = new FileWriter(target, StandardCharsets.UTF_8)) {
                fw.write(entry.rawJson);
            }
        } catch (IOException e) {
            MessageDialog.openError(getShell(), "Download failed",
                "Failed to save workflow to local directory: " + e.getMessage());
            return false;
        }
        return true;
    }

    private void showWarning(String message) {
        GridData gd = (GridData) warningLabel.getLayoutData();
        gd.exclude = false;
        warningLabel.setVisible(true);
        warningLabel.setText("⚠ " + message);
        warningLabel.getParent().layout(true, true);
    }
}
```

- [ ] **Step 2: Run the merge tests to confirm the refactor didn't break them**

```
mvn test -pl com.example.automation.parent/com.example.automation.tests -Dtest=WorkflowPickerDialogMergeTest -q 2>&1 | tail -5
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/WorkflowPickerDialog.java
git commit -m "feat: extend WorkflowPickerDialog with Artifactory source column and download-on-open"
```

---

## Task 7: AutomationView — update onOpenWorkflow()

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java`

- [ ] **Step 1: Update onOpenWorkflow() to pass File storageDir**

The current `onOpenWorkflow()` passes a `String storagePath` and `List<Workflow>` to the dialog. The new dialog takes `List<Workflow>` and `File storageDir`. Update the method:

Find the existing `onOpenWorkflow()` method (around line 232) and replace it:

```java
private void onOpenWorkflow() {
    File storageDir;
    try {
        storageDir = resolvedStorageDir();
    } catch (Exception e) {
        Platform.getLog(getClass()).error("Failed to resolve storage directory", e);
        storageDir = new File(System.getProperty("user.home"), "automation");
    }
    try { workflows = repository().list(); } catch (Exception e) {
        Platform.getLog(getClass()).error("Failed to reload workflows", e);
    }
    WorkflowPickerDialog dialog = new WorkflowPickerDialog(getSite().getShell(), workflows, storageDir);
    if (dialog.open() == Window.OK) {
        currentWorkflow = dialog.getResult();
        viewer.setInput(currentWorkflow.getSteps());
        updateHeader();
        updateButtonStates();
    }
}
```

Add this import if not already present:

```java
import java.io.File;
```

- [ ] **Step 2: Run all tests to verify nothing is broken**

```
mvn test -pl com.example.automation.parent/com.example.automation.tests -q 2>&1 | tail -8
```

Expected: `Tests run: N, Failures: 0, Errors: 0` (N = existing count + new tests)

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java
git commit -m "feat: wire Artifactory-aware WorkflowPickerDialog into AutomationView"
```

---

## Task 8: Full build + update local p2 site

- [ ] **Step 1: Build from parent**

```
cd com.example.automation.parent && mvn clean install -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, `Tests run: N, Failures: 0, Errors: 0`

- [ ] **Step 2: Verify local p2 site has been refreshed**

```
find com.example.automation.parent/local-repo/p2/automation-plugin -type f
```

Expected: `features/` and `plugins/` directories present with a `1.2.x` timestamped JAR each.

- [ ] **Step 3: Commit the refreshed p2 site**

```
git add com.example.automation.parent/local-repo/p2/automation-plugin/
git commit -m "chore: update p2 update site after Artifactory workflow source feature"
```
