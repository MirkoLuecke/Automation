# Built-in Action Implementations — Design Spec

**Date:** 2026-05-26
**Status:** Approved
**Sub-project:** 8 of 8 (Workflow Automation)

---

## Overview

Sub-project 8 adds nine concrete `IAction` implementations to the plugin. The extension point (`com.example.automation.actions`) and `IAction` / `IActionContext` interfaces are already in place. This sub-project registers all nine actions in `plugin.xml` and sets up the offline build infrastructure needed to compile against M2E.

---

## Architecture

### New package

All action classes live in `com.example.automation.actions` (new sub-package of `com.example.automation`). A helper class `MavenProgressParser` also lives there.

### New `Require-Bundle` entries

| Bundle | Used by | Source |
|---|---|---|
| `org.eclipse.core.resources` | RefreshAll, RefreshProject, ImportMavenProject | Standard Eclipse Platform — no download |
| `org.eclipse.debug.core` | ExecuteRunConfig, MavenRunWithProgress | Standard Eclipse Platform — no download |
| `org.eclipse.m2e.core` | ImportMavenProject, MavenUpdateProject | Not always present — bundled locally (see below) |

### Offline M2E build setup

A `local-repo/` directory at project root (sibling of `com.example.automation.parent/`) holds the M2E jar installed as a Maven artifact via `mvn install:install-file`. Three config changes make Tycho use it:

1. Parent `pom.xml`: add `<repository>` with layout `default` and URL `file:${project.basedir}/../local-repo`
2. Parent `pom.xml`: add `<pomDependencies>consider</pomDependencies>` to the Tycho compiler plugin configuration
3. Main plugin `pom.xml`: add `org.eclipse.m2e:org.eclipse.m2e.core` as `<dependency scope="provided">`

Tycho resolves `org.eclipse.m2e.core` from `Require-Bundle` against the local jar at build time. At runtime, M2E must be installed in Eclipse; if it is not, the two M2E actions fail fast via `validate()`.

### Runtime M2E guard

`ImportMavenProjectAction` and `MavenUpdateProjectAction` include this check at the top of `validate()`:

```java
Bundle m2eBundle = Platform.getBundle("org.eclipse.m2e.core");
if (m2eBundle == null || m2eBundle.getState() != Bundle.ACTIVE) {
    errors.add("M2E (Maven Integration for Eclipse) is not installed or not active.");
}
```

### plugin.xml

Each action gets its own `<extension>` element:

```xml
<extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.ShellCommandAction"/>
</extension>
```

Repeated for all nine action classes.

---

## Action Inventory

### `shell-command` — `ShellCommandAction`

**Config keys:**
- `command` (required) — shell command string
- `workingDir` (optional) — working directory; empty = workspace root via `ResourcesPlugin`

**Validate:** `command` non-blank.

**Execute:** `ProcessBuilder` with OS shell (`cmd.exe /c` on Windows, `sh -c` elsewhere). Stdout and stderr streamed to `IActionContext` streams. Progress: 0 on start, 100 on clean exit. Non-zero exit code throws.

---

### `git-clone` — `GitCloneAction`

**Config keys:**
- `url` (required) — repository URL
- `targetDir` (required) — local destination path
- `branch` (optional) — if non-blank, passed as `--branch <branch>`

**Validate:** `url` and `targetDir` non-blank.

**Execute:** Shells out `git clone [--branch <branch>] <url> <targetDir>`. Output streamed to context. Non-zero exit throws.

---

### `git-checkout` — `GitCheckoutAction`

**Config keys:**
- `repoDir` (required) — path to local repository
- `branch` (required) — branch name to check out

**Validate:** both non-blank.

**Execute:** Shells out `git -C <repoDir> checkout <branch>`. Output streamed to context.

---

### `refresh-all` — `RefreshAllAction`

**Config keys:** none.

**Execute:** `ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null)`.

---

### `refresh-project` — `RefreshProjectAction`

**Config keys:**
- `projectName` (required) — Eclipse project name as it appears in the workspace

**Validate:** non-blank.

**Execute:** `ResourcesPlugin.getWorkspace().getRoot().getProject(projectName).refreshLocal(IResource.DEPTH_INFINITE, null)`.

---

### `execute-run-config` — `ExecuteRunConfigAction`

**Config keys:**
- `configName` (required) — name of an existing Eclipse launch configuration
- `mode` (optional, default `run`) — launch mode: `run`, `debug`, or `profile`

**Validate:** `configName` non-blank.

**Execute:**
1. Find config by name via `DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations()` — first match wins.
2. Launch: `config.launch(mode, null, false, true)`.
3. Register listeners on `IProcess.getStreamsProxy()` output and error monitors, forwarding content to context streams.
4. Poll `ILaunch.isTerminated()` every 100 ms, respecting `context.isCancelled()`.
5. Non-zero exit value throws.

---

### `import-maven-project` — `ImportMavenProjectAction`

**Config keys:**
- `pomPath` (required) — path to `pom.xml` or to the directory containing one

**Validate:** non-blank; M2E bundle active (see runtime guard above).

**Execute:**
1. Resolve `pom.xml` file from `pomPath` (if directory, append `pom.xml`).
2. Read model: `MavenPlugin.getMavenModelManager().readMavenProject(pomFile, monitor)`.
3. Build project info list and call `MavenPlugin.getProjectConfigurationManager().importProjects(projectInfos, new ProjectImportConfiguration(), monitor)`.

---

### `maven-update-project` — `MavenUpdateProjectAction`

**Config keys:**
- `projectName` (required) — Eclipse project name

**Validate:** non-blank; M2E bundle active.

