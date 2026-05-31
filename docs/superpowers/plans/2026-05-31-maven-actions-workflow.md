# Maven Actions and Workflow Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `SetMavenSettingsAction`, rewrite `MavenRunWithProgressAction` to call `mvn` on the command line, and update the bundled Setup Automation Plugin workflow to fix the directory collision and use both new actions.

**Architecture:** Two action changes (one new class, one rewrite), one workflow JSON update, one README update. No new OSGi bundles — `org.eclipse.m2e.core` is already a dependency. `MavenRunWithProgressAction` manages its own process (cannot reuse `ProcessRunner` because line-by-line stdout parsing is needed for progress tracking). It reuses `ShellCommandAction.buildCommand()` for OS-appropriate shell dispatch.

**Tech Stack:** Java 17, OSGi/Tycho, `org.eclipse.m2e.core` (`MavenPlugin.getMavenConfiguration()`), `ProcessBuilder`, `BufferedReader` for line-by-line stdout, `MavenProgressParser` (existing), JUnit 4.

---

## Files

| File | Change |
|------|--------|
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetMavenSettingsTest.java` | Create |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetMavenSettingsAction.java` | Create |
| `com.example.automation.parent/com.example.automation/plugin.xml` | Add one `<action>` extension |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java` | Rewrite (configName → goals, add defaultConfig test) |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java` | Rewrite (drop launch config, add ProcessBuilder + MavenProgressParser) |
| `com.example.automation.parent/com.example.automation/src/main/resources/workflows/setup-automation-plugin.json` | Update paths + add set-maven-settings step + use maven-run-with-progress |
| `README.md` | Add Set Maven Settings section; rewrite Maven Run with Progress section |

---

### Task 1: SetMavenSettingsAction — TDD

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetMavenSettingsTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetMavenSettingsAction.java`

- [ ] **Step 1: Write the failing tests**

Create `SetMavenSettingsTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.SetMavenSettingsAction;

public class SetMavenSettingsTest {

