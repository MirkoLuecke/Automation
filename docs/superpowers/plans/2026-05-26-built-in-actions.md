# Built-in Action Implementations Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add nine concrete `IAction` implementations (shell command, git clone/checkout, workspace refresh, run config execution, Maven progress tracking, M2E project import/update) to the plugin, wired through the existing `com.example.automation.actions` extension point.

**Architecture:** All action classes live in a new `com.example.automation.actions` package. A package-private `ProcessRunner` helper handles OS process execution. `MavenProgressParser` is a stateful instance class that extracts 0-100 progress from Maven console output using `[N/M]` fraction lines and lifecycle phase milestones. M2E compile dependency is supplied offline via a project-local Maven repository (`local-repo/`) and Tycho's `pomDependencies=consider`.

**Tech Stack:** Java 17, Eclipse SWT/JFace, OSGi/Tycho 3.0.5, `org.eclipse.core.resources`, `org.eclipse.debug.core`, `org.eclipse.m2e.core`, JUnit 4.

---

## File Structure

| File | Role |
|---|---|
| `local-repo/` (inside `com.example.automation.parent/`) | M2E jar + Maven metadata for offline build |
| `com.example.automation.parent/pom.xml` | Add `local-m2e` repository + `pomDependencies=consider` |
| `com.example.automation/pom.xml` | Add M2E `<dependency scope="provided">` |
| `com.example.automation/META-INF/MANIFEST.MF` | Add `org.eclipse.core.resources`, `org.eclipse.debug.core`, `org.eclipse.m2e.core` to `Require-Bundle`; add `com.example.automation.actions` to `Export-Package` |
| `com.example.automation/plugin.xml` | Add 9 `<extension>` entries |
| `com.example.automation/src/…/actions/ProcessRunner.java` | **New.** Package-private helper: runs OS process, pipes stdout/stderr to `IActionContext`. |
| `com.example.automation/src/…/actions/MavenProgressParser.java` | **New.** Stateful instance: parses Maven output lines for progress 0–100. |
| `com.example.automation/src/…/actions/ShellCommandAction.java` | **New.** |
| `com.example.automation/src/…/actions/GitCloneAction.java` | **New.** |
| `com.example.automation/src/…/actions/GitCheckoutAction.java` | **New.** |
| `com.example.automation/src/…/actions/RefreshAllAction.java` | **New.** |
| `com.example.automation/src/…/actions/RefreshProjectAction.java` | **New.** |
| `com.example.automation/src/…/actions/ExecuteRunConfigAction.java` | **New.** |
| `com.example.automation/src/…/actions/MavenRunWithProgressAction.java` | **New.** |
| `com.example.automation/src/…/actions/ImportMavenProjectAction.java` | **New.** |
| `com.example.automation/src/…/actions/MavenUpdateProjectAction.java` | **New.** |
| `com.example.automation.tests/src/…/tests/MavenProgressParserTest.java` | **New.** 8 pure JUnit 4 tests. |
| `com.example.automation.tests/src/…/tests/ShellCommandActionTest.java` | **New.** 3 tests. |
| `com.example.automation.tests/src/…/tests/GitCloneActionTest.java` | **New.** 3 tests. |
| `com.example.automation.tests/src/…/tests/GitCheckoutActionTest.java` | **New.** 3 tests. |
| `com.example.automation.tests/src/…/tests/RefreshAllActionTest.java` | **New.** 2 tests. |
| `com.example.automation.tests/src/…/tests/RefreshProjectActionTest.java` | **New.** 2 tests. |
| `com.example.automation.tests/src/…/tests/ExecuteRunConfigActionTest.java` | **New.** 3 tests. |
| `com.example.automation.tests/src/…/tests/MavenRunWithProgressActionTest.java` | **New.** 2 tests. |
| `com.example.automation.tests/src/…/tests/ImportMavenProjectActionTest.java` | **New.** 2 tests. |
| `com.example.automation.tests/src/…/tests/MavenUpdateProjectActionTest.java` | **New.** 2 tests. |

All commands below run from `com.example.automation.parent/` unless stated otherwise.

---

### Task 1: Offline build setup

**Files:**
- Create: `com.example.automation.parent/local-repo/` (populated by Maven commands)
- Modify: `com.example.automation.parent/pom.xml`
- Modify: `com.example.automation.parent/com.example.automation/pom.xml`
- Modify: `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`