**Execute:**
```java
IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
MavenUpdateRequest request = new MavenUpdateRequest(project, false, false);
MavenPlugin.getProjectConfigurationManager()
    .updateProjectConfiguration(request, monitor);
```

---

### `maven-run-with-progress` — `MavenRunWithProgressAction`

**Config keys:**
- `configName` (required) — name of an existing Maven launch configuration

**Validate:** non-blank.

**Execute:** Same launch, stream, and poll mechanics as `execute-run-config`. Additionally, each output line is passed to `MavenProgressParser.parse(line)`:
- If it returns a value → `context.setProgress(value)`.
- If the line contains `BUILD SUCCESS` → `context.setProgress(100)`.
- If the line contains `BUILD FAILURE` → throw with the line as the message.

---

## Maven Progress Parsing — `MavenProgressParser`

```java
public static OptionalInt parse(String line)
```

Pure static method. Called per output line. Returns `OptionalInt.empty()` when no signal is found.

### Signal 1: `[N/M]` fraction (highest priority)

Pattern: `\[(\d+)/(\d+)\]` anywhere on a line. Appears on Maven's multi-module `[INFO] Building … [3/9]` lines.

- On a Building line: progress = `(N - 1) * 100 / M`
- On `BUILD SUCCESS` of that module: progress = `N * 100 / M`
- When N == M and BUILD SUCCESS appears: 100%

### Signal 2: Lifecycle phase milestones (fallback, single-module builds)

Matched against `[INFO] --- <plugin>:<version>:<goal>` lines:

| Goal substring | Progress |
|---|---|
| `:resources` | 15% |
| `:compile` | 30% |
| `:test-compile` | 45% |
| `:test` | 60% |
| `:package`, `:jar`, `:war`, `:ear` | 75% |
| `:install` | 90% |
| `:deploy` | 95% |

### Terminal signals

- Line contains `BUILD SUCCESS` → 100 (also terminates in caller)
- Line contains `BUILD FAILURE` → `OptionalInt.empty()` (caller throws)
- Unrecognised line → `OptionalInt.empty()`

---

## File Structure

| File | Role |
|---|---|
| `local-repo/` (project root) | M2E jar + Maven metadata for offline build |
| `com.example.automation/META-INF/MANIFEST.MF` | Add `org.eclipse.core.resources`, `org.eclipse.debug.core`, `org.eclipse.m2e.core` to `Require-Bundle`; export `com.example.automation.actions` |
| `com.example.automation/pom.xml` | Add M2E `<dependency scope="provided">` |
| `com.example.automation.parent/pom.xml` | Add local-repo `<repository>`; add `pomDependencies=consider` |
| `com.example.automation/plugin.xml` | Add 9 `<extension>` entries |
| `com.example.automation/src/…/actions/MavenProgressParser.java` | **New.** Pure static parser. |
| `com.example.automation/src/…/actions/ShellCommandAction.java` | **New.** |
| `com.example.automation/src/…/actions/GitCloneAction.java` | **New.** |
| `com.example.automation/src/…/actions/GitCheckoutAction.java` | **New.** |
| `com.example.automation/src/…/actions/RefreshAllAction.java` | **New.** |
| `com.example.automation/src/…/actions/RefreshProjectAction.java` | **New.** |
| `com.example.automation/src/…/actions/ExecuteRunConfigAction.java` | **New.** |
| `com.example.automation/src/…/actions/ImportMavenProjectAction.java` | **New.** |
| `com.example.automation/src/…/actions/MavenUpdateProjectAction.java` | **New.** |
| `com.example.automation/src/…/actions/MavenRunWithProgressAction.java` | **New.** |
| `com.example.automation.tests/src/…/tests/MavenProgressParserTest.java` | **New.** 6+ pure JUnit 4 tests. |
| `com.example.automation.tests/src/…/tests/ShellCommandActionTest.java` | **New.** validate + getDefaultConfig. |
| `com.example.automation.tests/src/…/tests/GitCloneActionTest.java` | **New.** |
| `com.example.automation.tests/src/…/tests/GitCheckoutActionTest.java` | **New.** |
| `com.example.automation.tests/src/…/tests/RefreshAllActionTest.java` | **New.** |
| `com.example.automation.tests/src/…/tests/RefreshProjectActionTest.java` | **New.** |
| `com.example.automation.tests/src/…/tests/ExecuteRunConfigActionTest.java` | **New.** |
| `com.example.automation.tests/src/…/tests/ImportMavenProjectActionTest.java` | **New.** |
| `com.example.automation.tests/src/…/tests/MavenUpdateProjectActionTest.java` | **New.** |
| `com.example.automation.tests/src/…/tests/MavenRunWithProgressActionTest.java` | **New.** |

---

## Testing Strategy

**Pure unit tests (no Eclipse runtime):** `MavenProgressParserTest` — 6+ tests covering `[N/M]` fraction, lifecycle phase milestones, `BUILD SUCCESS`, `BUILD FAILURE`, and unrecognised lines.

**OSGi-aware unit tests:** One class per action testing `validate()` and `getDefaultConfig()` only. M2E bundle check in `validate()` is exercised by the test runner's OSGi environment (M2E absent → the guard returns the expected error string). `execute()` methods require a live workspace, process environment, or M2E and are not unit-tested.

**Expected test count:** 42 existing + ~27 new ≈ 69 tests passing after all tasks.

---

## What Is NOT in Scope

- A UI for picking run configurations from a dropdown (config is a plain text field)
- Progress reporting for `execute-run-config` (only `maven-run-with-progress` parses progress)
- EGit/JGit integration (git actions shell out to system `git`)
- Bundling M2E's transitive runtime dependencies (M2E itself must be installed in Eclipse)
- Any new SWTBot UI tests
