# Artifactory Workflow Source — Design Spec

## Goal

Extend the Open Workflow dialog to show workflows from both the local directory
and a company-internal JFrog Artifactory folder. A new "Source" column
distinguishes the two origins. Selecting an Artifactory workflow downloads it to
the local directory before opening it.

---

## Scope

| # | Feature | Files touched |
|---|---------|---------------|
| 1 | XOR-obfuscated Artifactory URL | `ArtifactoryConfig` (new) |
| 2 | Artifactory REST client | `ArtifactoryClient` (new) |
| 3 | Merged workflow list with Source column | `WorkflowPickerDialog` |
| 4 | Download-on-open with overwrite prompt | `WorkflowPickerDialog` |
| 5 | Unit tests | `ArtifactoryConfigTest`, `ArtifactoryClientTest`, `WorkflowPickerDialogTest` |

---

## URL Derivation

The URL provided is the browser UI URL:

```
https://artifactory.surv-xc-de.cap.saab.se/ui/repos/tree/General/abew-smr-sync/tools/eclipse/automation-workflows
```

The two REST API URLs derived from it:

| Purpose | URL |
|---------|-----|
| Folder listing | `https://artifactory.surv-xc-de.cap.saab.se/artifactory/api/storage/abew-smr-sync/tools/eclipse/automation-workflows` |
| File download | `https://artifactory.surv-xc-de.cap.saab.se/artifactory/abew-smr-sync/tools/eclipse/automation-workflows/{filename}` |

Only the REST API base (`https://artifactory.surv-xc-de.cap.saab.se/artifactory`) and
the repository path (`abew-smr-sync/tools/eclipse/automation-workflows`) need to be
stored — the two endpoint patterns are assembled at runtime.

---

## Feature Specifications

### 1. URL Obfuscation — `ArtifactoryConfig`

The full folder URL is stored as a `byte[]` literal, XOR-encrypted with a
separate hard-coded `byte[]` key. Neither the URL nor the key appears as a
readable string in source or in the compiled class file (no `String` literals).

```java
final class ArtifactoryConfig {
    private static final byte[] KEY = { /* key bytes */ };
    private static final byte[] ENC = { /* encrypted URL bytes */ };

    static String folderUrl() {
        byte[] out = new byte[ENC.length];
        for (int i = 0; i < ENC.length; i++)
            out[i] = (byte) (ENC[i] ^ KEY[i % KEY.length]);
        return new String(out, StandardCharsets.UTF_8);
    }
}
```

`folderUrl()` returns the plain folder URL:
`https://artifactory.surv-xc-de.cap.saab.se/artifactory/abew-smr-sync/tools/eclipse/automation-workflows`

The listing URL is `folderUrl()` prefixed with
`https://artifactory.surv-xc-de.cap.saab.se/artifactory/api/storage/` + the
repository-relative path extracted from `folderUrl()`.

---

### 2. Artifactory REST Client — `ArtifactoryClient`

No external dependencies — uses `HttpURLConnection` from the JDK. Uses existing
Gson (already bundled) for JSON parsing.

**Authentication:** `Authorization: Basic base64(user:apikey)` header, where
`user` = `System.getenv("ARTIFACTORY_USER")` and
`apikey` = `System.getenv("ARTIFACTORY_API_KEY")`.

If either env var is blank or null, `ArtifactoryClient` throws
`ArtifactoryException("Artifactory credentials not configured
(ARTIFACTORY_USER / ARTIFACTORY_API_KEY missing)")` before making any
connection.

**Timeouts:** 5 s connect, 10 s read.

#### `listWorkflows()` → `List<ArtifactoryWorkflowEntry>`

`GET <listingUrl>` (Artifactory storage API).

Expected response shape:
```json
{
  "children": [
    { "uri": "/my-workflow.json", "folder": false },
    { "uri": "/other.json",       "folder": false }
  ]
}
```

Filters to entries where `folder == false` and `uri` ends with `.json`. For
each, downloads the file to read its `displayName` and `description` fields
(same Gson model as local workflows). Returns a list of
`ArtifactoryWorkflowEntry(filename, displayName, description, rawJson)`.

#### `downloadWorkflow(filename)` → `String` (raw JSON)

`GET <folderUrl>/<filename>`. Returns the raw JSON string.

#### Error mapping

| HTTP status / exception | `ArtifactoryException` message |
|------------------------|-------------------------------|
| 401 / 403 | `"Artifactory authentication failed (HTTP <status>)"` |
| other 4xx / 5xx | `"Artifactory returned HTTP <status>"` |
| `IOException` | `"Could not reach Artifactory: <exception message>"` |

