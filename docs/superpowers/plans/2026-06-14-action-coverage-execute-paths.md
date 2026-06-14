# Action Package Execute() Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `execute()` integration tests for 7 action classes, bringing the actions package from ~30–40% coverage to ~85–90%.

**Architecture:** All additions go into 7 existing test files — no new files. Each test creates disposable infrastructure (temp directories, local git repos, workspace projects) in `@Before`/`@Rule`, calls `execute()` with an anonymous `IActionContext` stub, and asserts the observable side-effect. The implementation already exists; these tests exercise untested code paths.

**Tech Stack:** JUnit 4 · `@Rule TemporaryFolder` · `ProcessBuilder` for git setup · M2E `MavenPlugin` API · OSGi test harness (`eclipse-test-plugin`, `useUIHarness=true`)

**Build command (full reactor — always run this, not `-pl`):**
```
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

---

## Files Modified

| File | Location |
|---|---|
| `WriteFileActionTest.java` | `com.example.automation.tests/src/main/java/com/example/automation/tests/` |
| `ShellCommandActionTest.java` | same package |
| `GitCheckoutActionTest.java` | same package |
| `GitCloneActionTest.java` | same package |
| `MavenRunWithProgressActionTest.java` | same package |
| `SetMavenSettingsTest.java` | same package |
| `MavenUpdateProjectActionTest.java` | same package |

---

## Task 1: WriteFileAction — execute() and writeFile() tests

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WriteFileActionTest.java`

The existing file has 5 tests covering `validate()`, `getDefaultConfig()`, `getId()`, `getName()`. It has no test for `execute()` or the public static `writeFile()` method.

- [ ] **Step 1: Add imports and new tests to WriteFileActionTest.java**

Replace the import block and class body. The existing tests stay; add a `@Rule`, a `trackingCtx` helper, and two new test methods.

Open `WriteFileActionTest.java`. The current import block is:
```java
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.WriteFileAction;
```

Replace with:
```java
import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.WriteFileAction;
import com.example.automation.api.IActionContext;
```

Then add the following inside the class, after the existing `getName_returnsWriteFile` test:

```java
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writeFile_createsNestedFileWithContent() throws Exception {
        File file = new File(tmp.getRoot(), "sub/dir/test.txt");
        WriteFileAction.writeFile(file.getAbsolutePath(), "hello world");
        assertEquals("hello world", Files.readString(file.toPath()));
    }

    @Test
    public void execute_writesFileAndProgressReaches100() throws Exception {
        File file = new File(tmp.getRoot(), "out.txt");
        List<Integer> progress = new ArrayList<>();
        new WriteFileAction().execute(
            Map.of("filePath", file.getAbsolutePath(), "content", "content"),
            trackingCtx(progress));
        assertEquals("content", Files.readString(file.toPath()));
        assertTrue(progress.contains(0));
        assertTrue(progress.contains(100));
    }

    private static IActionContext trackingCtx(List<Integer> progress) {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        { progress.add(p); }
            @Override public boolean isCancelled()          { return false; }
        };
    }
```

- [ ] **Step 2: Build and verify both new tests pass**

```
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: BUILD SUCCESS. Search the output for `WriteFileActionTest` — 7 tests should pass (5 existing + 2 new).

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WriteFileActionTest.java
git commit -m "test: add execute() and writeFile() tests for WriteFileAction"
```

---

## Task 2: ShellCommandAction — execute() test

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ShellCommandActionTest.java`

The existing file has 7 tests. `execute()` has never been called from tests. `ProcessRunner` pipes `process.getInputStream()` to `context.getOutputStream()`, so a `ByteArrayOutputStream` captures the shell output.

- [ ] **Step 1: Add imports and new test to ShellCommandActionTest.java**

Replace the import block:
```java
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.ShellCommandAction;
```

With:
```java
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.ShellCommandAction;
import com.example.automation.api.IActionContext;
```

Add inside the class after the last existing test:

```java
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void execute_shellCommand_outputContainsHello() throws Exception {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String cmd = isWindows ? "Write-Output hello" : "echo hello";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ShellCommandAction().execute(
            Map.of("command", cmd, "workingDir", tmp.getRoot().getAbsolutePath()),
            capturingCtx(baos));
        assertTrue("output must contain 'hello'", baos.toString().trim().contains("hello"));
    }

    private static IActionContext capturingCtx(ByteArrayOutputStream baos) {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return baos; }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }
```

- [ ] **Step 2: Build and verify the new test passes**

```
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: BUILD SUCCESS. `ShellCommandActionTest` shows 8 tests passing.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ShellCommandActionTest.java
git commit -m "test: add execute() test for ShellCommandAction"
```

---

## Task 3: GitCheckoutAction — execute() test

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitCheckoutActionTest.java`

The existing file has 5 tests (validate + id/name). `execute()` runs `git -C <repoDir> checkout <branch>` via `ProcessRunner`. The test sets up a real local git repo in `@Before`.

- [ ] **Step 1: Add imports, @Before setup, helper, and test to GitCheckoutActionTest.java**

Replace the import block:
```java
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.GitCheckoutAction;
```

With:
```java
import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.GitCheckoutAction;
import com.example.automation.api.IActionContext;
```

Add inside the class after the last existing test:

```java
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File repo;

    @Before
    public void initRepo() throws Exception {
        repo = tmp.newFolder("repo");
        runGit(repo, "init");
        runGit(repo, "config", "user.email", "test@example.com");
        runGit(repo, "config", "user.name", "Test");
        runGit(repo, "commit", "--allow-empty", "-m", "init");
        runGit(repo, "branch", "feature");
    }

    @Test
    public void execute_checkoutBranch_headPointsToFeature() throws Exception {
        new GitCheckoutAction().execute(
            Map.of("repoDir", repo.getAbsolutePath(), "branch", "feature"),
            nullCtx());
        String head = Files.readString(new File(repo, ".git/HEAD").toPath()).trim();
        assertTrue("HEAD must reference feature branch", head.endsWith("feature"));
    }

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }

    private static void runGit(File dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(dir.getAbsolutePath());
        cmd.addAll(Arrays.asList(args));
        int exit = new ProcessBuilder(cmd).inheritIO().start().waitFor();
        if (exit != 0) throw new Exception("git " + Arrays.toString(args) + " exited with " + exit);
    }
```

- [ ] **Step 2: Build and verify the new test passes**

```
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: BUILD SUCCESS. `GitCheckoutActionTest` shows 6 tests passing.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitCheckoutActionTest.java
git commit -m "test: add execute() test for GitCheckoutAction"
```

---

## Task 4: GitCloneAction — execute() test

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitCloneActionTest.java`

The existing file has 5 tests. `execute()` runs `git clone <url> <targetDir>`. The test creates a bare repo as the clone source, clones it to a temp dir, and verifies a `.git` directory was created. A bare repo with no commits is a valid clone source — git exits 0 and prints a warning (discarded by `nullCtx`).

- [ ] **Step 1: Add imports, @Before setup, helper, and test to GitCloneActionTest.java**

Replace the import block:
```java
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.GitCloneAction;
```

With:
```java
import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.GitCloneAction;
import com.example.automation.api.IActionContext;
```

Add inside the class after the last existing test:

```java
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File source;

    @Before
    public void createBareRepo() throws Exception {
        source = tmp.newFolder("source.git");
        runGit(source, "init", "--bare");
    }

    @Test
    public void execute_clonesLocalRepo_targetDirHasGitDirectory() throws Exception {
        File target = new File(tmp.getRoot(), "clone");
        new GitCloneAction().execute(
            Map.of("url", source.toURI().toString(),
                   "targetDir", target.getAbsolutePath(),
                   "branch", ""),
            nullCtx());
        assertTrue(".git directory must exist in cloned repo",
            new File(target, ".git").isDirectory());
    }

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }

    private static void runGit(File dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(dir.getAbsolutePath());
        cmd.addAll(Arrays.asList(args));
        int exit = new ProcessBuilder(cmd).inheritIO().start().waitFor();
        if (exit != 0) throw new Exception("git " + Arrays.toString(args) + " exited with " + exit);
    }
