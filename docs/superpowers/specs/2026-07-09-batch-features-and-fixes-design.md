# Batch Features and Fixes — Design Spec

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 10 features and fixes: remove variable resolution from CreateFile content, add SetXmlTagText action, insert-after-selection for new steps, persist/restore last opened workflow, replace Duplicate with Copy+Paste (system clipboard), fix ImportMavenProject PathType, add SetMavenPreferences action, add SetBuildAutomatically action, migrate WorkflowRunner to a background Job with workspace refresh, and add per-step retry config.

**Architecture:** Changes span the action layer (new actions, fixes), the step data model (retry fields), the execution engine (WorkflowRunner → Job), and the UI (toolbar, Properties view, startup restore). All changes are backward compatible with existing workflow JSON files.

**Tech Stack:** Eclipse SWT/JFace, Eclipse Job API (`Job`, `IProgressMonitor`), Eclipse Workspace API (`IWorkspace`, `IEclipsePreferences`, `InstanceScope`), SWT `Clipboard` / `TextTransfer`, Java DOM API (`DocumentBuilder`, `Transformer`), JUnit 4, SWTBot.

---

## Fix 1: Remove Variable Resolution from CreateFile Content

**File:** `WriteFileAction.java`

Remove the `EclipseVariables.resolve(content)` call on the file content. Only `filePath` is resolved. Content is written verbatim. This prevents spurious failures when content contains `${...}` patterns that are not Eclipse variables.

---

## Fix 2: ImportMavenProject PathType

**File:** `StepPropertySource.java`

The field name that maps to `ImportMavenProjectAction`'s directory config key is currently assigned `PathType.FILE`. Change it to `PathType.DIRECTORY` so `PathPickerDialog` opens a directory picker.

---

## Fix 3: Insert Step After Selection

**File:** `AutomationView.java`

In `onAddStep()`, if `tableViewer.getStructuredSelection()` is non-empty, find the maximum selected index and insert the new step at `maxIndex + 1`. If selection is empty, append at end (existing behavior). The index-finding logic already exists in `onDuplicate()` — extract it to a private `lastSelectedIndex()` helper returning `-1` when nothing is selected, and use it in both methods.

---

## Background Execution Model

**Files:** `WorkflowRunner.java` → replaced by `WorkflowJob.java`, `AutomationView.java`

### Why a plain `Job`, not `WorkspaceJob`

`WorkspaceJob` holds `IWorkspace.getRoot()` as its scheduling rule. `ImportMavenProjectAction` calls `IJobManager.join(MavenPlugin.getProjectConfigurationManager(), ...)` to wait for M2E's project-configuration jobs, which are themselves `WorkspaceJob`s. Waiting for them from inside a `WorkspaceJob` that already holds the workspace lock causes deadlock. A plain `Job` with `null` scheduling rule avoids this entirely while still appearing in Eclipse's Progress view.

### WorkflowJob design

```java
public class WorkflowJob extends Job {
    public WorkflowJob(List<Step> steps, String workflowName) {
        super("Running workflow: " + workflowName);
        setRule(null);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask(workflowName, steps.size());
        for (Step step : steps) {
            if (monitor.isCanceled()) return Status.CANCEL_STATUS;
            monitor.subTask(step.getDisplayName());
            try {
                executeWithRetry(step, monitor);
            } catch (Exception e) {
                // log to Eclipse error log; abort remaining steps
                return new Status(IStatus.ERROR, PLUGIN_ID, "Step failed: " + step.getDisplayName(), e);
            }
            refreshWorkspace(monitor);
            monitor.worked(1);
        }
        return Status.OK_STATUS;
    }

    private void executeWithRetry(Step step, IProgressMonitor monitor) throws Exception {
        try {
            step.execute(monitor);
        } catch (Exception e) {
            if (step.isRetryOnError()) {
                monitor.subTask("Retrying " + step.getDisplayName()
                    + " in " + step.getRetryWaitSeconds() + "s…");
                Thread.sleep(step.getRetryWaitSeconds() * 1000L);
                step.execute(monitor);  // second failure propagates
            } else {
                throw e;
            }
        }
    }

    private void refreshWorkspace(IProgressMonitor monitor) {
        try {
            ResourcesPlugin.getWorkspace().getRoot()
                .refreshLocal(IResource.DEPTH_INFINITE, monitor);
        } catch (CoreException ignored) {}
    }
}
```

