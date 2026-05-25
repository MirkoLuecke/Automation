# Workflow Data Model and Persistence — Design Spec

**Date:** 2026-05-25
**Status:** Approved
**Sub-project:** 1 of 8 (Workflow Automation)

---

## Overview

Define the core data model for workflows and steps, and a persistence layer that stores one workflow per JSON file in `${user.home}/automation/`. This is the foundation all other sub-projects build on.

---

## Data Model

Package: `com.example.automation.model`

### `StepStatus` (enum)

Runtime-only. Never persisted.

| Value | Meaning |
|---|---|
| `WHITE` | Not yet executed (default) |
| `GREEN` | Executed successfully |
| `YELLOW` | Executed with warnings |
| `RED` | Executed with errors |

### `Step`

| Field | Type | Persisted | Notes |
|---|---|---|---|
| `actionId` | String | yes | Identifies the contributing action |
| `config` | Map\<String,String\> | yes | Action-specific key/value pairs |
| `status` | StepStatus | no (`transient`) | Runtime state, default WHITE |
| `progress` | int | no (`transient`) | 0–100, runtime only |

`transient` fields are excluded from Gson serialization automatically. No annotations required.

### `Workflow`

| Field | Type | Persisted | Notes |
|---|---|---|---|
| `workflowId` | String | yes | Used as filename (`{workflowId}.json`) |
| `displayName` | String | yes | Shown in UI ComboBox |
| `description` | String | yes | Mandatory |
| `steps` | List\<Step\> | yes | Ordered list |

---

## JSON Schema

File: `${user.home}/automation/{workflowId}.json`

```json
{
  "workflowId": "build-and-refresh",
  "displayName": "Build and Refresh",
  "description": "Runs a Maven build then refreshes the project.",
  "steps": [
    {
      "actionId": "com.example.automation.action.maven",
      "config": { "path": "${workspace_loc}/my-project" }
    },
    {
      "actionId": "com.example.automation.action.refresh",
      "config": { "projectName": "my-project" }
    }
  ]
}
```

---

## Persistence Layer

Package: `com.example.automation.persistence`

### `WorkflowRepository`

| Method | Behaviour |
|---|---|
| `list()` | Scans `~/automation/*.json`; skips malformed files (logs error, continues); creates directory if absent |
| `save(Workflow)` | Writes `~/automation/{workflowId}.json` atomically (write temp file, rename) |
| `load(String workflowId)` | Reads and deserializes one file |
| `delete(String workflowId)` | Deletes `~/automation/{workflowId}.json` |

### Threading

`WorkflowRepository` is single-threaded. It makes no concurrency guarantees. The UI layer is responsible for not calling `save()` concurrently on the same workflow. File I/O is synchronous (workflow files are a few KB — blocking time is negligible).

### Error Handling

| Situation | Behaviour |
|---|---|
| `~/automation/` does not exist | Created automatically on first `list()` or `save()` |
| JSON file is malformed | Logged via `ILog`; file skipped; other workflows still load |
| `workflowId` collision across files | Last-writer-wins on `save()`; `list()` returns both; deduplication is out of scope |

---

## Gson Bundling

Gson is not in the Eclipse 2023-06 target platform and must be embedded.

| Step | Detail |
|---|---|
| Download | `maven-dependency-plugin:copy` fetches `gson-2.10.1.jar` to `lib/` at build time |
| Classpath | `Bundle-ClassPath: ., lib/gson-2.10.1.jar` in `MANIFEST.MF` |
| Packaging | `lib/` added to `bin.includes` in `build.properties` |

---

## Package Layout

```
com.example.automation.model
    StepStatus.java       (enum)
    Step.java
    Workflow.java
com.example.automation.persistence
    WorkflowRepository.java
```

New test class added to `com.example.automation.tests`:
```
WorkflowRepositoryTest.java   (plain JUnit 4, no Eclipse runtime)
```

---

## Test Coverage

`WorkflowRepositoryTest` uses `TemporaryFolder` as the storage root (injected via constructor):

| Test | Verifies |
|---|---|
| Round-trip serialize/deserialize | All persisted fields survive save → load |
| `transient` fields absent | `status` and `progress` not written to JSON |
| `list()` returns all files | Multiple workflows found correctly |
| `save()` overwrites existing | Re-saving updates the file |
| Directory auto-created | First `save()` on missing dir succeeds |
| Malformed file skipped | `list()` returns remaining valid workflows |

---

## What Is NOT in Scope

- Status persistence
- File watching / live reload from external edits
- Workflow validation (done by action layer in sub-project 2)
- Concurrent access / locking