```

- [ ] **Step 2: Build and verify the new test passes**

```
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: BUILD SUCCESS. `GitCloneActionTest` shows 6 tests passing.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitCloneActionTest.java
git commit -m "test: add execute() test for GitCloneAction"
```

---

## Task 5: MavenRunWithProgressAction — execute() test

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java`

The existing file has 4 tests, including `processOutputStream` tests that cover ANSI stripping and progress parsing. `execute()` itself is never called. The test uses `goals = "--version"` which exits 0 in ~1 s without a POM. The existing `stubContext(List<Integer>)` helper is reused.

`execute()` calls `context.setProgress(0)` at start and `context.setProgress(100)` after a successful exit, so `progress.contains(100)` confirms the full code path ran.

- [ ] **Step 1: Add TemporaryFolder rule and new test to MavenRunWithProgressActionTest.java**

The existing import block already has `java.io.OutputStream`, `java.util.ArrayList`, `java.util.List`, `java.util.Map`, and `com.example.automation.api.IActionContext`. Add only the missing imports:

Add to the import block (after `import java.io.OutputStream;`):
```java
import java.io.File;
```

Add after the existing imports block (before the class declaration):
```java
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
```

Add inside the class, after the existing `ESC` constant and before the first `@Test`:

```java
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
```

Add the new test after the existing `getId` and `getName` tests:

```java
    @Test
    public void execute_mvnVersion_progressReaches100() throws Exception {
        List<Integer> progress = new ArrayList<>();
        new MavenRunWithProgressAction().execute(
            Map.of("goals", "--version",
                   "workingDir", tmp.getRoot().getAbsolutePath()),
            stubContext(progress));
        assertTrue("progress must reach 100", progress.contains(100));
    }
```

The existing `stubContext` helper (already in the file) is:
```java
private static IActionContext stubContext(List<Integer> progress) {
    return new IActionContext() {
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
        @Override public void setProgress(int p)        { progress.add(p); }
        @Override public boolean isCancelled()          { return false; }
    };
}
```
Do not add it again — it already exists.

- [ ] **Step 2: Build and verify the new test passes**

```
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: BUILD SUCCESS. `MavenRunWithProgressActionTest` shows 5 tests passing. The test takes ~2–5 s due to spawning `mvn`.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java
git commit -m "test: add execute() test for MavenRunWithProgressAction"
```

---

## Task 6: SetMavenSettingsAction — execute() test

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetMavenSettingsTest.java`

The existing file has 5 tests covering `validate()`, `getDefaultConfig()`, `getId()`, `getName()`. `execute()` calls `MavenPlugin.getMavenConfiguration().setUserSettingsFile(filePath)`. M2E is active in the test harness. The `@Before`/`@After` saves and restores the original setting to prevent cross-test pollution.

- [ ] **Step 1: Add imports, @Before/@After, @Rule, nullCtx helper, and test to SetMavenSettingsTest.java**

Replace the import block:
```java
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.SetMavenSettingsAction;
```

With:
```java
import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.m2e.core.MavenPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.SetMavenSettingsAction;
import com.example.automation.api.IActionContext;
```

Add inside the class, after the class declaration opening brace and before the first `@Test`:

```java
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private String originalSettings;

    @Before
    public void captureOriginalSettings() {
        originalSettings = MavenPlugin.getMavenConfiguration().getUserSettingsFile();
    }

    @After
    public void restoreOriginalSettings() throws Exception {
        MavenPlugin.getMavenConfiguration().setUserSettingsFile(originalSettings);
    }

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }
```

Add the new test after the existing `getName_returnsSetMavenSettings` test:

```java
    @Test
    public void execute_setsUserSettingsFile() throws Exception {
        File settings = tmp.newFile("settings.xml");
        new SetMavenSettingsAction().execute(
            Map.of("filePath", settings.getAbsolutePath()), nullCtx());
        assertEquals(settings.getAbsolutePath(),
            MavenPlugin.getMavenConfiguration().getUserSettingsFile());
    }