`AutomationView` replaces the current `WorkflowRunner` thread with `new WorkflowJob(steps, name).schedule()`.

The workspace refresh fires after every step (cheap when nothing changed) and fixes the git clone → Maven build timing exception: Eclipse's resource model is up to date before the next step starts.

---

## Per-Step Retry Config

### Step data model

Two new optional fields added to `Step`:

| Field | Type | Default | JSON key |
|---|---|---|---|
| `retryOnError` | boolean | `false` | `"retryOnError"` |
| `retryWaitSeconds` | int | `10` | `"retryWaitSeconds"` |

Missing fields in existing JSON files deserialize to defaults — fully backward compatible.

### Properties view

Two new rows appear at the bottom of the Properties view for **every** step, below all action-specific config fields:

- **"Retry on error"** — checkbox (boolean cell editor), default unchecked
- **"Retry wait (seconds)"** — integer text field, default `10`, only editable when "Retry on error" is checked

### Execution

Handled inside `WorkflowJob.executeWithRetry()` as shown above. One retry only: if the second attempt also throws, the exception propagates and the workflow fails.

The `Thread.sleep()` is on the background job worker thread, not the SWT UI thread — the UI remains fully responsive during the wait.

---

## Copy + Paste (replaces Duplicate)

### Toolbar

Remove the Duplicate button. Add two buttons in its place:
- **Copy** — uses `ISharedImages.IMG_TOOL_COPY`
- **Paste** — uses `ISharedImages.IMG_TOOL_PASTE`

### Enabled state

- **Copy** enabled when `tableViewer.getStructuredSelection()` is non-empty; disabled otherwise.
- **Paste** enabled when `Clipboard.getContents(TextTransfer.getInstance())` returns text that parses as a JSON array of steps; disabled otherwise.
- Both states are evaluated in the `ISelectionChangedListener` on the table viewer.
- Paste state is also re-evaluated in `IPartListener2.partActivated()` (view focus gain), since clipboard content can change while Eclipse is in the background.
- If clipboard parse fails, the Paste button is silently disabled — no dialog.

### Clipboard format

Serialize selected `Step` objects to JSON using the existing step serializer (same format as the workflow file). Store via `SWT Clipboard` as `TextTransfer`. This is plain system-clipboard text, so cross-instance paste works natively.

### Paste insertion

Parsed steps are inserted after the current selection, using the same `lastSelectedIndex()` helper from Fix 3. If nothing is selected, steps are appended at end.

### Keyboard shortcuts

Wire `Ctrl+C` / `Ctrl+V` on the table widget to the same copy/paste handlers.

---

## Persist Last Opened Workflow

**Files:** `AutomationView.java`, plugin preference constants

On successful workflow load, store the file's absolute path to the plugin's `IEclipsePreferences` node (via `InstanceScope.INSTANCE`) under key `last.workflow.path`.

On `createPartControl()`, read `last.workflow.path`. If the value is non-empty and the file exists, load it automatically. If the file has been deleted or moved, silently skip — no error dialog.

Eclipse `IEclipsePreferences` persists across restarts automatically.

---

## SetXmlTagText Action

### Config fields (Properties view)

| Field | Type | Variable resolution |
|---|---|---|
| `filePath` | FILE picker | Yes — `EclipseVariables.resolve()` |
| `tagPath` | Text field | No |
| `value` | Text field | Yes — `EclipseVariables.resolve()` |

`tagPath` format: `/root/child/tag` — slash-separated tag names, not full XPath. Leading slash is optional.

### Execution

