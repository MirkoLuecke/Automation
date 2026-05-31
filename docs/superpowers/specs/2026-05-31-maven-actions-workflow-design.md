# Maven Actions and Workflow Overhaul

## Goal

Fix the workflow storage / clone directory collision, add a `SetMavenSettingsAction`, rewrite `MavenRunWithProgressAction` to call `mvn` on the command line instead of launching a run configuration, and update the bundled Setup Automation Plugin workflow to use all three changes.

## Architecture

Two action changes (one new, one rewrite), one workflow JSON update. No new UI components, no persistence changes, no new OSGi bundles.

## Tech Stack

Java 17, OSGi/Tycho, `org.eclipse.m2e.core` (already a dependency — see `MavenUpdateProjectAction`), `ProcessBuilder`, `MavenProgressParser` (existing), JUnit 4.

---

## 1. `SetMavenSettingsAction`

**File:** `com.example.automation/src/main/java/com/example/automation/actions/SetMavenSettingsAction.java`

**Action ID:** `set-maven-settings` | **Name:** "Set Maven Settings"

**Description:** `"Sets the Maven user settings file in Eclipse's M2E configuration."`

**Config:**

| Key | Required | Description |
|-----|----------|-------------|
| `filePath` | Yes | Path to the settings.xml file. Eclipse variables resolved by WorkflowRunner before execute(). |

**Validate:** rejects blank `filePath`; also checks `org.eclipse.m2e.core` bundle is active (same guard pattern as `MavenUpdateProjectAction`).

**execute():**
```java
context.setProgress(0);
MavenPlugin.getMavenConfiguration().setUserSettingsFile(filePath);
context.getStdout().println("Maven user settings set to: "
    + Path.of(filePath).toAbsolutePath());
context.setProgress(100);
```

**plugin.xml:** add one `<extension point="com.example.automation.actions">` block after the `WriteFileAction` one.

---

## 2. `MavenRunWithProgressAction` — rewrite

**File:** `com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java`

Same action ID (`maven-run-with-progress`) and name ("Maven Run with Progress"). Config changes from `configName` to `goals` + `workingDir`.

**Description:** `"Runs a Maven build from the command line and tracks progress from output. Uses powershell.exe on Windows and sh on Linux/macOS."`

**Config:**

| Key | Required | Description |
|-----|----------|-------------|
| `goals` | Yes | Maven goals and arguments, e.g. `clean install` or `-s /path/settings.xml clean install`. |
| `workingDir` | No | Working directory. Defaults to the context working directory. |

**Validate:** rejects blank `goals`.

**execute() — process management:**

`ProcessRunner` cannot be reused here because it pipes via `transferTo` with no line-by-line access. The action manages its own process:

1. Build OS-appropriate command via `ShellCommandAction.buildCommand("mvn " + goals)`.
2. Start with `ProcessBuilder`; directory = `workingDir` if set, else `new File(context.getWorkingDirectory())`.
3. Start a thread to read `stdout` line-by-line: each line is written to `context.getStdout()`, checked for `"BUILD FAILURE"` (sets a volatile flag), and passed to `MavenProgressParser` to update progress.
4. Start a thread to pipe `stderr` to `context.getErrorStream()`.
5. Poll `process.waitFor(100, MILLISECONDS)` in a loop; call `process.destroyForcibly()` and return if `context.isCancelled()`.
6. Join both threads.
7. If BUILD FAILURE flag set → `throw new Exception("Maven build failed.")`.
8. If `process.exitValue() != 0` → `throw new Exception("mvn exited with code " + exit)`.
9. `context.setProgress(100)`.

---

## 3. Workflow — `setup-automation-plugin.json`

**File:** `com.example.automation/src/main/resources/workflows/setup-automation-plugin.json`

8 steps (was 7). Changes from previous version:
- Clone `targetDir` renamed `Automation` → `automation-plugin` (fixes Windows case-insensitive collision with workflow storage dir `automation`).
- `import-maven-project` `pomPath` updated to `automation-plugin`.
- `set-maven-settings` step inserted after `write-file`.
- `shell-command` mvn step replaced by `maven-run-with-progress`.
- `maven-run-with-progress` `workingDir` updated to `automation-plugin`.

```json
{
  "workflowId": "setup-automation-plugin",
  "displayName": "Setup Automation Plugin",
  "description": "Clones the Automation plugin repository next to the workspace, creates a project-local Maven settings file, configures Eclipse Maven settings, imports the Maven project into Eclipse, runs a full Maven build, updates the Maven project configuration, and refreshes the workspace.",
  "steps": [
    {
      "actionId": "shell-command",
      "config": {
        "command": "echo Working directory: ${workspace_loc}/..",
        "workingDir": "${workspace_loc}/.."
      }
    },
    {
      "actionId": "write-file",
      "config": {
        "filePath": "${workspace_loc}/../settings.xml",
        "content": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd\">\n  <localRepository>${workspace_loc}/../.m2/repository</localRepository>\n</settings>"
      }
    },
    {
      "actionId": "set-maven-settings",
      "config": {
        "filePath": "${workspace_loc}/../settings.xml"
      }
    },
    {
      "actionId": "git-clone",
      "config": {
        "url": "https://github.com/MirkoLuecke/Automation",
        "targetDir": "${workspace_loc}/../automation-plugin"
      }
    },
    {
      "actionId": "import-maven-project",
      "config": {
        "pomPath": "${workspace_loc}/../automation-plugin/com.example.automation.parent/pom.xml"
      }
    },
    {
      "actionId": "maven-run-with-progress",
      "config": {
        "goals": "-s ${workspace_loc}/../settings.xml clean install",
        "workingDir": "${workspace_loc}/../automation-plugin/com.example.automation.parent"
      }
    },
    {
      "actionId": "maven-update-project",
      "config": {
        "projectName": "com.example.automation.parent"
      }
    },
    {
      "actionId": "refresh-all",
      "config": {}
    }
  ]
}
```

---

## 4. Tests

**New:** `SetMavenSettingsTest.java`
```java
validate_rejectsBlankFilePath()
validate_acceptsNonBlankFilePath()
defaultConfig_containsFilePathKey()
```

**Updated:** `MavenRunWithProgressActionTest.java` — replace `configName` tests with:
```java
validate_rejectsBlankGoals()
validate_acceptsNonBlankGoals()
defaultConfig_containsGoalsAndWorkingDirKeys()
```

---

## 5. README

- Update `### Maven Run with Progress` section: remove `configName` row, add `goals` and `workingDir` rows, update description.
- Add `### Set Maven Settings` section (after `### Maven Run with Progress`) documenting `filePath`.
- Update Bundled Workflows table description to mention the settings configuration step.

---

## Out of Scope

- Progress bar accuracy improvement (existing `MavenProgressParser` unchanged)
- Exposing Maven installation path as a preference (users are expected to have `mvn` on PATH)
- Appending to / merging settings.xml (write-file overwrites only)