- [ ] **Step 1: Download M2E jar and install to local-repo**

Run from `com.example.automation.parent/`:

```
mvn dependency:copy -Dartifact=org.eclipse.m2e:org.eclipse.m2e.core:2.1.0:jar "-DoutputDirectory=.\tmp-m2e"
mvn install:install-file "-Dfile=.\tmp-m2e\org.eclipse.m2e.core-2.1.0.jar" -DgroupId=org.eclipse.m2e -DartifactId=org.eclipse.m2e.core -Dversion=2.1.0 -Dpackaging=jar "-DlocalRepositoryPath=.\local-repo" -DgeneratePom=true
Remove-Item -Recurse -Force .\tmp-m2e
```

Expected: `local-repo/org/eclipse/m2e/org.eclipse.m2e.core/2.1.0/` exists with a `.jar` and `.pom` file.

If 2.1.0 is not found on Maven Central, check `https://central.sonatype.com/search?q=g:org.eclipse.m2e+a:org.eclipse.m2e.core` for the latest 2.x version and substitute that version number throughout this task.

- [ ] **Step 2: Update parent pom.xml**

Replace the existing `<repositories>` block and the `target-platform-configuration` plugin section with:

```xml
  <repositories>
    <repository>
      <id>local-m2e</id>
      <url>file:${session.executionRootDirectory}/local-repo</url>
    </repository>
    <repository>
      <id>eclipse-2023-06</id>
      <layout>p2</layout>
      <url>https://download.eclipse.org/releases/2023-06</url>
    </repository>
    <repository>
      <id>swtbot</id>
      <layout>p2</layout>
      <url>https://download.eclipse.org/technology/swtbot/releases/latest/</url>
    </repository>
  </repositories>
```

And replace the `target-platform-configuration` plugin entry with:

```xml
      <plugin>
        <groupId>org.eclipse.tycho</groupId>
        <artifactId>target-platform-configuration</artifactId>
        <version>${tycho.version}</version>
        <configuration>
          <pomDependencies>consider</pomDependencies>
          <environments>
            <environment>
              <os>win32</os>
              <ws>win32</ws>
              <arch>x86_64</arch>
            </environment>
            <environment>
              <os>linux</os>
              <ws>gtk</ws>
              <arch>x86_64</arch>
            </environment>
            <environment>
              <os>macosx</os>
              <ws>cocoa</ws>
              <arch>x86_64</arch>
            </environment>
          </environments>
        </configuration>
      </plugin>
```

- [ ] **Step 3: Update plugin pom.xml**

In `com.example.automation/pom.xml`, add a `<dependencies>` section after `</build>`:

```xml
  <dependencies>
    <dependency>
      <groupId>org.eclipse.m2e</groupId>
      <artifactId>org.eclipse.m2e.core</artifactId>
      <version>2.1.0</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>
```

- [ ] **Step 4: Update MANIFEST.MF**

Replace `com.example.automation/META-INF/MANIFEST.MF` with:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Automation
Bundle-SymbolicName: com.example.automation;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Activator: com.example.automation.Activator
Require-Bundle: org.eclipse.ui,
 org.eclipse.ui.views,
 org.eclipse.core.runtime,
 org.eclipse.core.commands,
 org.eclipse.jface,
 org.eclipse.swt,
 org.eclipse.ui.console,
 org.eclipse.core.resources,
 org.eclipse.debug.core,
 org.eclipse.m2e.core
Bundle-RequiredExecutionEnvironment: JavaSE-17
Bundle-ActivationPolicy: lazy
Bundle-ClassPath: .,
 lib/gson-2.10.1.jar
Export-Package: com.example.automation,
 com.example.automation.api,
 com.example.automation.model,
 com.example.automation.persistence,
 com.example.automation.actions
```

- [ ] **Step 5: Verify build still compiles**

```
mvn verify -q
```

Expected: `Tests run: 42, Failures: 0, Errors: 0, Skipped: 0` — no new code yet, just infrastructure.

- [ ] **Step 6: Commit**

```
git add com.example.automation.parent/pom.xml
git add com.example.automation.parent/com.example.automation/pom.xml
git add com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF
git add com.example.automation.parent/local-repo/
git commit -m "chore: add offline M2E build setup and new Require-Bundle entries"
```

---

### Task 2: MavenProgressParser

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenProgressParserTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/MavenProgressParser.java`

