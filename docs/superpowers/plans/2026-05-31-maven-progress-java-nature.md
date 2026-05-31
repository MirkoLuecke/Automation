# Maven Progress Display and Java Nature Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Maven Run with Progress never showing a percentage (ANSI codes from PowerShell corrupt parser input) and imported Maven projects not receiving Java nature (wrong M2E update API used).

**Architecture:** Two independent bug fixes. Task 1 extracts the stdout-reading loop from `MavenRunWithProgressAction.execute()` into a public `processOutputStream` method and strips ANSI escape codes before parsing. Task 2 replaces per-project `updateProjectConfiguration(IProject, …)` calls in `ImportMavenProjectAction` with a single `MavenUpdateRequest` batch call — the same API Eclipse uses for "Maven → Update Project".

**Tech Stack:** Java 17, OSGi/Tycho 3.0.5, `org.eclipse.m2e.core`, `org.eclipse.jdt.core`, JUnit 4, Tycho surefire (Eclipse runtime tests).

---

## File Map

| File | Change |
|---|---|
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java` | Add `ANSI_ESCAPE` constant; extract `processOutputStream`; strip codes before parsing |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java` | Add 2 tests for ANSI stripping |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java` | Replace per-project loop with `MavenUpdateRequest` batch call |
| `com.example.automation.parent/com.example.automation.tests/src/main/resources/test-import-pom.xml` | New — minimal `jar` pom for import integration test |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ImportMavenProjectActionTest.java` | Add Java-nature integration test |

---

## Task 1: Strip ANSI codes in MavenRunWithProgressAction

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java`

### Background

`MavenRunWithProgressAction.execute()` runs `mvn` via `powershell.exe`. PowerShell inherits a console PTY, so Maven's Jansi library detects a terminal and emits ANSI escape codes. A line like `[INFO] --- maven-compiler-plugin:3.8.0:compile ... ---` arrives with surrounding escape sequences; `MavenProgressParser.parse()` checks `line.contains("[INFO] ---")` which then fails and returns `empty()`.

The fix: add a public method `processOutputStream` (public so the test bundle can call it) that strips ANSI codes before calling the parser.

- [ ] **Step 1: Add two failing tests to `MavenRunWithProgressActionTest`**

Replace the entire file content with:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.MavenProgressParser;
import com.example.automation.actions.MavenRunWithProgressAction;
import com.example.automation.api.IActionContext;

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

    // --- new tests ---

    @Test
    public void processOutputStream_stripsAnsiAndReportsProgress() throws Exception {
        List<Integer> seen = new ArrayList<>();
        // ANSI-coded line: [1m[INFO][0m Building foo 1.0.0 [1m[2/5][0m
        // After stripping: [INFO] Building foo 1.0.0 [2/5]  → (2-1)*100/5 = 20
        String line = "[1m[INFO][0m Building foo 1.0.0 [1m[2/5][0m";
        new MavenRunWithProgressAction().processOutputStream(
            asStream(line), stubContext(seen), new MavenProgressParser(), new boolean[]{false});
        assertEquals(List.of(20), seen);
    }

    @Test
    public void processOutputStream_detectsBuildFailureThroughAnsi() throws Exception {
        boolean[] failed = {false};
        String line = "[31m[INFO] BUILD FAILURE[0m";
        new MavenRunWithProgressAction().processOutputStream(
            asStream(line), stubContext(new ArrayList<>()), new MavenProgressParser(), failed);
        assertTrue(failed[0]);
    }

    // helpers

    private static InputStream asStream(String text) {
        return new ByteArrayInputStream((text + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static IActionContext stubContext(List<Integer> progress) {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        { progress.add(p); }
            @Override public boolean isCancelled()          { return false; }
        };
    }
}
```

- [ ] **Step 2: Run build to verify compilation error**

```
cd com.example.automation.parent
mvn clean install
```

Expected: compilation error in `com.example.automation.tests` — `processOutputStream` not found on `MavenRunWithProgressAction`. The build fails before running any tests.

- [ ] **Step 3: Add `ANSI_ESCAPE` constant and `processOutputStream` method; replace inline lambda**

Replace the entire `MavenRunWithProgressAction.java` with:

```java
package com.example.automation.actions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class MavenRunWithProgressAction implements IAction {

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\[[0-9;]*[mK]");

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
            try {
                processOutputStream(process.getInputStream(), context, parser, buildFailed);
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

    public void processOutputStream(InputStream in, IActionContext context,
                                    MavenProgressParser parser, boolean[] buildFailed)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = ANSI_ESCAPE.matcher(line).replaceAll("");
                context.getStdout().println(clean);
                if (clean.contains("BUILD FAILURE")) buildFailed[0] = true;
                OptionalInt progress = parser.parse(clean);
                if (progress.isPresent()) context.setProgress(progress.getAsInt());
            }
        }
    }
}
```

- [ ] **Step 4: Run build to verify both new tests pass**

```
cd com.example.automation.parent
mvn clean install
```

Expected output includes:
```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, ... in com.example.automation.tests.MavenRunWithProgressActionTest
```
and overall:
```
[INFO] BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java
git commit -m "fix: strip ANSI codes in MavenRunWithProgressAction so progress parser works via PowerShell"
```

---

## Task 2: Use MavenUpdateRequest to apply Java nature after import

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/resources/test-import-pom.xml`
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ImportMavenProjectActionTest.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java`

### Background

