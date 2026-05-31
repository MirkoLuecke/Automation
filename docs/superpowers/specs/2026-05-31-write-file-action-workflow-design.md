# Write File Action and Setup Workflow

## Goal

Add a cross-platform "Write File" action and replace the two existing bundled example workflows with a single seven-step "Setup Automation Plugin" workflow that clones the repository, creates a project-local Maven settings file, imports the project, builds it, and refreshes the workspace.

## Architecture

One new `WriteFileAction` class registered as an OSGi extension. One new bundled workflow JSON file replacing the two existing ones. No changes to existing actions or the persistence layer.

## Tech Stack

Java 17, OSGi/Tycho, `IStringVariableManager` (Eclipse variable substitution), `java.nio.file.Files`, JUnit 4, JSON (Jackson).

---

## 1. Write File Action

### Class: `WriteFileAction`

**File:** `com.example.automation/src/main/java/com/example/automation/actions/WriteFileAction.java`

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

        Path path = Path.of(filePath);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);

        context.setProgress(100);
        context.getOutputStream().println("Written: " + path.toAbsolutePath());
    }
}
```

### plugin.xml registration

Add inside the existing `<extension point="com.example.automation.actions">` element:

```xml
<action class="com.example.automation.actions.WriteFileAction"/>
```

### README

Add to the Actions reference table:

| Write File | Writes text to a file. Creates parent directories if needed. Eclipse variables supported in both `filePath` and `content`. |

Add a detail section parallel to the existing Shell Command section:

**Write File config:**

| Key | Required | Description |
|-----|----------|-------------|
| `filePath` | Yes | Path to the file to write. Eclipse variables supported. |
| `content` | No | Text content. Eclipse variables supported. Multi-line supported via the built-in multi-line editor. |

---

## 2. Bundled Workflow

### Remove

- `src/main/resources/workflows/echo-workspace-info.json`
- `src/main/resources/workflows/refresh-workspace.json`

### Add

**File:** `src/main/resources/workflows/setup-automation-plugin.json`

```json
{
  "workflowId": "setup-automation-plugin",
  "displayName": "Setup Automation Plugin",
  "description": "Clones the Automation plugin repository, creates a project-local Maven settings file, imports the project into Eclipse, runs a full Maven build, and refreshes the workspace.",
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

---

## 3. Tests

**File:** `com.example.automation.tests/src/main/java/com/example/automation/tests/WriteFileActionTest.java`

```java
@Test
public void validate_rejectsBlankFilePath() { ... }

@Test
public void validate_acceptsNonBlankFilePath() { ... }

@Test
public void defaultConfig_containsFilePathAndContentKeys() { ... }

@Test
public void execute_writesFileWithContent() { ... }  // uses temp directory
```

---

## Out of Scope

- Appending to existing files (overwrite-only for now)
- Binary file writing
- Encoding options (UTF-8 only)
- Re-deployment of bundled workflows on update (existing installer runs once; user must clear the storage folder to re-trigger)