- [ ] **Step 1: Create the test file**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.OptionalInt;

import org.junit.Test;

import com.example.automation.actions.MavenProgressParser;

public class MavenProgressParserTest {

    @Test
    public void parse_nMBuildingLine_returnsN1Fraction() {
        MavenProgressParser p = new MavenProgressParser();
        // (3-1)*100/9 = 22
        assertEquals(22, p.parse("[INFO] Building my-module 1.0.0 [3/9]").getAsInt());
    }

    @Test
    public void parse_buildSuccessAfterNM_returnsNFraction() {
        MavenProgressParser p = new MavenProgressParser();
        p.parse("[INFO] Building my-module 1.0.0 [3/9]");
        // 3*100/9 = 33
        assertEquals(33, p.parse("[INFO] BUILD SUCCESS").getAsInt());
    }

    @Test
    public void parse_buildSuccessNoNM_returns100() {
        assertEquals(100, new MavenProgressParser().parse("[INFO] BUILD SUCCESS").getAsInt());
    }

    @Test
    public void parse_compileGoal_returns30() {
        MavenProgressParser p = new MavenProgressParser();
        assertEquals(30, p.parse(
            "[INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) @ proj ---").getAsInt());
    }

    @Test
    public void parse_testCompileGoal_returns45() {
        MavenProgressParser p = new MavenProgressParser();
        assertEquals(45, p.parse(
            "[INFO] --- maven-compiler-plugin:3.8.0:testCompile (default-testCompile) @ proj ---").getAsInt());
    }

    @Test
    public void parse_testGoal_returns60() {
        MavenProgressParser p = new MavenProgressParser();
        assertEquals(60, p.parse(
            "[INFO] --- maven-surefire-plugin:2.22.2:test (default-test) @ proj ---").getAsInt());
    }

    @Test
    public void parse_buildFailure_returnsEmpty() {
        assertFalse(new MavenProgressParser().parse("[INFO] BUILD FAILURE").isPresent());
    }

    @Test
    public void parse_unrecognisedLine_returnsEmpty() {
        assertFalse(new MavenProgressParser().parse("[INFO] Scanning for projects...").isPresent());
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
mvn verify -q
```

Expected: `BUILD FAILURE` — `MavenProgressParser` does not exist yet.

- [ ] **Step 3: Create MavenProgressParser.java**

```java
package com.example.automation.actions;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenProgressParser {

    private static final Pattern NM = Pattern.compile("\\[(\\d+)/(\\d+)\\]");

    private int lastN = 0;
    private int lastM = 0;
    private boolean seenNM = false;

    public OptionalInt parse(String line) {
        if (line == null) return OptionalInt.empty();

        Matcher m = NM.matcher(line);
        if (m.find()) {
            lastN = Integer.parseInt(m.group(1));
            lastM = Integer.parseInt(m.group(2));
            seenNM = true;
            if (lastM > 0) return OptionalInt.of((lastN - 1) * 100 / lastM);
        }

        if (line.contains("BUILD SUCCESS")) {
            if (seenNM && lastM > 0) return OptionalInt.of(lastN * 100 / lastM);
            return OptionalInt.of(100);
        }
        if (line.contains("BUILD FAILURE")) return OptionalInt.empty();

        if (!line.contains("[INFO] ---")) return OptionalInt.empty();
        if (line.contains(":deploy"))       return OptionalInt.of(95);
        if (line.contains(":install"))      return OptionalInt.of(90);
        if (line.contains(":jar") || line.contains(":war") || line.contains(":ear"))
                                            return OptionalInt.of(75);
        if (line.contains(":testCompile"))  return OptionalInt.of(45);
        if (line.contains(":test"))         return OptionalInt.of(60);
        if (line.contains(":compile"))      return OptionalInt.of(30);
        if (line.contains(":resources"))    return OptionalInt.of(15);

        return OptionalInt.empty();
    }
}
```

- [ ] **Step 4: Run to confirm 50 tests pass**

```
mvn verify -q
```

Expected: `Tests run: 50, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/MavenProgressParserTest.java
git add com.example.automation/src/main/java/com/example/automation/actions/MavenProgressParser.java
git commit -m "feat: add MavenProgressParser with NM fraction and phase milestone parsing"
```

---

### Task 3: ProcessRunner helper + ShellCommandAction

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ShellCommandActionTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ProcessRunner.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java`

- [ ] **Step 1: Create the test file**

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
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
mvn verify -q
```

Expected: `BUILD FAILURE` — `ShellCommandAction` does not exist yet.

- [ ] **Step 3: Create ProcessRunner.java**

```java
package com.example.automation.actions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.example.automation.api.IActionContext;

class ProcessRunner {

    static void run(List<String> cmd, File dir, IActionContext context) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (dir != null) pb.directory(dir);
        context.setProgress(0);
        Process process = pb.start();
        Thread t1 = pipe(process.getInputStream(), context.getOutputStream());
        Thread t2 = pipe(process.getErrorStream(), context.getErrorStream());
        while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
            if (context.isCancelled()) {
                process.destroy();
                return;
            }
        }
        t1.join();
        t2.join();
        int exit = process.exitValue();
        if (exit != 0) throw new Exception("Process exited with code " + exit);
        context.setProgress(100);
    }

