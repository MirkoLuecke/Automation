# Shell Command — PowerShell on Windows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `cmd.exe` with `powershell.exe -NonInteractive` as the Windows shell in the Shell Command action and update the README accordingly.

**Architecture:** Single-line change in `ShellCommandAction.execute()` plus a description string update. No new classes, no config schema changes, no OSGi dependencies.

**Tech Stack:** Java 17, Maven/Tycho 3.0.5, JUnit 4.

---

## Files

| File | Change |
|------|--------|
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java` | Switch `cmd.exe /c` → `powershell.exe -NonInteractive -Command`; update description string |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ShellCommandActionTest.java` | Add `description_mentionsPowershell` test |
| `README.md` | Update Shell Command section (line ~112) |

---

### Task 1: Switch Windows shell to PowerShell

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ShellCommandActionTest.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java`

- [ ] **Step 1: Write the failing test**

Open `ShellCommandActionTest.java`. Add this test at the end of the class (before the closing `}`):

```java
@Test
public void description_mentionsPowershell() {
    String desc = new ShellCommandAction().getDescription().toLowerCase();
    assertTrue(desc.contains("powershell"));
}
```

The full file after the edit:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.ShellCommandAction;

public class ShellCommandActionTest {

    @Test
    public void validate_rejectsBlankCommand() {
        List<String> errors = new ShellCommandAction().validate(Map.of("command", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankCommand() {
        List<String> errors = new ShellCommandAction().validate(Map.of("command", "echo hello"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_containsCommandAndWorkingDirKeys() {
        Map<String, String> cfg = new ShellCommandAction().getDefaultConfig();
        assertTrue(cfg.containsKey("command"));
        assertTrue(cfg.containsKey("workingDir"));
    }

    @Test
    public void description_mentionsPowershell() {
        String desc = new ShellCommandAction().getDescription().toLowerCase();
        assertTrue(desc.contains("powershell"));
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: BUILD FAILURE — `description_mentionsPowershell` fails because the current description is `"Executes a shell command."` which does not contain "powershell".

- [ ] **Step 3: Update `ShellCommandAction`**

Replace the entire content of `ShellCommandAction.java` with:

```java
package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class ShellCommandAction implements IAction {

    @Override public String getId()          { return "shell-command"; }
    @Override public String getName()        { return "Shell Command"; }
    @Override public String getDescription() {
        return "Executes a shell command. Uses powershell.exe on Windows and sh on Linux/macOS.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("command", "", "workingDir", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("command", "").isBlank())
            errors.add("command must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String command    = config.get("command");
        if (command == null || command.isBlank())
            throw new IllegalArgumentException("command must not be blank");
        String workingDir = config.getOrDefault("workingDir", "");

        List<String> cmd = System.getProperty("os.name").toLowerCase().contains("win")
            ? List.of("powershell.exe", "-NonInteractive", "-Command", command)
            : List.of("sh", "-c", command);

        File dir = workingDir.isBlank()
            ? new File(context.getWorkingDirectory())
            : new File(workingDir);

        ProcessRunner.run(cmd, dir, context);
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ShellCommandActionTest.java
git commit -m "feat: use powershell.exe on Windows in Shell Command action"
```

---

### Task 2: Update README

**Files:**
- Modify: `README.md` (line ~112)

- [ ] **Step 1: Update the Shell Command section**

Find this line in `README.md` (Shell Command subsection):

```
Executes a shell command. On Windows: `cmd.exe /c <command>`. On Linux/macOS: `sh -c <command>`.
```

Replace with:

```
Executes a shell command. On Windows: `powershell.exe -NonInteractive -Command <command>`. On Linux/macOS: `sh -c <command>`.
```

- [ ] **Step 2: Run the build to confirm nothing broke**

```
cd com.example.automation.parent
mvn clean verify -q
```

Expected: `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Commit**

```
git add README.md
git commit -m "docs: update Shell Command README to reflect PowerShell on Windows"
```