1. Resolve `filePath` and `value` via `EclipseVariables.resolve()`
2. Parse with `DocumentBuilder` (preserves XML comments as `Comment` nodes)
3. Split `tagPath` on `/`, discard empty parts (from leading slash)
4. Walk the path: at each level call `getElementsByTagName(tagName)` and take index 0; if not found → throw `Exception("Tag '<name>' not found in <filePath>")` — step fails, retry applies
5. Replace text content of **all** leaf nodes matching the final path segment
6. Serialize back with `Transformer`, `OutputKeys.INDENT = "no"` (minimizes whitespace reformatting; comments are preserved)

### XML comment preservation

Java's DOM `DocumentBuilder` retains XML comment nodes in the tree. The `Transformer` writes them back. Indentation of non-edited elements is preserved when `INDENT=no` is set; whitespace immediately adjacent to the edited tag may change slightly, but the document remains valid.

---

## SetMavenPreferences Action

### Config fields

Three independent comboboxes, each with options: `Yes` / `No` / `Do not change`:

| Field | Config key | M2E preference key |
|---|---|---|
| Download Artifact Sources | `downloadSources` | `eclipse.m2.downloadSources` |
| Download Artifact Javadoc | `downloadJavadoc` | `eclipse.m2.downloadJavadoc` |
| Repository index updates on startup | `updateIndexes` | `eclipse.m2.updateIndexes` |

"Do not change" serializes as the config key being absent from the step JSON. Existing workflows that omit any key leave that preference unchanged.

### Execution

Obtain `IEclipsePreferences` for `org.eclipse.m2e.core` via `InstanceScope.INSTANCE.getNode("org.eclipse.m2e.core")`. For each non-null field, call `prefs.putBoolean(key, value)`. Call `prefs.flush()` once after all changes.

---

## SetBuildAutomatically Action

### Config field

Single combobox `enabled`: `Yes` / `No`.

### Execution

```java
IWorkspace ws = ResourcesPlugin.getWorkspace();
IWorkspaceDescription desc = ws.getDescription();
desc.setAutoBuilding("Yes".equals(config.get("enabled")));
ws.setDescription(desc);
```

---

## File Summary

| File | Change |
|---|---|
| `WriteFileAction.java` | Remove `EclipseVariables.resolve()` from content |
| `StepPropertySource.java` | Fix ImportMavenProject field to `PathType.DIRECTORY`; add retry fields to Properties view |
| `AutomationView.java` | Fix `onAddStep()` insert-after-selection; extract `lastSelectedIndex()`; replace WorkflowRunner with WorkflowJob; add Copy+Paste toolbar buttons with enabled-state logic; persist/restore last workflow path; add `IPartListener2` for paste state refresh |
| `WorkflowRunner.java` | Delete (replaced by WorkflowJob) |
| `WorkflowJob.java` | New — `Job` subclass with null rule, progress reporting, per-step workspace refresh, retry logic |
| `Step.java` | Add `retryOnError` (boolean, default false), `retryWaitSeconds` (int, default 10) |
| `SetXmlTagTextAction.java` | New action |
| `SetMavenPreferencesAction.java` | New action |
| `SetBuildAutomaticallyAction.java` | New action |
| `ActionRegistry.java` (or equivalent) | Register 3 new actions with IDs `setXmlTagText`, `setMavenPreferences`, `setBuildAutomatically` |

---

## Testing

- `WriteFileActionTest` — assert content with `${...}` is written verbatim (no resolution)
- `WorkflowJobTest` — workspace refresh called after each step; retry: second attempt called after delay; cancellation stops loop
- `StepSerializationTest` — step with no retry fields deserializes with defaults; fields round-trip correctly
- `SetXmlTagTextActionTest` — tag replaced; missing tag throws; comments preserved in output
- `SetMavenPreferencesActionTest` — only non-null fields written to preferences
- `SetBuildAutomaticallyActionTest` — workspace description updated correctly
- `AutomationViewCopyPasteTest` (SWTBot) — copy serializes to clipboard; paste inserts after selection; paste disabled when clipboard is empty