    private static Thread pipe(InputStream in, OutputStream out) {
        Thread t = new Thread(() -> {
            try { in.transferTo(out); } catch (IOException ignored) {}
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
```

- [ ] **Step 4: Create ShellCommandAction.java**

```java
package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class ShellCommandAction implements IAction {

    @Override public String getId()          { return "shell-command"; }
    @Override public String getName()        { return "Shell Command"; }
    @Override public String getDescription() { return "Executes a shell command."; }

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
        String workingDir = config.getOrDefault("workingDir", "");

        List<String> cmd = System.getProperty("os.name").toLowerCase().contains("win")
            ? List.of("cmd.exe", "/c", command)
            : List.of("sh", "-c", command);

        File dir = workingDir.isBlank()
            ? new File(ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString())
            : new File(workingDir);

        ProcessRunner.run(cmd, dir, context);
    }
}
```

- [ ] **Step 5: Run to confirm 53 tests pass**

```
mvn verify -q
```

Expected: `Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/ShellCommandActionTest.java
git add com.example.automation/src/main/java/com/example/automation/actions/ProcessRunner.java
git add com.example.automation/src/main/java/com/example/automation/actions/ShellCommandAction.java
git commit -m "feat: add ProcessRunner helper and ShellCommandAction"
```

---

### Task 4: GitCloneAction + GitCheckoutAction

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitCloneActionTest.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitCheckoutActionTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/GitCloneAction.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/GitCheckoutAction.java`

- [ ] **Step 1: Create the test files**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.GitCloneAction;

public class GitCloneActionTest {

    @Test
    public void validate_rejectsBlankUrl() {
        List<String> errors = new GitCloneAction().validate(
            Map.of("url", "", "targetDir", "/tmp/repo"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_rejectsBlankTargetDir() {
        List<String> errors = new GitCloneAction().validate(
            Map.of("url", "https://example.com/repo.git", "targetDir", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsUrlAndTargetDir() {
        List<String> errors = new GitCloneAction().validate(
            Map.of("url", "https://example.com/repo.git", "targetDir", "/tmp/repo"));
        assertTrue(errors.isEmpty());
    }
}
```

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.GitCheckoutAction;

public class GitCheckoutActionTest {

    @Test
    public void validate_rejectsBlankRepoDir() {
        List<String> errors = new GitCheckoutAction().validate(
            Map.of("repoDir", "", "branch", "main"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_rejectsBlankBranch() {
        List<String> errors = new GitCheckoutAction().validate(
            Map.of("repoDir", "/tmp/repo", "branch", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsRepoDirAndBranch() {
        List<String> errors = new GitCheckoutAction().validate(
            Map.of("repoDir", "/tmp/repo", "branch", "main"));
        assertTrue(errors.isEmpty());
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
mvn verify -q
```

Expected: `BUILD FAILURE`

- [ ] **Step 3: Create GitCloneAction.java**

```java
package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class GitCloneAction implements IAction {

    @Override public String getId()          { return "git-clone"; }
    @Override public String getName()        { return "Git Clone"; }
    @Override public String getDescription() { return "Clones a git repository to a local directory."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("url", "", "targetDir", "", "branch", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("url", "").isBlank())
            errors.add("url must not be blank");
        if (config.getOrDefault("targetDir", "").isBlank())
            errors.add("targetDir must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String url       = config.get("url");
        String targetDir = config.get("targetDir");
        String branch    = config.getOrDefault("branch", "");

        List<String> cmd = new ArrayList<>(List.of("git", "clone"));
        if (!branch.isBlank()) { cmd.add("--branch"); cmd.add(branch); }
        cmd.add(url);
        cmd.add(targetDir);

        ProcessRunner.run(cmd, null, context);
    }
}
```

- [ ] **Step 4: Create GitCheckoutAction.java**

```java
package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class GitCheckoutAction implements IAction {

    @Override public String getId()          { return "git-checkout"; }
    @Override public String getName()        { return "Git Checkout"; }
    @Override public String getDescription() { return "Checks out a branch in a local git repository."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("repoDir", "", "branch", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("repoDir", "").isBlank())
            errors.add("repoDir must not be blank");
        if (config.getOrDefault("branch", "").isBlank())
            errors.add("branch must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        ProcessRunner.run(
            List.of("git", "-C", config.get("repoDir"), "checkout", config.get("branch")),
            null, context);
    }
}
```

- [ ] **Step 5: Run to confirm 59 tests pass**

```
mvn verify -q
```

Expected: `Tests run: 59, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/GitCloneActionTest.java
git add com.example.automation.tests/src/main/java/com/example/automation/tests/GitCheckoutActionTest.java
git add com.example.automation/src/main/java/com/example/automation/actions/GitCloneAction.java
git add com.example.automation/src/main/java/com/example/automation/actions/GitCheckoutAction.java
git commit -m "feat: add GitCloneAction and GitCheckoutAction"
```

---

### Task 5: RefreshAllAction + RefreshProjectAction

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/RefreshAllActionTest.java`
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/RefreshProjectActionTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/RefreshAllAction.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/RefreshProjectAction.java`

- [ ] **Step 1: Create the test files**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.RefreshAllAction;

public class RefreshAllActionTest {

    @Test
    public void defaultConfig_isEmpty() {
        assertTrue(new RefreshAllAction().getDefaultConfig().isEmpty());
    }

    @Test
    public void validate_alwaysReturnsEmpty() {
        assertTrue(new RefreshAllAction().validate(Map.of()).isEmpty());
    }
}
```

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.RefreshProjectAction;

public class RefreshProjectActionTest {

    @Test
    public void validate_rejectsBlankProjectName() {
        List<String> errors = new RefreshProjectAction().validate(Map.of("projectName", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankProjectName() {
        List<String> errors = new RefreshProjectAction().validate(Map.of("projectName", "my-project"));
        assertTrue(errors.isEmpty());
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
mvn verify -q
```

Expected: `BUILD FAILURE`

- [ ] **Step 3: Create RefreshAllAction.java**

```java
package com.example.automation.actions;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class RefreshAllAction implements IAction {

    @Override public String getId()          { return "refresh-all"; }
    @Override public String getName()        { return "Refresh All"; }
    @Override public String getDescription() { return "Refreshes all projects in the workspace."; }

    @Override public Map<String, String> getDefaultConfig() { return Map.of(); }
    @Override public List<String> validate(Map<String, String> config) { return List.of(); }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        context.setProgress(0);
        ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null);
        context.setProgress(100);
    }
}
```

- [ ] **Step 4: Create RefreshProjectAction.java**

```java
package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class RefreshProjectAction implements IAction {

    @Override public String getId()          { return "refresh-project"; }
    @Override public String getName()        { return "Refresh Project"; }
    @Override public String getDescription() { return "Refreshes a specific project in the workspace."; }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("projectName", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("projectName", "").isBlank())
            errors.add("projectName must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        context.setProgress(0);
        ResourcesPlugin.getWorkspace().getRoot()
            .getProject(config.get("projectName"))
            .refreshLocal(IResource.DEPTH_INFINITE, null);
        context.setProgress(100);
    }
}
```

- [ ] **Step 5: Run to confirm 63 tests pass**

```
mvn verify -q
```

Expected: `Tests run: 63, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/RefreshAllActionTest.java
git add com.example.automation.tests/src/main/java/com/example/automation/tests/RefreshProjectActionTest.java
git add com.example.automation/src/main/java/com/example/automation/actions/RefreshAllAction.java
git add com.example.automation/src/main/java/com/example/automation/actions/RefreshProjectAction.java
git commit -m "feat: add RefreshAllAction and RefreshProjectAction"
```

---

### Task 6: ExecuteRunConfigAction

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ExecuteRunConfigActionTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ExecuteRunConfigAction.java`

- [ ] **Step 1: Create the test file**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.ExecuteRunConfigAction;

public class ExecuteRunConfigActionTest {

    @Test
    public void validate_rejectsBlankConfigName() {
        List<String> errors = new ExecuteRunConfigAction().validate(Map.of("configName", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankConfigName() {
        List<String> errors = new ExecuteRunConfigAction().validate(Map.of("configName", "My Build"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_modeIsRun() {
        assertEquals("run", new ExecuteRunConfigAction().getDefaultConfig().get("mode"));
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
mvn verify -q
```

Expected: `BUILD FAILURE`

- [ ] **Step 3: Create ExecuteRunConfigAction.java**

```java
package com.example.automation.actions;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class ExecuteRunConfigAction implements IAction {

    @Override public String getId()          { return "execute-run-config"; }
    @Override public String getName()        { return "Execute Run Configuration"; }
    @Override public String getDescription() { return "Executes an existing Eclipse launch configuration by name."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("configName", "", "mode", "run");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("configName", "").isBlank())
            errors.add("configName must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String configName = config.get("configName");
        String mode       = config.getOrDefault("mode", "run");
        if (mode.isBlank()) mode = "run";

        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfiguration launchConfig = null;
        for (ILaunchConfiguration c : mgr.getLaunchConfigurations()) {
            if (configName.equals(c.getName())) { launchConfig = c; break; }
        }
        if (launchConfig == null)
            throw new Exception("Launch configuration not found: " + configName);

        context.setProgress(0);
        ILaunch launch = launchConfig.launch(mode, null, false, true);

        for (IProcess process : launch.getProcesses()) {
            OutputStream out = context.getOutputStream();
            OutputStream err = context.getErrorStream();
            process.getStreamsProxy().getOutputStreamMonitor()
                .addListener((text, mon) -> writeQuietly(out, text));
            process.getStreamsProxy().getErrorStreamMonitor()
                .addListener((text, mon) -> writeQuietly(err, text));
            writeQuietly(out,
                process.getStreamsProxy().getOutputStreamMonitor().getContents());
        }

        while (!launch.isTerminated()) {
            if (context.isCancelled()) { launch.terminate(); return; }
            Thread.sleep(100);
        }

        for (IProcess process : launch.getProcesses()) {
            if (process.getExitValue() != 0)
                throw new Exception("Launch failed with exit code " + process.getExitValue());
        }
        context.setProgress(100);
    }

    private static void writeQuietly(OutputStream out, String text) {
        if (text == null || text.isEmpty()) return;
        try { out.write(text.getBytes()); } catch (IOException ignored) {}
    }
}
```

- [ ] **Step 4: Run to confirm 66 tests pass**

```
mvn verify -q
```

Expected: `Tests run: 66, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/ExecuteRunConfigActionTest.java
git add com.example.automation/src/main/java/com/example/automation/actions/ExecuteRunConfigAction.java
git commit -m "feat: add ExecuteRunConfigAction"
```

---

### Task 7: MavenRunWithProgressAction

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java`

- [ ] **Step 1: Create the test file**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.MavenRunWithProgressAction;

public class MavenRunWithProgressActionTest {

    @Test
    public void validate_rejectsBlankConfigName() {
        List<String> errors = new MavenRunWithProgressAction().validate(Map.of("configName", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankConfigName() {
        List<String> errors = new MavenRunWithProgressAction().validate(
            Map.of("configName", "My Maven Build"));
        assertTrue(errors.isEmpty());
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
mvn verify -q
```

Expected: `BUILD FAILURE`

- [ ] **Step 3: Create MavenRunWithProgressAction.java**

```java
package com.example.automation.actions;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class MavenRunWithProgressAction implements IAction {

    @Override public String getId()          { return "maven-run-with-progress"; }
    @Override public String getName()        { return "Maven Run with Progress"; }
    @Override public String getDescription() { return "Executes a Maven launch configuration and parses progress from console output."; }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("configName", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("configName", "").isBlank())
            errors.add("configName must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        new Execution(config.get("configName"), context).run();
    }

    private static class Execution {
        private final String configName;
        private final IActionContext context;
        private final MavenProgressParser parser = new MavenProgressParser();
        private final StringBuilder lineBuffer   = new StringBuilder();
        private volatile boolean buildFailed     = false;

        Execution(String configName, IActionContext context) {
            this.configName = configName;
            this.context    = context;
        }

        void run() throws Exception {
            ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
            ILaunchConfiguration launchConfig = null;
            for (ILaunchConfiguration c : mgr.getLaunchConfigurations()) {
                if (configName.equals(c.getName())) { launchConfig = c; break; }
            }
            if (launchConfig == null)
                throw new Exception("Launch configuration not found: " + configName);

            context.setProgress(0);
            ILaunch launch = launchConfig.launch(ILaunchManager.RUN_MODE, null, false, true);

            for (IProcess process : launch.getProcesses()) {
                OutputStream err = context.getErrorStream();
                process.getStreamsProxy().getOutputStreamMonitor()
                    .addListener((text, mon) -> onOutput(text));
                process.getStreamsProxy().getErrorStreamMonitor()
                    .addListener((text, mon) -> writeQuietly(err, text));
                onOutput(process.getStreamsProxy().getOutputStreamMonitor().getContents());
            }

            while (!launch.isTerminated()) {
                if (context.isCancelled()) { launch.terminate(); return; }
                Thread.sleep(100);
            }

            flushBuffer();

            if (buildFailed) throw new Exception("Maven build failed.");
            for (IProcess process : launch.getProcesses()) {
                if (process.getExitValue() != 0)
                    throw new Exception("Maven run failed with exit code " + process.getExitValue());
            }
            context.setProgress(100);
        }

        private void onOutput(String text) {
            writeQuietly(context.getOutputStream(), text);
            lineBuffer.append(text);
            int idx;
            while ((idx = lineBuffer.indexOf("\n")) >= 0) {
                String line = lineBuffer.substring(0, idx).stripTrailing();
                lineBuffer.delete(0, idx + 1);
                processLine(line);
            }
        }

        private void processLine(String line) {
            if (line.contains("BUILD FAILURE")) buildFailed = true;
            OptionalInt progress = parser.parse(line);
            if (progress.isPresent()) context.setProgress(progress.getAsInt());
        }

        private void flushBuffer() {
            String remaining = lineBuffer.toString().stripTrailing();
            if (!remaining.isEmpty()) processLine(remaining);
        }

        private static void writeQuietly(OutputStream out, String text) {
            if (text == null || text.isEmpty()) return;
            try { out.write(text.getBytes()); } catch (IOException ignored) {}
        }
    }
}
```

- [ ] **Step 4: Run to confirm 68 tests pass**

```
mvn verify -q
```

Expected: `Tests run: 68, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/MavenRunWithProgressActionTest.java
git add com.example.automation/src/main/java/com/example/automation/actions/MavenRunWithProgressAction.java
git commit -m "feat: add MavenRunWithProgressAction with NM and phase progress parsing"
```

---

### Task 8: ImportMavenProjectAction

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/ImportMavenProjectActionTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java`

- [ ] **Step 1: Create the test file**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.ImportMavenProjectAction;

public class ImportMavenProjectActionTest {

    @Test
    public void validate_blankPomPath_alwaysRejected() {
        List<String> errors = new ImportMavenProjectAction().validate(Map.of("pomPath", ""));
        assertTrue(errors.stream().anyMatch(e -> e.contains("pomPath")));
    }

    @Test
    public void defaultConfig_containsPomPathKey() {
        assertTrue(new ImportMavenProjectAction().getDefaultConfig().containsKey("pomPath"));
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
mvn verify -q
```

Expected: `BUILD FAILURE`

- [ ] **Step 3: Create ImportMavenProjectAction.java**

```java
package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class ImportMavenProjectAction implements IAction {

    @Override public String getId()          { return "import-maven-project"; }
    @Override public String getName()        { return "Import Maven Project"; }
    @Override public String getDescription() { return "Imports an existing Maven project into the Eclipse workspace."; }

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
        File pomFile = new File(pomPath);
        if (pomFile.isDirectory()) pomFile = new File(pomFile, "pom.xml");
        if (!pomFile.exists())
            throw new Exception("pom.xml not found at: " + pomFile.getAbsolutePath());

        context.setProgress(0);
        MavenProjectInfo info = new MavenProjectInfo(pomFile.getName(), pomFile, null, null);
        MavenPlugin.getProjectConfigurationManager().importProjects(
            Collections.singletonList(info),
            new ProjectImportConfiguration(),
            new NullProgressMonitor());
        context.setProgress(100);
    }
}
```

- [ ] **Step 4: Run to confirm 70 tests pass**

```
mvn verify -q
```

Expected: `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/ImportMavenProjectActionTest.java
git add com.example.automation/src/main/java/com/example/automation/actions/ImportMavenProjectAction.java
git commit -m "feat: add ImportMavenProjectAction using M2E API"
```

---

### Task 9: MavenUpdateProjectAction

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/MavenUpdateProjectActionTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/MavenUpdateProjectAction.java`

- [ ] **Step 1: Create the test file**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.MavenUpdateProjectAction;

public class MavenUpdateProjectActionTest {

    @Test
    public void validate_blankProjectName_alwaysRejected() {
        List<String> errors = new MavenUpdateProjectAction().validate(Map.of("projectName", ""));
        assertTrue(errors.stream().anyMatch(e -> e.contains("projectName")));
    }

    @Test
    public void defaultConfig_containsProjectNameKey() {
        assertTrue(new MavenUpdateProjectAction().getDefaultConfig().containsKey("projectName"));
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
mvn verify -q
```

Expected: `BUILD FAILURE`

- [ ] **Step 3: Create MavenUpdateProjectAction.java**

```java
package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.MavenUpdateRequest;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class MavenUpdateProjectAction implements IAction {

    @Override public String getId()          { return "maven-update-project"; }
    @Override public String getName()        { return "Maven Update Project"; }
    @Override public String getDescription() { return "Updates Maven project configuration (Maven > Update Project)."; }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("projectName", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        Bundle m2e = Platform.getBundle("org.eclipse.m2e.core");
        if (m2e == null || m2e.getState() != Bundle.ACTIVE)
            errors.add("M2E (Maven Integration for Eclipse) is not installed or not active.");
        if (config.getOrDefault("projectName", "").isBlank())
            errors.add("projectName must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String projectName = config.get("projectName");
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (!project.exists())
            throw new Exception("Project not found in workspace: " + projectName);

        context.setProgress(0);
        MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(
            new MavenUpdateRequest(project, false, false),
            new NullProgressMonitor());
        context.setProgress(100);
    }
}
```

- [ ] **Step 4: Run to confirm 72 tests pass**

```
mvn verify -q
```

Expected: `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/MavenUpdateProjectActionTest.java
git add com.example.automation/src/main/java/com/example/automation/actions/MavenUpdateProjectAction.java
git commit -m "feat: add MavenUpdateProjectAction using M2E API"
```

---

### Task 10: Register all 9 actions in plugin.xml + final verification

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml`

- [ ] **Step 1: Replace plugin.xml with the complete content including all 9 action extensions**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>

  <extension-point
      id="actions"
      name="Automation Actions"
      schema="schema/actions.exsd"/>

  <extension point="org.eclipse.ui.views">
    <view
        id="com.example.automation.view"
        name="Automation"
        class="com.example.automation.AutomationView"
        icon="icons/configs.png"
        restorable="true"/>
  </extension>

  <extension point="org.eclipse.ui.commands">
    <command
        id="com.example.automation.showView"
        name="Automation"/>
  </extension>

  <extension point="org.eclipse.ui.handlers">
    <handler
        commandId="com.example.automation.showView"
        class="com.example.automation.ShowAutomationViewHandler"/>
  </extension>

  <extension point="org.eclipse.ui.menus">
    <menuContribution locationURI="menu:project?after=additions">
      <command
          commandId="com.example.automation.showView"
          label="Automation"
          icon="icons/configs.png"/>
    </menuContribution>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.ShellCommandAction"/>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.GitCloneAction"/>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.GitCheckoutAction"/>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.RefreshAllAction"/>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.RefreshProjectAction"/>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.ExecuteRunConfigAction"/>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.MavenRunWithProgressAction"/>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.ImportMavenProjectAction"/>
  </extension>

  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.MavenUpdateProjectAction"/>
  </extension>

</plugin>
```

- [ ] **Step 2: Run full test suite**

```
mvn verify -q
```

Expected: `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Commit**

```
git add com.example.automation/plugin.xml
git commit -m "feat: register all 9 built-in actions in plugin.xml"
```