    @Test
    public void validate_rejectsBlankFilePath() {
        List<String> errors = new SetMavenSettingsAction().validate(Map.of("filePath", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankFilePath() {
        List<String> errors = new SetMavenSettingsAction().validate(
            Map.of("filePath", "/path/to/settings.xml"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_containsFilePathKey() {
        Map<String, String> cfg = new SetMavenSettingsAction().getDefaultConfig();
        assertTrue(cfg.containsKey("filePath"));
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: BUILD FAILURE — `SetMavenSettingsAction` does not exist yet.

- [ ] **Step 3: Create SetMavenSettingsAction**

Create `SetMavenSettingsAction.java`:

```java
package com.example.automation.actions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Platform;
import org.eclipse.m2e.core.MavenPlugin;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class SetMavenSettingsAction implements IAction {

    @Override public String getId()          { return "set-maven-settings"; }
    @Override public String getName()        { return "Set Maven Settings"; }
    @Override public String getDescription() {
        return "Sets the Maven user settings file in Eclipse's M2E configuration.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("filePath", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        Bundle m2e = Platform.getBundle("org.eclipse.m2e.core");
        if (m2e == null || m2e.getState() != Bundle.ACTIVE)
            errors.add("M2E (Maven Integration for Eclipse) is not installed or not active.");
        if (config.getOrDefault("filePath", "").isBlank())
            errors.add("filePath must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String filePath = config.getOrDefault("filePath", "");
        if (filePath.isBlank()) throw new IllegalArgumentException("filePath must not be blank");
        context.setProgress(0);
        MavenPlugin.getMavenConfiguration().setUserSettingsFile(filePath);
        context.getStdout().println("Maven user settings set to: "
            + Path.of(filePath).toAbsolutePath());
        context.setProgress(100);
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: `Tests run: 95, Failures: 0, Errors: 0, Skipped: 0` (92 existing + 3 new)

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetMavenSettingsAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetMavenSettingsTest.java
git commit -m "feat: add SetMavenSettingsAction"
```

---

### Task 2: Register SetMavenSettingsAction and add README section

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml`
- Modify: `README.md`

- [ ] **Step 1: Register in plugin.xml**

Open `com.example.automation.parent/com.example.automation/plugin.xml`. After the `WriteFileAction` extension block (lines 76–78), add:

```xml
  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.SetMavenSettingsAction"/>
  </extension>
```

The file around that area should look like this after the edit:

```xml
  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.WriteFileAction"/>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.SetMavenSettingsAction"/>
  </extension>

  <extension point="org.eclipse.ui.preferencePages">
```

- [ ] **Step 2: Add Set Maven Settings section to README.md**

Find the `### Write File` section in `README.md`. After its table and before the `---` separator, insert:

```markdown
### Set Maven Settings

Sets the Maven user settings file in Eclipse's M2E configuration. Equivalent to changing the **User Settings** field in **Window > Preferences > Maven**.

| Field | Required | Description |
|---|---|---|
| `filePath` | Yes | Path to the settings.xml file. Eclipse variables are supported. |

Requires M2E (Maven Integration for Eclipse) to be installed.
```

- [ ] **Step 3: Run the build**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: `Tests run: 95, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 4: Commit**

```
git add com.example.automation.parent/com.example.automation/plugin.xml
git add README.md
git commit -m "feat: register SetMavenSettingsAction in plugin.xml; document in README"
```

---

### Task 3: Rewrite MavenRunWithProgressAction — TDD

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java`

**Context:** `ProcessRunner.run()` cannot be reused here because it uses `InputStream.transferTo()` which gives no line-by-line access. `MavenRunWithProgressAction` manages its own `ProcessBuilder`, reads stdout line-by-line via `BufferedReader`, and feeds each line to `MavenProgressParser`. It reuses `ShellCommandAction.buildCommand(String)` (public static, added in the shell-powershell feature) for the OS-appropriate shell invocation.

- [ ] **Step 1: Rewrite the tests**

Replace the entire content of `MavenRunWithProgressActionTest.java` with:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.MavenRunWithProgressAction;

public class MavenRunWithProgressActionTest {

    @Test
    public void validate_rejectsBlankGoals() {
        List<String> errors = new MavenRunWithProgressAction().validate(Map.of("goals", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankGoals() {
        List<String> errors = new MavenRunWithProgressAction().validate(
            Map.of("goals", "clean install"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_containsGoalsAndWorkingDirKeys() {
        Map<String, String> cfg = new MavenRunWithProgressAction().getDefaultConfig();
        assertTrue(cfg.containsKey("goals"));
        assertTrue(cfg.containsKey("workingDir"));
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: BUILD FAILURE — `validate_acceptsNonBlankGoals` and `defaultConfig_containsGoalsAndWorkingDirKeys` fail because the action still has `configName` in its config.

- [ ] **Step 3: Rewrite MavenRunWithProgressAction**

Replace the entire content of `MavenRunWithProgressAction.java` with:

```java
package com.example.automation.actions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class MavenRunWithProgressAction implements IAction {

    @Override public String getId()          { return "maven-run-with-progress"; }
    @Override public String getName()        { return "Maven Run with Progress"; }
    @Override public String getDescription() {
        return "Runs a Maven build from the command line and tracks progress from output. Uses powershell.exe on Windows and sh on Linux/macOS.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("goals", "", "workingDir", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("goals", "").isBlank())
            errors.add("goals must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String goals = config.getOrDefault("goals", "");
        if (goals.isBlank()) throw new IllegalArgumentException("goals must not be blank");
        String workingDir = config.getOrDefault("workingDir", "");
        File dir = workingDir.isBlank()
            ? new File(context.getWorkingDirectory())
            : new File(workingDir);

        List<String> cmd = ShellCommandAction.buildCommand("mvn " + goals);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        context.setProgress(0);
        Process process = pb.start();

        MavenProgressParser parser = new MavenProgressParser();
        boolean[] buildFailed = {false};

        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    context.getStdout().println(line);
                    if (line.contains("BUILD FAILURE")) buildFailed[0] = true;
                    OptionalInt progress = parser.parse(line);
                    if (progress.isPresent()) context.setProgress(progress.getAsInt());
                }
            } catch (IOException ignored) {}
        });

        Thread stderrThread = new Thread(() -> {
            try { process.getErrorStream().transferTo(context.getErrorStream()); }
            catch (IOException ignored) {}
        });

        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);
        stdoutThread.start();
        stderrThread.start();

        while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
            if (context.isCancelled()) {
                process.destroyForcibly();
                stdoutThread.join();
                stderrThread.join();
                return;
            }
        }
        stdoutThread.join();
        stderrThread.join();

        if (buildFailed[0]) throw new Exception("Maven build failed.");
        int exit = process.exitValue();
        if (exit != 0) throw new Exception("mvn exited with code " + exit);
        context.setProgress(100);
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: `Tests run: 96, Failures: 0, Errors: 0, Skipped: 0` (95 from Task 1 − 2 old tests + 3 new = 96)

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java
git commit -m "feat: rewrite MavenRunWithProgressAction to call mvn on the command line"
```

---

### Task 4: Update README for Maven Run with Progress

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Replace the Maven Run with Progress section**

Find these lines in `README.md` (the entire Maven Run with Progress subsection, approximately lines 138–146):

```markdown
### Maven Run with Progress

Launches an existing Eclipse Maven launch configuration and monitors its output for build progress.

| Field | Required | Description |
|---|---|---|
| `configName` | Yes | Name of an Eclipse launch configuration (as shown in **Run > Run Configurations…**). |

Requires M2E (Maven Integration for Eclipse) to be installed.
```

Replace with:

```markdown
### Maven Run with Progress

Runs a Maven build from the command line and tracks progress from output. On Windows: `powershell.exe -NonInteractive -Command mvn <goals>`. On Linux/macOS: `sh -c mvn <goals>`.

| Field | Required | Description |
|---|---|---|
| `goals` | Yes | Maven goals and arguments, e.g. `clean install` or `-s /path/settings.xml clean install`. Eclipse variables are supported. |
| `workingDir` | No | Working directory. If blank, uses the **Default working directory** from the preference page. Eclipse variables are supported. |
```

- [ ] **Step 2: Run the build**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: `Tests run: 96, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Commit**

```
git add README.md
git commit -m "docs: update Maven Run with Progress README to reflect CLI execution"
```

---

### Task 5: Update bundled workflow JSON

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/resources/workflows/setup-automation-plugin.json`

Changes vs current file:
- `git-clone` `targetDir`: `Automation` → `automation-plugin`
- `import-maven-project` `pomPath`: `Automation` → `automation-plugin`
- Add `set-maven-settings` step after `write-file`
- Replace `shell-command` mvn step with `maven-run-with-progress`; update `workingDir` to `automation-plugin`

- [ ] **Step 1: Replace setup-automation-plugin.json**

Replace the entire content of `com.example.automation.parent/com.example.automation/src/main/resources/workflows/setup-automation-plugin.json` with:

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

- [ ] **Step 2: Run the build**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: `Tests run: 96, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/resources/workflows/setup-automation-plugin.json
git commit -m "feat: update Setup Automation Plugin workflow — fix dir collision, add set-maven-settings, use maven-run-with-progress"
```
