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
| 1 | OS-based Artifactory URL derivation | `ArtifactoryConfig` (new) |
| 2 | Artifactory REST client | `ArtifactoryClient` (new) |
| 3 | Merged workflow list with Source column | `WorkflowPickerDialog` |
| 4 | Download-on-open with overwrite prompt | `WorkflowPickerDialog` |
| 5 | Unit tests | `ArtifactoryConfigTest`, `ArtifactoryClientTest`, `WorkflowPickerDialogTest` |

---

## URL Derivation

The Artifactory folder URL is never stored as a plain string in source code.
It is resolved at runtime using the following priority order:

| Priority | Source | Example |
|----------|--------|---------|
| 1 | `ARTIFACTORY_FOLDER_URL` environment variable | Full URL set by the user |
| 2 | `USERDNSDOMAIN` environment variable (Windows) | `SOMETHING.COMPANYNAME.SE` → extracts `companyname` |
| 3 | `InetAddress.getLocalHost().getCanonicalHostName()` | `host.companyname.de` → extracts `companyname` |
| 4 | `hostname` OS command | `host.companyname.de` → extracts `companyname` |

For sources 2–4, the company name is the second-to-last component of the
fully-qualified domain name, lowercased. It is inserted into the known URL
template to produce the final folder URL.

If none of the sources yields a usable value (e.g. outside the company
network), `folderUrl()` returns `null` and the dialog shows a warning with
instructions to set `ARTIFACTORY_FOLDER_URL`.

The two REST API URLs assembled at runtime:

| Purpose | Pattern |
|---------|---------|
| Folder listing | `<base>/artifactory/api/storage/<repo-path>` |
| File download | `<base>/artifactory/<repo-path>/{filename}` |

---

## Feature Specifications

### 1. URL Derivation — `ArtifactoryConfig`

```java
public final class ArtifactoryConfig {

    static final String ENV_FOLDER_URL = "ARTIFACTORY_FOLDER_URL";

    public static String folderUrl() {
        String direct = System.getenv(ENV_FOLDER_URL);
        if (direct != null && !direct.isBlank()) return direct;
        String companyName = extractCompanyName();
        return companyName != null ? buildFolderUrl(companyName) : null;
    }

    public static String listingUrl() {
        String folder = folderUrl();
        return folder != null ? buildListingUrl(folder) : null;
    }

    static String buildFolderUrl(String companyName) { /* assembles folder URL */ }
    static String buildListingUrl(String folderUrl)  { /* inserts /api/storage */ }

    static String extractCompanyName() {
        // Tries USERDNSDOMAIN, then InetAddress, then hostname command
    }

    static String companyNameFrom(String fqdn) {
        // Returns second-to-last dot-separated component, lowercased
    }
}
```

`folderUrl()` returns `null` when the company name cannot be determined.
Callers must handle null.

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

**URL injection:** `listingUrl` and `folderUrl` are resolved once at
construction time and stored as fields. A 3-argument test constructor accepts
a `Fetcher` and supplies fixed dummy URLs so tests are not affected by OS
hostname resolution.

#### `listWorkflows()` → `List<RemoteWorkflow>`

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
`RemoteWorkflow(filename, workflow, rawJson)`.

#### `downloadWorkflow(filename)` → `String` (raw JSON)

`GET <folderUrl>/<filename>`. Returns the raw JSON string.

#### Error mapping

| HTTP status / exception | `ArtifactoryException` message |
|------------------------|-------------------------------|
| `listingUrl == null` | `"Artifactory URL could not be determined … Set ARTIFACTORY_FOLDER_URL …"` |
| 401 / 403 | `"Artifactory authentication failed (HTTP <status>)"` |
| other 4xx / 5xx | `"Artifactory returned HTTP <status>"` |
| `IOException` | `"Could not reach Artifactory: <exception message>"` |

---

### 3. `WorkflowPickerDialog` — Merged List with Source Column

**New types:**