---

### 3. `WorkflowPickerDialog` — Merged List with Source Column

**New types:**

```java
enum SourceType { LOCAL, ARTIFACTORY }

record WorkflowEntry(Workflow workflow, SourceType source, String rawJson /* null for LOCAL */) {}
```

**Column layout** (updated):

| Column | Content |
|--------|---------|
| Name | Workflow display name |
| Description | Workflow description |
| Source | `"Local"` or `"Artifactory"` |

**On dialog open:**

1. Load local workflows as today → add as `LOCAL` entries.
2. Call `ArtifactoryClient.listWorkflows()`.
   - On success: add returned entries as `ARTIFACTORY` entries. Entries with the
     same filename as a local workflow are still shown separately (both rows
     appear; the source column distinguishes them).
   - On `ArtifactoryException`: display a yellow warning label above the table
     with the exception message. Local entries are unaffected.
3. All entries are shown in a single flat list sorted by display name, with
   `LOCAL` entries appearing before `ARTIFACTORY` entries of the same name.

---

### 4. Download-on-Open Flow

Triggered when the user selects an `ARTIFACTORY` entry and clicks OK.

1. Use the `rawJson` already fetched during listing (no second network call for
   the common case).
2. Determine the target file path: `<localWorkflowDir>/<filename>`.
3. If the file already exists locally, show a confirmation dialog:
   > `"A workflow named '<displayName>' already exists locally. Overwrite?"`
   - **Cancel**: abort. The picker dialog stays open.
   - **OK**: proceed.
4. Write `rawJson` to the target file.
   - On `IOException`: show a modal error dialog:
     `"Failed to save workflow to local directory: <reason>"`. Picker stays open.
5. Load the workflow from disk using the existing `WorkflowRepository` and open
   it in the Automation view — identical to opening a local workflow.

If the user selects a `LOCAL` entry, behaviour is unchanged from today.

---

## Error Handling Summary

| Situation | Behaviour |
|-----------|-----------|
| Env vars missing | Warning label above table |
| Connection timeout / unreachable | Warning label above table |
| HTTP 401 / 403 | Warning label above table |
| Other HTTP error | Warning label above table |
| Download fails after OK | Modal error dialog; picker stays open |
| File write fails | Modal error dialog; picker stays open |

---

## Testing

The Artifactory URL is not reachable outside the company network. All tests are
unit tests; no SWTBot integration tests are added for this feature.

### `ArtifactoryConfigTest`

- `decrypt_roundtrip`: encrypts a known string with the key, decrypts it, asserts equality. Does not assert the URL value.

### `ArtifactoryClientTest`

Uses a test-injectable `ConnectionFactory` interface so `HttpURLConnection` can
be replaced in tests.

| Test | Covers |
|------|--------|
| `listWorkflows_parsesChildrenArray` | Valid folder listing JSON → correct entry list |
| `listWorkflows_filtersOutFolders` | `folder: true` entries excluded |
| `listWorkflows_filtersOutNonJson` | Non-`.json` entries excluded |
| `listWorkflows_throwsOnMissingEnvVars` | Blank `ARTIFACTORY_USER` → `ArtifactoryException` |
| `listWorkflows_throwsOn401` | HTTP 401 → `ArtifactoryException` with auth message |
| `listWorkflows_throwsOn500` | HTTP 500 → `ArtifactoryException` with status |
| `downloadWorkflow_returnsBody` | 200 response → raw JSON string returned |

### `WorkflowPickerDialogTest` (extend existing)

| Test | Covers |
|------|--------|
| `mergedList_containsBothSources` | Local + remote entries combined; source labels correct |
| `mergedList_localFirstOnSameName` | Same filename → local entry sorts before Artifactory |
| `artifactoryFailure_showsOnlyLocal` | Exception from client → only local entries present |

---

## File Change Summary

| File | Change type |
|------|-------------|
| `ArtifactoryConfig.java` (new) | XOR-obfuscated URL storage |
| `ArtifactoryClient.java` (new) | Artifactory REST client |
| `ArtifactoryException.java` (new) | Typed exception |
| `WorkflowPickerDialog.java` | Source column, merged list, download-on-open |
| `ArtifactoryConfigTest.java` (new) | Decrypt round-trip test |
| `ArtifactoryClientTest.java` (new) | REST client unit tests |
| `WorkflowPickerDialogTest.java` (new/extend) | Merge logic unit tests |