After `importProjects` returns, Maven nature is applied synchronously. But the Tycho lifecycle mapping that adds Java nature, source folders, and the Java builder is scheduled as an async workspace job. `updateProjectConfiguration(IProject, NullProgressMonitor)` is a low-level single-project API that may not fire the full lifecycle mapping. `MavenUpdateRequest` is the exact API the Eclipse UI uses for "Maven → Update Project" and runs synchronously and in batch.

- [ ] **Step 1: Create the test pom resource**

Create `com.example.automation.parent/com.example.automation.tests/src/main/resources/test-import-pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example.test</groupId>
  <artifactId>test-import</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <packaging>jar</packaging>
</project>
```

- [ ] **Step 2: Add the failing integration test to `ImportMavenProjectActionTest`**

Replace the entire file with:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.ImportMavenProjectAction;
import com.example.automation.api.IActionContext;

public class ImportMavenProjectActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @After
    public void cleanupWorkspace() throws Exception {
        IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject("test-import");
        if (p.exists()) p.delete(true, true, null);
    }

    @Test
    public void validate_blankPomPath_alwaysRejected() {
        List<String> errors = new ImportMavenProjectAction().validate(Map.of("pomPath", ""));
        assertTrue(errors.stream().anyMatch(e -> e.contains("pomPath")));
    }

    @Test
    public void defaultConfig_containsPomPathKey() {
        assertTrue(new ImportMavenProjectAction().getDefaultConfig().containsKey("pomPath"));
    }

    @Test
    public void execute_givesJavaNatureToImportedProject() throws Exception {
        File dir = tmp.newFolder("test-import");
        try (InputStream src = ImportMavenProjectActionTest.class
                .getResourceAsStream("/test-import-pom.xml")) {
            assertNotNull("test-import-pom.xml must be on classpath", src);
            Files.copy(src, new File(dir, "pom.xml").toPath());
        }

        new ImportMavenProjectAction().execute(
            Map.of("pomPath", dir.getAbsolutePath()), stubContext());

        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("test-import");
        assertTrue("project must exist in workspace", project.exists());
        assertTrue("project must have Java nature",
            project.hasNature("org.eclipse.jdt.core.javanature"));
    }

    private static IActionContext stubContext() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return System.out; }
            @Override public OutputStream getErrorStream()  { return System.err; }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }
}
```

- [ ] **Step 3: Run build to verify the new test fails**

```
cd com.example.automation.parent
mvn clean install
```

Expected: `execute_givesJavaNatureToImportedProject` fails with:
```
java.lang.AssertionError: project must have Java nature
```
(Project is imported but lacks `org.eclipse.jdt.core.javanature`.)

- [ ] **Step 4: Fix `ImportMavenProjectAction` — replace per-project loop with `MavenUpdateRequest`**

Replace the entire `ImportMavenProjectAction.java` with:

```java
package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.MavenUpdateRequest;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class ImportMavenProjectAction implements IAction {

    @Override public String getId()          { return "import-maven-project"; }
    @Override public String getName()        { return "Import Maven Project"; }
    @Override public String getDescription() { return "Imports an existing Maven project and all its modules into the Eclipse workspace."; }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("pomPath", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        Bundle m2e = Platform.getBundle("org.eclipse.m2e.core");
        if (m2e == null || m2e.getState() != Bundle.ACTIVE)
            errors.add("M2E (Maven Integration for Eclipse) is not installed or not active.");
        if (config.getOrDefault("pomPath", "").isBlank())
            errors.add("pomPath must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String pomPath = config.get("pomPath");
        if (pomPath == null || pomPath.isBlank())
            throw new IllegalArgumentException("pomPath must not be blank");

        File pomFile = new File(pomPath);
        if (pomFile.isDirectory()) pomFile = new File(pomFile, "pom.xml");
        if (!pomFile.exists())
            throw new Exception("pom.xml not found at: " + pomFile.getAbsolutePath());

        context.setProgress(0);
        LocalProjectScanner scanner = new LocalProjectScanner(
            List.of(pomFile.getParentFile().getAbsolutePath()),
            false,
            MavenPlugin.getMavenModelManager());
        scanner.run(new NullProgressMonitor());

        List<MavenProjectInfo> projects = scanner.getProjects();
        context.getStdout().println("Discovered " + projects.size() + " Maven project(s) to import.");
        MavenPlugin.getProjectConfigurationManager().importProjects(
            projects,
            new ProjectImportConfiguration(),
            new NullProgressMonitor());

        List<IProject> toUpdate = new ArrayList<>();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            try {
                if (project.isOpen() && project.hasNature("org.eclipse.m2e.core.maven2Nature"))
                    toUpdate.add(project);
            } catch (CoreException ignored) {}
        }
        if (!toUpdate.isEmpty()) {
            MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(
                new MavenUpdateRequest(toUpdate, false), new NullProgressMonitor());
            context.getStdout().println("Updated configuration for " + toUpdate.size() + " project(s).");
        }
        context.setProgress(100);
    }
}
```

- [ ] **Step 5: Run build to verify all tests pass**

```
cd com.example.automation.parent
mvn clean install
```

Expected output includes:
```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, ... in com.example.automation.tests.ImportMavenProjectActionTest
```
and overall:
```
Tests run: 99, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ImportMavenProjectActionTest.java
git add com.example.automation.parent/com.example.automation.tests/src/main/resources/test-import-pom.xml
git commit -m "fix: use MavenUpdateRequest after import so Tycho lifecycle mapping adds Java nature"
```
