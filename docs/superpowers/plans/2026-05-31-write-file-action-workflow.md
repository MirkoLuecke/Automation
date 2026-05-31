# Write File Action and Setup Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a cross-platform `WriteFileAction` and replace the two bundled example workflows with a single seven-step "Setup Automation Plugin" workflow.

**Architecture:** One new `WriteFileAction` class (registered as an OSGi extension) that resolves Eclipse variables in both path and content before writing via `Files.writeString`. The file-writing logic is extracted to a package-private static helper so it can be unit-tested without an OSGi runtime. The bundled workflow JSON replaces the two existing ones.

**Tech Stack:** Java 17, OSGi/Tycho, `IStringVariableManager` (Eclipse variable substitution), `java.nio.file.Files`, JUnit 4 with `TemporaryFolder`.

---

## Files

| File | Change |
|------|--------|
| `com.example.automation/src/main/java/com/example/automation/actions/WriteFileAction.java` | Create |
| `com.example.automation.tests/src/main/java/com/example/automation/tests/WriteFileActionTest.java` | Create |
| `com.example.automation/plugin.xml` | Add one `<action>` element |
| `com.example.automation/src/main/resources/workflows/setup-automation-plugin.json` | Create |
| `com.example.automation/src/main/resources/workflows/echo-workspace-info.json` | Delete |
| `com.example.automation/src/main/resources/workflows/refresh-workspace.json` | Delete |
| `README.md` | Add Write File action section; update Bundled Workflows table |

---

### Task 1: WriteFileAction — TDD

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WriteFileActionTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/WriteFileAction.java`

- [ ] **Step 1: Write the failing tests**

Create `WriteFileActionTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.WriteFileAction;

