# Action Package Coverage — Design Spec

## Goal

Bring the `com.example.automation.actions` package from its current low coverage (~30–40%)
to ~85–90% by adding `execute()` integration tests for every action whose infrastructure
is available in the OSGi test harness. No new test files; all additions go into the 8
existing action test files.

## Scope Decision (Option B)

### Included — execute() paths to test

| Action | Test approach |
|---|---|
| `WriteFileAction` | `writeFile()` (public static) + `execute()` with `TemporaryFolder` |
| `ShellCommandAction` | `execute()` with `echo`/`Write-Output`, temp workingDir |
| `GitCheckoutAction` | Create local git repo in `@Before`, checkout a branch, verify `.git/HEAD` |
| `GitCloneAction` | Create local bare repo in `@Before`, clone to temp dir, verify `.git` dir exists |
| `MavenRunWithProgressAction` | `execute()` with goal `--version`, verify progress reaches 100 |
| `SetMavenSettingsAction` | `execute()` with temp file, verify via `MavenPlugin.getMavenConfiguration()` |
| `MavenUpdateProjectAction` | Import small Maven project via `ImportMavenProjectAction`, then update |

Already have execute() coverage: `RefreshAllAction`, `RefreshProjectAction`, `ImportMavenProjectAction`,
`SetCodeFormatterAction`, `SetSaveActionsAction`. No changes needed to their test files.

### Excluded

| Class | Reason |
|---|---|
| `ProcessRunner` | Package-private — OSGi isolation prevents access from the test bundle |
| `ExecuteRunConfigAction.execute()` | Requires a pre-existing named Eclipse launch configuration |
| `SetActiveTargetPlatformAction.execute()` | Requires a `.target` file + PDE target resolution; harness may not have PDE active |

---

## Tech Stack

JUnit 4 · `@Rule TemporaryFolder` · inline anonymous `IActionContext` stubs ·
`ProcessBuilder` for local git repo setup · `MavenPlugin` (M2E, active in harness)

---

## Detailed Test Specifications

### `WriteFileActionTest` — add 2 tests

```java
@Rule public TemporaryFolder tmp = new TemporaryFolder();

// writeFile_createsFileWithContent
// File in nested subdir (verifies createDirectories logic)
File file = new File(tmp.getRoot(), "sub/dir/test.txt");
WriteFileAction.writeFile(file.getAbsolutePath(), "hello world");
assertEquals("hello world", Files.readString(file.toPath()));

// execute_writesFileAndProgressReaches100
List<Integer> progress = new ArrayList<>();
File file = new File(tmp.getRoot(), "out.txt");
new WriteFileAction().execute(
    Map.of("filePath", file.getAbsolutePath(), "content", "content"),
    trackingCtx(progress));
assertEquals("content", Files.readString(file.toPath()));
assertTrue(progress.contains(0));
assertTrue(progress.contains(100));
```

`trackingCtx(List<Integer>)` — private helper returning an anonymous `IActionContext` that
records `setProgress` calls. Same pattern as in `MavenRunWithProgressActionTest`.

### `ShellCommandActionTest` — add 1 test

```java
@Rule public TemporaryFolder tmp = new TemporaryFolder();

// execute_shellCommand_outputContainsHello
// On Windows: "Write-Output hello"; on POSIX: "echo hello"
boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
String cmd = isWindows ? "Write-Output hello" : "echo hello";
StringBuilder out = new StringBuilder();
new ShellCommandAction().execute(
    Map.of("command", cmd, "workingDir", tmp.getRoot().getAbsolutePath()),
    capturingCtx(out));
assertTrue(out.toString().trim().contains("hello"));
```

`capturingCtx(StringBuilder)` — anonymous `IActionContext` whose `getOutputStream()` returns
a stream that appends to the `StringBuilder`. `getErrorStream()` returns `nullOutputStream()`.

### `GitCheckoutActionTest` — add 1 test

```java
@Rule public TemporaryFolder tmp = new TemporaryFolder();
private File repo;

@Before
public void initRepo() throws Exception {
    repo = tmp.newFolder("repo");
    runGit(repo, "init");
    runGit(repo, "commit", "--allow-empty", "-m", "init");
    runGit(repo, "branch", "feature");
}

// execute_checkoutBranch_headPointsToFeature
new GitCheckoutAction().execute(
    Map.of("repoDir", repo.getAbsolutePath(), "branch", "feature"), nullCtx());
String head = Files.readString(new File(repo, ".git/HEAD").toPath()).trim();
assertTrue(head.endsWith("feature"));

// runGit: private helper — new ProcessBuilder("git", "-C", dir, ...args).start().waitFor()
private static void runGit(File dir, String... args) throws Exception {
    List<String> cmd = new ArrayList<>();
    cmd.add("git");
    cmd.add("-C");
    cmd.add(dir.getAbsolutePath());
    cmd.addAll(Arrays.asList(args));
    new ProcessBuilder(cmd).inheritIO().start().waitFor();
}
```

### `GitCloneActionTest` — add 1 test

```java
@Rule public TemporaryFolder tmp = new TemporaryFolder();
private File source;

@Before
public void createBareRepo() throws Exception {
    source = tmp.newFolder("source.git");
    runGit(source, "init", "--bare");
}

// execute_clonesRepo_targetDirHasGitDir
File target = new File(tmp.getRoot(), "clone");
new GitCloneAction().execute(
    Map.of("url", source.toURI().toString(),
           "targetDir", target.getAbsolutePath(),
           "branch", ""),
    nullCtx());
assertTrue(new File(target, ".git").isDirectory());

// runGit: same helper as GitCheckoutActionTest (copy into this file)
```