```java
public enum SourceType { LOCAL, ARTIFACTORY }

public static class WorkflowEntry {
    public final Workflow workflow;
    public final SourceType source;
    public final String rawJson;   // null for LOCAL
    public final String filename;
}
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
| URL cannot be determined (not on company network) | Warning label with hint to set `ARTIFACTORY_FOLDER_URL` |
| Credentials env vars missing | Warning label above table |
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

| Test | Covers |
|------|--------|
| `buildFolderUrl_returnsHttpsUrl` | Built URL starts with `https://` |
| `buildFolderUrl_containsCompanyName` | Company name appears in built URL |
| `buildListingUrl_containsApiStorage` | `/api/storage/` inserted correctly |
| `buildListingUrl_startsWithHttps` | Listing URL is valid https |
| `companyNameFrom_extractsSecondToLast` | `host.company.se` → `company` |
| `companyNameFrom_lowercasesResult` | `SOMETHING.COMPANY.SE` → `company` |
| `companyNameFrom_returnsNullForSinglePart` | `hostname` → `null` |
| `companyNameFrom_returnsNullForNull` | `null` → `null` |
| `companyNameFrom_handlesSubdomains` | `sub.domain.company.se` → `company` |

### `ArtifactoryClientTest`

Uses a test-injectable `Fetcher` interface so `HttpURLConnection` can
be replaced in tests. The 3-argument constructor supplies dummy URLs.

| Test | Covers |
|------|--------|
| `listWorkflows_parsesJsonFiles` | Valid folder listing JSON → correct entry list |
| `listWorkflows_filtersOutFolders` | `folder: true` entries excluded |
| `listWorkflows_filtersOutNonJson` | Non-`.json` entries excluded |
| `listWorkflows_throwsOnMissingUser` | Blank `ARTIFACTORY_USER` → `ArtifactoryException` |
| `listWorkflows_throwsOnMissingApiKey` | Blank `ARTIFACTORY_API_KEY` → `ArtifactoryException` |
| `listWorkflows_throwsOn401` | HTTP 401 → `ArtifactoryException` with auth message |
| `listWorkflows_401_messageContainsAuthFailed` | Message contains "authentication failed" |
| `listWorkflows_throwsOn500` | HTTP 500 → `ArtifactoryException` with status |
| `listWorkflows_500_messageContainsHttpStatus` | Message contains "500" |
| `listWorkflows_throwsOnIoException` | `IOException` → `ArtifactoryException` |
| `listWorkflows_ioException_messageContainsReason` | Message contains original reason |
| `downloadWorkflow_returnsRawJson` | 200 response → raw JSON string returned |

### `WorkflowPickerDialogMergeTest`

| Test | Covers |
|------|--------|
| `buildEntries_containsBothSources` | Local + remote entries combined; source labels correct |
| `buildEntries_sourceLabelsCorrect` | LOCAL has null rawJson; ARTIFACTORY has non-null rawJson |
| `buildEntries_localFirstForSameName` | Same display name → local entry sorts before Artifactory |
| `buildEntries_emptyRemote_onlyLocal` | Empty remote → only local entries |
| `buildEntries_emptyLocal_onlyRemote` | Empty local → only Artifactory entries |

---

## File Change Summary

| File | Change type |
|------|-------------|
| `ArtifactoryConfig.java` (new) | OS-based URL derivation; `ARTIFACTORY_FOLDER_URL` override |
| `ArtifactoryClient.java` (new) | Artifactory REST client with injectable `Fetcher` |
| `ArtifactoryException.java` (new) | Typed exception |
| `WorkflowPickerDialog.java` | Source column, merged list, download-on-open |
| `AutomationView.java` | `onOpenWorkflow()` passes `File storageDir` to dialog |
| `ArtifactoryConfigTest.java` (new) | URL builder and company-name extraction tests |
| `ArtifactoryClientTest.java` (new) | REST client unit tests via injectable `Fetcher` |
| `WorkflowPickerDialogMergeTest.java` (new) | `buildEntries()` merge logic unit tests |