```

- [ ] **Step 2: Build and verify the new test passes**

```
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: BUILD SUCCESS. `SetMavenSettingsTest` shows 6 tests passing.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetMavenSettingsTest.java
git commit -m "test: add execute() test for SetMavenSettingsAction"
```

---

## Task 7: MavenUpdateProjectAction — execute() test

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenUpdateProjectActionTest.java`

The existing file has 5 tests covering `validate()`, `getDefaultConfig()`, `getId()`, `getName()`. `execute()` calls `MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(...)` which requires a Maven project in the workspace.

**Important:** Do NOT use `@Before`/`@After` for the Maven import. The existing 5 tests are fast (pure method calls); adding a `@Before` that imports a Maven project would make each of them take 10–30 s. Instead, put all setup and teardown inline in the new test method using try/finally.

- [ ] **Step 1: Add imports, nullCtx helper, and test to MavenUpdateProjectActionTest.java**

Replace the import block:
```java
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.MavenUpdateProjectAction;
```

With:
```java
import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.ImportMavenProjectAction;
import com.example.automation.actions.MavenUpdateProjectAction;
import com.example.automation.api.IActionContext;
```

Add inside the class, after the class declaration opening brace and before the first `@Test`:

```java
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()          { return false; }
        };
    }
```

Add the new test after the existing `validate_acceptsNonBlankProjectName` test. All setup and teardown are inline — the existing tests are not affected:

```java
    @Test
    public void execute_updatesExistingMavenProject_doesNotThrow() throws Exception {
        String projectName = "maven-update-test";
        IProject existing = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (existing.exists()) existing.delete(true, true, null);

        File dir = tmp.newFolder(projectName);
        String pom =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project>\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.example.test</groupId>\n" +
            "  <artifactId>" + projectName + "</artifactId>\n" +
            "  <version>0.0.1-SNAPSHOT</version>\n" +
            "  <packaging>jar</packaging>\n" +
            "</project>\n";
        Files.writeString(new File(dir, "pom.xml").toPath(), pom, StandardCharsets.UTF_8);
        new ImportMavenProjectAction().execute(
            Map.of("pomPath", dir.getAbsolutePath()), nullCtx());

        try {
            new MavenUpdateProjectAction().execute(
                Map.of("projectName", projectName), nullCtx());
        } finally {
            IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
            if (p.exists()) p.delete(true, true, null);
        }
    }
```

- [ ] **Step 2: Build and verify the new test passes**

```
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: BUILD SUCCESS. `MavenUpdateProjectActionTest` shows 6 tests passing. This test may take 10–30 s because M2E resolves the Maven project configuration.

- [ ] **Step 3: Commit**

```
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenUpdateProjectActionTest.java
git commit -m "test: add execute() test for MavenUpdateProjectAction"
```

---

## Spec Coverage Verification

| Spec requirement | Task |
|---|---|
| `WriteFileAction.writeFile()` | Task 1 (`writeFile_createsNestedFileWithContent`) |
| `WriteFileAction.execute()` | Task 1 (`execute_writesFileAndProgressReaches100`) |
| `ShellCommandAction.execute()` | Task 2 (`execute_shellCommand_outputContainsHello`) |
| `GitCheckoutAction.execute()` | Task 3 (`execute_checkoutBranch_headPointsToFeature`) |
| `GitCloneAction.execute()` | Task 4 (`execute_clonesLocalRepo_targetDirHasGitDirectory`) |
| `MavenRunWithProgressAction.execute()` | Task 5 (`execute_mvnVersion_progressReaches100`) |
| `SetMavenSettingsAction.execute()` | Task 6 (`execute_setsUserSettingsFile`) |
| `MavenUpdateProjectAction.execute()` | Task 7 (`execute_updatesExistingMavenProject_doesNotThrow`) |
| `ProcessRunner` excluded | No task (package-private, OSGi isolation) |
| `ExecuteRunConfigAction.execute()` excluded | No task (requires pre-existing launch config) |
| `SetActiveTargetPlatformAction.execute()` excluded | No task (requires .target file + PDE resolution) |
