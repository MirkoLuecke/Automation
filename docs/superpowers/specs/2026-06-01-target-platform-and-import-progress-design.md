# Target Platform Activation & Import Progress Design

## Goal

Add a `SetActiveTargetPlatformAction` that loads, resolves, and activates a `.target` file with live progress; add a progress bridge to `ImportMavenProjectAction`; update the bundled `setup-automation-plugin.json` workflow to include the new step so Eclipse ends up with the correct target platform and no compile errors.

## Architecture

Three self-contained changes inside the `com.example.automation` bundle:

1. **New** `SetActiveTargetPlatformAction.java` — OSGi service lookup of `ITargetPlatformService`, resolve with bridging monitor, activate.
2. **Modify** `ImportMavenProjectAction.java` — replace `NullProgressMonitor` on `importProjects()` with a counting bridge.
3. **Modify** `setup-automation-plugin.json` — insert one new step between `import-maven-project` and `maven-run-with-progress`.
4. **Modify** `META-INF/MANIFEST.MF` — add `org.eclipse.pde.core` to `Require-Bundle`.

## SetActiveTargetPlatformAction

**File:** `com.example.automation/src/main/java/com/example/automation/actions/SetActiveTargetPlatformAction.java`

**Action ID:** `set-active-target-platform`

**Config:**

| Key | Description |
|-----|-------------|
| `targetFile` | Absolute path to a `.target` file. Variable-resolved by `WorkflowRunner` before `execute()` is called. |

**Validate:**
- `org.eclipse.pde.core` bundle must be ACTIVE.
- `targetFile` must not be blank.

**Execute sequence:**
1. Resolve `targetFile` → `File`; throw if it does not exist.
2. Get `ITargetPlatformService` via `ServiceTracker` on `org.eclipse.pde.core`'s `BundleContext`.
3. `service.getTarget(file.toURI())` → `ITargetHandle` → `handle.getTargetDefinition()` → `ITargetDefinition`.
4. Call `definition.resolve(bridgingMonitor)` — network-bound p2 resolution; bridge maps `beginTask/worked` to `setProgress(0..90)`.
5. Check returned `IStatus`; if severity is ERROR, throw with the status message.
6. Activate: create a `LoadTargetDefinitionJob(definition)` (from `org.eclipse.pde.ui.internal.preferences`), schedule it, and `join()` so the call blocks until Eclipse has applied the new target. Then `setProgress(100)`.

**Progress bridge** — private inner class `TargetMonitor implements IProgressMonitor`:
- `beginTask(name, total)` → `setProgress(0)`, record `total`.
- `worked(n)` → `done += n`, `setProgress(Math.min(90, done * 90 / total))`.
- `done()` → no-op (caller sets 100 after activation).
- All other methods are no-ops.

## ImportMavenProjectAction Progress Bridge

**File:** `com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java`

Replace the `NullProgressMonitor` passed to `importProjects()` with a private inner class `ImportMonitor implements IProgressMonitor`:
- `beginTask(name, total)` → `setProgress(0)`, record `total`.
- `worked(n)` → `done += n`, `setProgress(Math.min(99, done * 100 / total))`.
- `done()` → no-op (existing `setProgress(100)` call after `importProjects()` covers this).
- All other methods are no-ops.

The `scanner.run(...)` call keeps `NullProgressMonitor` — it is fast and has no meaningful work units.

## Workflow Update

**File:** `com.example.automation/src/main/resources/workflows/setup-automation-plugin.json`

Insert after the `import-maven-project` step and before `maven-run-with-progress`:

```json
{
  "actionId": "set-active-target-platform",
  "config": {
    "targetFile": "${project_loc:com.example.automation.parent}/platform.target"
  }
}
```

`${project_loc:com.example.automation.parent}` resolves to the filesystem location of the imported project, which is available after `import-maven-project` completes. The `platform.target` file is committed in the repo root of that project.

## MANIFEST.MF

Add to `Require-Bundle`:

```
org.eclipse.pde.core,
org.eclipse.pde.ui
```

`org.eclipse.pde.ui` is needed for `LoadTargetDefinitionJob`. Both are present in any standard Eclipse IDE installation that includes PDE.

## Testing

- Unit test `SetActiveTargetPlatformAction.validate()`: missing bundle (mocked), blank path.
- Integration / manual test: run `setup-automation-plugin` workflow end-to-end; verify progress increases monotonically during target resolution and import; verify no compile errors in imported projects after workflow completes.
