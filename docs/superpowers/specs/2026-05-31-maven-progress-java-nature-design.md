# Maven Progress Display and Java Nature Fix

## Goal

Fix two regressions: (1) the Maven Run with Progress step never updates the percentage in the Status column because ANSI escape codes corrupt Maven's output, and (2) imported Maven projects do not receive Java nature because the wrong M2E update API is used after import.

## Architecture

Two action fixes and two new tests. No new files beyond the test resource pom.xml.

## Tech Stack

Java 17, OSGi/Tycho, `org.eclipse.m2e.core`, `org.eclipse.jdt.core`, JUnit 4, Eclipse runtime (Tycho surefire).

---

## 1. Maven Progress Fix

**File:** `com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java`

**Root cause:** `MavenRunWithProgressAction` routes `mvn` through `powershell.exe`. PowerShell inherits a console PTY, so Maven's Jansi library detects a terminal and emits ANSI escape codes. The line `[INFO] --- maven-compiler-plugin:3.8.0:compile ... ---` arrives as `\033[1m[INFO]\033[0m \033[35m---\033[0m maven-compiler-plugin...`. The `MavenProgressParser` check `line.contains("[INFO] ---")` fails, so every phase line returns `empty()` and `setProgress` is never called with a useful value.

**Fix:**

Add a class-level constant:
```java
private static final Pattern ANSI_ESCAPE = Pattern.compile("\\u001B\\[[0-9;]*[mK]");
```

Extract the stdout-reading loop into a new package-private method:
```java
void processOutputStream(InputStream in, IActionContext context,
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
```

Replace the inline lambda body in `stdoutThread` with a call to `processOutputStream(process.getInputStream(), context, parser, buildFailed)`.

Note: the console now receives the cleaned (no-ANSI) line. Eclipse's `MessageConsole` is plain text and would show garbage characters from raw ANSI codes.

---

## 2. Java Nature Fix

**File:** `com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java`

**Root cause:** After `importProjects` returns, Maven nature is applied synchronously to each project, but the Tycho lifecycle mapping (which adds Java nature, source folders, and the Java builder) is scheduled as an async Eclipse workspace job. The current code calls `configManager.updateProjectConfiguration(project, monitor)` per project, which is a low-level single-project update that may not trigger the full Tycho lifecycle mapping. The `hasNature` check is fine (Maven nature is present by the time we check), but the update API is wrong.

**Fix:**

Replace the current per-project loop with a single batch call using `MavenUpdateRequest` — the exact API that Eclipse's "Maven → Update Project" menu item uses:

```java
List<IProject> toUpdate = new ArrayList<>();
for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
    try {
        if (project.isOpen() && project.hasNature("org.eclipse.m2e.core.maven2Nature"))
            toUpdate.add(project);
    } catch (CoreException ignored) {}
}
if (!toUpdate.isEmpty()) {
    MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(
        new MavenUpdateRequest(toUpdate, false),
        new NullProgressMonitor());
}
```

New imports needed: `org.eclipse.core.runtime.CoreException`, `org.eclipse.m2e.core.project.MavenUpdateRequest`.

---

## 3. Tests

### 3a. `MavenRunWithProgressActionTest` — two new tests

**File:** `com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java`

These tests call `processOutputStream` directly with an `InputStream` built from a known string — no Eclipse API, no Maven installation required.

```java
// Test 1: ANSI-coded [N/M] line → setProgress called with correct value
@Test
public void processOutputStream_stripsAnsiAndReportsProgress() throws Exception {
    List<Integer> seen = new ArrayList<>();
    IActionContext ctx = stubContextRecordingProgress(seen);
    String line = "\033[1m[INFO]\033[0m Building foo 1.0.0 \033[1m[2/5]\033[0m";
    new MavenRunWithProgressAction().processOutputStream(
        stream(line), ctx, new MavenProgressParser(), new boolean[]{false});
    assertEquals(List.of(20), seen); // (2-1)*100/5 = 20
}

// Test 2: ANSI-coded BUILD FAILURE line → buildFailed flag set
@Test
public void processOutputStream_detectsBuildFailureThroughAnsi() throws Exception {
    boolean[] buildFailed = {false};
    String line = "\033[31m[INFO] BUILD FAILURE\033[0m";
    new MavenRunWithProgressAction().processOutputStream(
        stream(line), stubContextRecordingProgress(new ArrayList<>()),
        new MavenProgressParser(), buildFailed);
    assertTrue(buildFailed[0]);
}
```

Helper methods in the test class:
```java
private static InputStream stream(String text) {
    return new ByteArrayInputStream((text + "\n").getBytes(StandardCharsets.UTF_8));
}

private static IActionContext stubContextRecordingProgress(List<Integer> out) {
    // anonymous IActionContext that records setProgress calls; all other methods no-op
}
```

### 3b. `ImportMavenProjectActionTest` — one new Eclipse-runtime test

**File:** `com.example.automation.tests/src/main/java/com/example/automation/tests/ImportMavenProjectActionTest.java`

**Test resource:** `com.example.automation.tests/src/main/resources/test-import-pom.xml`

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

```java
@Test
public void execute_givesJavaNatureToImportedProject() throws Exception {
    // Copy test-import-pom.xml to a temp directory
    File dir = tmpFolder.newFolder("test-import");
    copyResourceToFile("test-import-pom.xml", new File(dir, "pom.xml"));

    ImportMavenProjectAction action = new ImportMavenProjectAction();
    action.execute(Map.of("pomPath", dir.getAbsolutePath()), stubContext());

    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("test-import");
    try {
        assertTrue("project must exist in workspace", project.exists());
        assertTrue("project must have Java nature",
            project.hasNature("org.eclipse.jdt.core.javanature"));
    } finally {
        if (project.exists()) project.delete(true, true, null);
    }
}
```

The `@Rule public TemporaryFolder tmpFolder` provides cleanup. `stubContext()` returns an `IActionContext` with no-op methods (stdout → `System.out`, progress discarded).

---

## 4. Out of Scope

- Fixing `MavenProgressParser` accuracy for Tycho-specific output patterns (existing coverage is sufficient)
- Testing `ImportMavenProjectAction` with multi-module Tycho projects (requires network)
- Stripping ANSI from `stderr` output (stderr is piped through as-is to the error console)