Note: `source.toURI().toString()` produces a `file:///...` URL accepted by `git clone`.
Cloning a bare repo that has no commits will succeed (empty clone); `.git` directory is created.

### `MavenRunWithProgressActionTest` — add 1 test

```java
@Rule public TemporaryFolder tmp = new TemporaryFolder();

// execute_mvnVersion_progressReaches100
List<Integer> progress = new ArrayList<>();
new MavenRunWithProgressAction().execute(
    Map.of("goals", "--version",
           "workingDir", tmp.getRoot().getAbsolutePath()),
    trackingCtx(progress));
assertTrue("progress must reach 100", progress.contains(100));
```

`mvn --version` exits 0 in ~1 s with no POM required. The existing `trackingCtx()` stub
(already defined in this file) records `setProgress` calls.

### `SetMavenSettingsTest` — add 1 test

```java
@Rule public TemporaryFolder tmp = new TemporaryFolder();
private String originalSettings;

@Before
public void captureOriginalSettings() {
    originalSettings = MavenPlugin.getMavenConfiguration().getUserSettingsFile();
}

@After
public void restoreOriginalSettings() throws Exception {
    MavenPlugin.getMavenConfiguration().setUserSettingsFile(originalSettings);
}

// execute_setsUserSettingsFile
File settings = tmp.newFile("settings.xml");
new SetMavenSettingsAction().execute(
    Map.of("filePath", settings.getAbsolutePath()), nullCtx());
assertEquals(settings.getAbsolutePath(),
    MavenPlugin.getMavenConfiguration().getUserSettingsFile());
```

`nullCtx()` is already defined in this file (added in the previous coverage sprint).

### `MavenUpdateProjectActionTest` — add 1 test

Requires a Maven project in the workspace. Reuses `ImportMavenProjectAction` (already tested)
to import a project from the same `test-import-pom.xml` classpath resource used by
`ImportMavenProjectActionTest`.

```java
@Rule public TemporaryFolder tmp = new TemporaryFolder();

private static final String PROJECT_NAME = "MavenUpdateProjectTest";

@Before
public void importMavenProject() throws Exception {
    IProject existing = ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
    if (existing.exists()) existing.delete(true, true, null);
    File dir = tmp.newFolder(PROJECT_NAME);
    try (InputStream src = MavenUpdateProjectActionTest.class
            .getResourceAsStream("/test-import-pom.xml")) {
        // Patch artifactId so this test's project is named PROJECT_NAME, not "test-import"
        String pom = new String(src.readAllBytes(), StandardCharsets.UTF_8)
            .replace("<artifactId>test-import</artifactId>",
                     "<artifactId>" + PROJECT_NAME + "</artifactId>");
        Files.writeString(new File(dir, "pom.xml").toPath(), pom);
    }
    new ImportMavenProjectAction().execute(
        Map.of("pomPath", dir.getAbsolutePath()), nullCtx());
}

@After
public void deleteProject() throws Exception {
    IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
    if (p.exists()) p.delete(true, true, null);
}

// execute_updatesExistingMavenProject_doesNotThrow
new MavenUpdateProjectAction().execute(
    Map.of("projectName", PROJECT_NAME), nullCtx());
// No exception = success; M2E re-reads pom.xml and reconfigures the project
```

`nullCtx()` is already defined in this file.

---

## File Change Summary

| Test file | New tests | Notes |
|---|---|---|
| `WriteFileActionTest` | 2 | Add `@Rule TemporaryFolder`, `trackingCtx` helper |
| `ShellCommandActionTest` | 1 | Add `@Rule TemporaryFolder`, `capturingCtx` helper |
| `GitCheckoutActionTest` | 1 | Add `@Rule TemporaryFolder`, `@Before initRepo`, `runGit` helper |
| `GitCloneActionTest` | 1 | Add `@Rule TemporaryFolder`, `@Before createBareRepo`, `runGit` helper |
| `MavenRunWithProgressActionTest` | 1 | Add `@Rule TemporaryFolder`; reuse existing `trackingCtx` |
| `SetMavenSettingsTest` | 1 | Add `@Rule TemporaryFolder`, `@Before`/`@After` to save/restore settings |
| `MavenUpdateProjectActionTest` | 1 | Add `@Rule TemporaryFolder`, `@Before`/`@After` using `ImportMavenProjectAction` |

Total: **8 new test methods** across 7 existing test files.

---

## Classpath Resource Reuse

`MavenUpdateProjectActionTest` reads `/test-import-pom.xml` from the classpath — already
present in the test bundle's `src/test/resources/` from `ImportMavenProjectActionTest`.
The `artifactId` is patched at runtime to avoid workspace name conflicts.

---

## Spec Self-Review

- No TBDs or placeholders.
- All stub patterns (`nullCtx`, `trackingCtx`, `capturingCtx`) are concrete — callers know exactly which methods they use.
- `runGit` helper duplicated in `GitCheckoutActionTest` and `GitCloneActionTest` (two files, not a shared utility); acceptable because the files are independent and sharing utilities across test files in OSGi requires additional classpath setup.
- `MavenUpdateProjectAction` test depends on `ImportMavenProjectAction` working — already proven by `ImportMavenProjectActionTest` passing.
- `mvn --version` works without a POM, so `MavenRunWithProgressAction` test does not pollute the workspace.
- `SetMavenSettingsAction` test restores the original settings in `@After` — no cross-test pollution.
- Excluded classes listed with rationale; no coverage gap is silently ignored.