public class WriteFileActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void validate_rejectsBlankFilePath() {
        List<String> errors = new WriteFileAction().validate(Map.of("filePath", "", "content", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankFilePath() {
        List<String> errors = new WriteFileAction().validate(Map.of("filePath", "settings.xml", "content", ""));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_containsFilePathAndContentKeys() {
        Map<String, String> cfg = new WriteFileAction().getDefaultConfig();
        assertTrue(cfg.containsKey("filePath"));
        assertTrue(cfg.containsKey("content"));
    }

    @Test
    public void writeFile_createsFileWithContent() throws Exception {
        Path file = tmp.newFolder().toPath().resolve("out.txt");
        WriteFileAction.writeFile(file.toString(), "hello world");
        assertEquals("hello world", Files.readString(file));
    }

    @Test
    public void writeFile_createsParentDirectories() throws Exception {
        Path file = tmp.newFolder().toPath().resolve("a/b/c/out.txt");
        WriteFileAction.writeFile(file.toString(), "nested");
        assertTrue(Files.exists(file));
        assertEquals("nested", Files.readString(file));
    }

    @Test
    public void writeFile_overwritesExistingFile() throws Exception {
        Path file = tmp.newFile("existing.txt").toPath();
        Files.writeString(file, "old");
        WriteFileAction.writeFile(file.toString(), "new");
        assertEquals("new", Files.readString(file));
    }
}
```

- [ ] **Step 2: Run the tests to confirm they fail**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: BUILD FAILURE — `WriteFileAction` does not exist yet.

- [ ] **Step 3: Create WriteFileAction**

Create `WriteFileAction.java`:

```java
package com.example.automation.actions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class WriteFileAction implements IAction {

    @Override public String getId()          { return "write-file"; }
    @Override public String getName()        { return "Write File"; }
    @Override public String getDescription() {
        return "Writes text content to a file, creating parent directories as needed. Eclipse variables are supported in both path and content.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("filePath", "", "content", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("filePath", "").isBlank())
            errors.add("filePath must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String filePath = svm.performStringSubstitution(config.getOrDefault("filePath", ""));
        String content  = svm.performStringSubstitution(config.getOrDefault("content", ""));
        writeFile(filePath, content);
        context.setProgress(100);
        context.getOutputStream().println("Written: " + Path.of(filePath).toAbsolutePath());
    }

    static void writeFile(String filePath, String content) throws Exception {
        Path path = Path.of(filePath);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: `Tests run: 90, Failures: 0, Errors: 0, Skipped: 0`

(84 existing + 6 new)

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/WriteFileAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WriteFileActionTest.java
git commit -m "feat: add WriteFileAction with Eclipse variable substitution in path and content"
```

---

### Task 2: Register action and update README

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml:72-74`
- Modify: `README.md`

- [ ] **Step 1: Register WriteFileAction in plugin.xml**

Open `com.example.automation.parent/com.example.automation/plugin.xml`. After the last `</extension>` block (the `MavenUpdateProjectAction` one, currently lines 72–74), add:

```xml
  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.WriteFileAction"/>
  </extension>
```

The file around that area should look like this after the edit:

```xml
  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.MavenUpdateProjectAction"/>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.WriteFileAction"/>
  </extension>

  <extension point="org.eclipse.ui.preferencePages">
```

- [ ] **Step 2: Add Write File to the Action Reference section in README.md**

Find the `### Refresh All` section in `README.md` (currently ends with "No configuration fields." followed by `---`). Insert the new section **after** `### Refresh All` and **before** the `---` separator:

```markdown
### Write File

Writes text content to a file. Creates parent directories if they do not already exist. Overwrites any existing file at the given path.

| Field | Required | Description |
|---|---|---|
| `filePath` | Yes | Path to the file to write. Eclipse variables are supported. |
| `content` | No | Text content to write. Eclipse variables are supported. Multi-line content is supported via the built-in multi-line editor. |
```

- [ ] **Step 3: Update Bundled Workflows table in README.md**

Find the `## Bundled Workflows` section (currently lists "Refresh Workspace" and "Echo Workspace Info"). Replace the table with:

```markdown
| Workflow | Description |
|---|---|
| Setup Automation Plugin | Clones the Automation plugin repository next to the workspace, creates a project-local Maven settings file, imports the Maven project into Eclipse, runs `mvn clean install`, updates the Maven project configuration, and refreshes the workspace. |
```

- [ ] **Step 4: Run the build to confirm nothing broke**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: `Tests run: 90, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/plugin.xml
git add README.md
git commit -m "feat: register WriteFileAction in plugin.xml; document in README"
```

---

### Task 3: Replace bundled workflows

**Files:**
- Delete: `com.example.automation.parent/com.example.automation/src/main/resources/workflows/echo-workspace-info.json`
- Delete: `com.example.automation.parent/com.example.automation/src/main/resources/workflows/refresh-workspace.json`
- Create: `com.example.automation.parent/com.example.automation/src/main/resources/workflows/setup-automation-plugin.json`

- [ ] **Step 1: Delete the two old workflow files**

```
git rm com.example.automation.parent/com.example.automation/src/main/resources/workflows/echo-workspace-info.json
git rm com.example.automation.parent/com.example.automation/src/main/resources/workflows/refresh-workspace.json
```

- [ ] **Step 2: Create setup-automation-plugin.json**

Create `com.example.automation.parent/com.example.automation/src/main/resources/workflows/setup-automation-plugin.json` with this exact content:

```json
{
  "workflowId": "setup-automation-plugin",
  "displayName": "Setup Automation Plugin",
  "description": "Clones the Automation plugin repository next to the workspace, creates a project-local Maven settings file, imports the Maven project into Eclipse, runs a full Maven build, updates the Maven project configuration, and refreshes the workspace.",
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
      "actionId": "git-clone",
      "config": {
        "url": "https://github.com/MirkoLuecke/Automation",
        "targetDir": "${workspace_loc}/../Automation"
      }
    },
    {
      "actionId": "import-maven-project",
      "config": {
        "pomPath": "${workspace_loc}/../Automation/com.example.automation.parent/pom.xml"
      }
    },
    {
      "actionId": "shell-command",
      "config": {
        "command": "mvn -s ${workspace_loc}/../settings.xml clean install",
        "workingDir": "${workspace_loc}/../Automation/com.example.automation.parent"
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

- [ ] **Step 3: Run the build to confirm nothing broke**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: `Tests run: 90, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 4: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/resources/workflows/setup-automation-plugin.json
git commit -m "feat: replace bundled example workflows with Setup Automation Plugin workflow"
```
