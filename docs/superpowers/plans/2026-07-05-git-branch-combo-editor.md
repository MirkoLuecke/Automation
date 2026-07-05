# Git Branch Combo Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain text `branch` field in the `GitCheckoutAction` Properties view with an editable combo box that lists available remote branches from the configured `repoDir`, refreshed each time the dropdown opens.

**Architecture:** A new `GitBranchComboEditor` cell editor (modelled on `ProjectComboBoxCellEditor`) takes a `Supplier<String>` for `repoDir` and runs `git branch -r` on demand. `StepPropertySource` adds one special case for `("git-checkout", "branch")` matching the existing `MultiLineTextCellEditor` pattern. `GitCheckoutAction` gains `"main"` as the default branch and treats blank as `"main"` at execute time.

**Tech Stack:** Java 17, SWT/JFace, Eclipse `org.eclipse.core.variables` (`IStringVariableManager`), JUnit 4, Tycho Maven build.

---

## File Map

| File | Change |
|------|--------|
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/GitBranchComboEditor.java` | Create — new cell editor |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java` | Modify — one new special case |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/GitCheckoutAction.java` | Modify — default branch, blank handling, relax validate |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitBranchComboEditorTest.java` | Create — parsing + SWT tests |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitCheckoutActionTest.java` | Modify — update + add tests |
| 6× `pom.xml`, 2× `MANIFEST.MF`, `feature.xml` | Modify — version 1.5.0 → 1.6.0 |

---

## Task 1: `parseRemoteBranches` — tests first, then static method

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitBranchComboEditorTest.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/GitBranchComboEditor.java`

- [ ] **Step 1: Write the failing tests**

Create `GitBranchComboEditorTest.java` with the six pure unit tests for `parseRemoteBranches`. No SWT or git needed.

```java
package com.example.automation.tests;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;
import com.example.automation.GitBranchComboEditor;

public class GitBranchComboEditorTest {

    @Test
    public void parseRemoteBranches_stripsRemotePrefix() {
        List<String> result = GitBranchComboEditor.parseRemoteBranches("  origin/main\n");
        assertEquals(List.of("main"), result);
    }

    @Test
    public void parseRemoteBranches_excludesHeadPointer() {
        List<String> result = GitBranchComboEditor.parseRemoteBranches(
            "  origin/HEAD -> origin/main\n  origin/main\n");
        assertEquals(List.of("main"), result);
    }

    @Test
    public void parseRemoteBranches_emptyOutput_returnsEmpty() {
        assertTrue(GitBranchComboEditor.parseRemoteBranches("").isEmpty());
    }

    @Test
    public void parseRemoteBranches_preservesSubpathInBranchName() {
        List<String> result = GitBranchComboEditor.parseRemoteBranches("  origin/feature/foo\n");
        assertEquals(List.of("feature/foo"), result);
    }

    @Test
    public void parseRemoteBranches_deduplicatesAcrossRemotes() {
        List<String> result = GitBranchComboEditor.parseRemoteBranches(
            "  origin/main\n  upstream/main\n");
        assertEquals(List.of("main"), result);
    }

    @Test
    public void parseRemoteBranches_sortsAlphabetically() {
        List<String> result = GitBranchComboEditor.parseRemoteBranches(
            "  origin/main\n  origin/develop\n");
        assertEquals(List.of("develop", "main"), result);
    }
}
```

- [ ] **Step 2: Run the tests — confirm they fail with "class not found"**

```
cd com.example.automation.parent
mvn test -pl com.example.automation.tests -Dtest=GitBranchComboEditorTest -B
```

Expected: BUILD FAILURE — `GitBranchComboEditor` does not exist yet.

- [ ] **Step 3: Create `GitBranchComboEditor.java` with only `parseRemoteBranches`**

Create the file. The SWT editor body is a stub for now; only the static method is real.

```java
package com.example.automation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public class GitBranchComboEditor extends CellEditor {

    private final Supplier<String> repoDirSupplier;
    private Combo combo;

    public GitBranchComboEditor(Composite parent, Supplier<String> repoDirSupplier) {
        super(parent);
        this.repoDirSupplier = repoDirSupplier;
    }

    @Override
    protected Control createControl(Composite parent) {
        combo = new Combo(parent, SWT.DROP_DOWN);
        return combo;
    }

    @Override protected void doSetFocus()              { combo.setFocus(); }
    @Override protected Object doGetValue()            { return combo.getText(); }
    @Override protected void doSetValue(Object value)  { combo.setText(value instanceof String s ? s : ""); }

    public static List<String> parseRemoteBranches(String gitOutput) {
        if (gitOutput == null || gitOutput.isBlank()) return List.of();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Arrays.stream(gitOutput.split("\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.contains(" -> "))
            .map(line -> { int slash = line.indexOf('/'); return slash >= 0 ? line.substring(slash + 1) : line; })
            .sorted()
            .forEach(seen::add);
        return new ArrayList<>(seen);
    }
}
```

- [ ] **Step 4: Run the tests — confirm all 6 pass**

```
mvn test -pl com.example.automation.tests -Dtest=GitBranchComboEditorTest -B
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/GitBranchComboEditorTest.java
git add com.example.automation/src/main/java/com/example/automation/GitBranchComboEditor.java
git commit -m "feat: add GitBranchComboEditor with parseRemoteBranches"
```

---

## Task 2: Full `GitBranchComboEditor` — SWT tests + complete implementation

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitBranchComboEditorTest.java`
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/GitBranchComboEditor.java`

- [ ] **Step 1: Add four SWT tests to `GitBranchComboEditorTest`**

Add these tests to the existing test class (keep the six parsing tests already there). Add `import java.io.File;`, `import java.util.Arrays;`, `import org.eclipse.swt.widgets.*;`, `import org.junit.Rule;`, `import org.junit.rules.TemporaryFolder;`.

```java
@Rule
public TemporaryFolder tmp = new TemporaryFolder();

@Test
public void combo_showsConfigurePlaceholder_whenRepoDirBlank() {
    boolean[] found = {false};
    Display.getDefault().syncExec(() -> {
        Shell shell = new Shell(Display.getDefault(), SWT.NONE);
        shell.open();
        GitBranchComboEditor editor = new GitBranchComboEditor(shell, () -> "");
        found[0] = Arrays.asList(((Combo) editor.getControl()).getItems())
                         .contains("(configure repoDir first)");
        shell.dispose();
    });
    assertTrue("Blank repoDir must show configure placeholder", found[0]);
}

@Test
public void combo_showsNotFoundPlaceholder_whenRepoDirNotGitRepo() throws Exception {
    File plain = tmp.newFolder("notgit");
    boolean[] found = {false};
    Display.getDefault().syncExec(() -> {
        Shell shell = new Shell(Display.getDefault(), SWT.NONE);
        shell.open();
        GitBranchComboEditor editor = new GitBranchComboEditor(shell,
            () -> plain.getAbsolutePath());
        found[0] = Arrays.asList(((Combo) editor.getControl()).getItems())
                         .contains("(no remote branches found)");
        shell.dispose();
    });
    assertTrue("Non-git dir must show not-found placeholder", found[0]);
}

@Test
public void combo_populatedWithoutExplicitSetFocus() {
    boolean[] hasItems = {false};
    Display.getDefault().syncExec(() -> {
        Shell shell = new Shell(Display.getDefault(), SWT.NONE);
        shell.open();
        GitBranchComboEditor editor = new GitBranchComboEditor(shell, () -> "");
        hasItems[0] = ((Combo) editor.getControl()).getItemCount() > 0;
        shell.dispose();
    });
    assertTrue("Combo must have items without calling setFocus()", hasItems[0]);
}

@Test
public void combo_preservesCurrentValueAfterSetFocus() {
    String[] result = {null};
    Display.getDefault().syncExec(() -> {
        Shell shell = new Shell(Display.getDefault(), SWT.NONE);
        shell.open();
        GitBranchComboEditor editor = new GitBranchComboEditor(shell, () -> "");
        editor.setValue("develop");
        editor.setFocus();
        result[0] = (String) editor.getValue();
        shell.dispose();
    });
    assertEquals("develop", result[0]);
}
```

- [ ] **Step 2: Run — confirm the four new SWT tests fail**

```
mvn test -pl com.example.automation.tests -Dtest=GitBranchComboEditorTest -B
```

Expected: 4 failures (combo has no items; `createControl` stub doesn't populate anything).

- [ ] **Step 3: Replace `GitBranchComboEditor.java` with the full implementation**

Replace the entire file content:

```java
package com.example.automation;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public class GitBranchComboEditor extends CellEditor {

    private final Supplier<String> repoDirSupplier;
    private Combo combo;

    public GitBranchComboEditor(Composite parent, Supplier<String> repoDirSupplier) {
        super(parent);
        this.repoDirSupplier = repoDirSupplier;
    }

    @Override
    protected Control createControl(Composite parent) {
        combo = new Combo(parent, SWT.DROP_DOWN);
        populateItems();
        combo.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                GitBranchComboEditor.this.focusLost();
            }
        });
        combo.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                keyReleaseOccured(e);
            }
        });
        return combo;
    }

    @Override
    protected void doSetFocus() {
        populateItems();
        combo.setFocus();
    }

    @Override protected Object doGetValue()           { return combo.getText(); }
    @Override protected void doSetValue(Object value) { combo.setText(value instanceof String s ? s : ""); }

    private void populateItems() {
        String current = combo.getText();
        List<String> branches = fetchBranches();
        combo.setItems(branches.toArray(new String[0]));
        if (!current.isEmpty()) combo.setText(current);
    }

    private List<String> fetchBranches() {
        String repoDir = resolveRepoDir();
        if (repoDir == null) return List.of("(configure repoDir first)");
        try {
            Process proc = new ProcessBuilder("git", "branch", "-r")
                .directory(new File(repoDir))
                .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();
            List<String> branches = parseRemoteBranches(output);
            return branches.isEmpty() ? List.of("(no remote branches found)") : branches;
        } catch (Exception e) {
            return List.of("(no remote branches found)");
        }
    }

    private String resolveRepoDir() {
        String raw = repoDirSupplier.get();
        if (raw == null || raw.isBlank()) return null;
        try {
            IStringVariableManager mgr = VariablesPlugin.getDefault().getStringVariableManager();
            String resolved = mgr.performStringSubstitution(raw, false);
            return resolved.isBlank() ? null : resolved;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> parseRemoteBranches(String gitOutput) {
        if (gitOutput == null || gitOutput.isBlank()) return List.of();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Arrays.stream(gitOutput.split("\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.contains(" -> "))
            .map(line -> { int slash = line.indexOf('/'); return slash >= 0 ? line.substring(slash + 1) : line; })
            .sorted()
            .forEach(seen::add);
        return new ArrayList<>(seen);
    }
}
```

- [ ] **Step 4: Run all 10 tests — confirm they all pass**

```
mvn test -pl com.example.automation.tests -Dtest=GitBranchComboEditorTest -B
```

Expected: `Tests run: 10, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/GitBranchComboEditorTest.java
git add com.example.automation/src/main/java/com/example/automation/GitBranchComboEditor.java
git commit -m "feat: complete GitBranchComboEditor with SWT item population and placeholder handling"
```

---

## Task 3: Wire `StepPropertySource` for `("git-checkout", "branch")`

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/StepPropertySource.java`

No new unit test — the wiring is verified by the full build compiling cleanly and by Task 4's action tests running through the normal flow.

- [ ] **Step 1: Add one import and one special case to `StepPropertySource`**

Add this import at the top of `StepPropertySource.java`:

```java
import com.example.automation.GitBranchComboEditor;
```

In `createConfigDescriptor(String key)` (line 130), insert this block **before** the existing `"projectName"` check:

```java
if ("git-checkout".equals(step.getActionId()) && "branch".equals(key)) {
    return new PropertyDescriptor(key, key) {
        @Override
        public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
            return new GitBranchComboEditor(parent,
                () -> step.getConfig().get("repoDir"));
        }
    };
}
```

After the insert, `createConfigDescriptor` starts with:

```java
private PropertyDescriptor createConfigDescriptor(String key) {
    if ("git-checkout".equals(step.getActionId()) && "branch".equals(key)) {
        return new PropertyDescriptor(key, key) {
            @Override
            public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                return new GitBranchComboEditor(parent,
                    () -> step.getConfig().get("repoDir"));
            }
        };
    }
    if ("projectName".equals(key)) {
        // ... existing code unchanged
```

- [ ] **Step 2: Build the plugin module to verify it compiles**

```
cd com.example.automation.parent
mvn compile -pl com.example.automation -B
```

Expected: BUILD SUCCESS (no compilation errors).

- [ ] **Step 3: Commit**

```
git add com.example.automation/src/main/java/com/example/automation/StepPropertySource.java
git commit -m "feat: wire GitBranchComboEditor for git-checkout branch field in Properties view"
```

---

## Task 4: Update `GitCheckoutAction` — default branch, blank handling, relax validate

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/GitCheckoutAction.java`
- Modify: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/GitCheckoutActionTest.java`

- [ ] **Step 1: Write failing tests and update the existing broken one**

The existing test `validate_rejectsBlankBranch` will break after the change — rename and invert it now so it documents the new expected behaviour. Also add `defaultConfig_branchIsMain` and `execute_blankBranch_checkoutsMain`.

Also add `runGit(repo, "branch", "main");` to `initRepo()` so the "main" branch exists for checkout.

Replace the full `GitCheckoutActionTest.java`:

```java
package com.example.automation.tests;

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

public class GitCheckoutActionTest {

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
        runGit(repo, "branch", "main");
    }

    @Test
    public void validate_rejectsBlankRepoDir() {
        List<String> errors = new GitCheckoutAction().validate(
            Map.of("repoDir", "", "branch", "main"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_allowsBlankBranch() {
        List<String> errors = new GitCheckoutAction().validate(
            Map.of("repoDir", "/tmp/repo", "branch", ""));
        assertTrue("blank branch must be valid (defaults to main)", errors.isEmpty());
    }

    @Test
    public void validate_acceptsRepoDirAndBranch() {
        List<String> errors = new GitCheckoutAction().validate(
            Map.of("repoDir", "/tmp/repo", "branch", "main"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_branchIsMain() {
        assertEquals("main", new GitCheckoutAction().getDefaultConfig().get("branch"));
    }

    @Test
    public void getId_returnsGitCheckout() {
        assertEquals("git-checkout", new GitCheckoutAction().getId());
    }

    @Test
    public void getName_returnsGitCheckout() {
        assertEquals("Git Checkout", new GitCheckoutAction().getName());
    }

    @Test
    public void execute_checkoutBranch_headPointsToFeature() throws Exception {
        new GitCheckoutAction().execute(
            Map.of("repoDir", repo.getAbsolutePath(), "branch", "feature"),
            nullCtx());
        String head = Files.readString(new File(repo, ".git/HEAD").toPath()).trim();
        assertEquals("ref: refs/heads/feature", head);
    }

    @Test
    public void execute_blankBranch_checkoutsMain() throws Exception {
        runGit(repo, "checkout", "feature");
        new GitCheckoutAction().execute(
            Map.of("repoDir", repo.getAbsolutePath(), "branch", ""),
            nullCtx());
        String head = Files.readString(new File(repo, ".git/HEAD").toPath()).trim();
        assertEquals("ref: refs/heads/main", head);
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
}
```

- [ ] **Step 2: Run tests — confirm the new/changed tests fail**

```
mvn test -pl com.example.automation.tests -Dtest=GitCheckoutActionTest -B
```

Expected: `validate_allowsBlankBranch` FAILS (blank is still rejected), `defaultConfig_branchIsMain` FAILS, `execute_blankBranch_checkoutsMain` FAILS.

- [ ] **Step 3: Update `GitCheckoutAction.java`**

Replace the full file:

```java
package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.EclipseVariables;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that checks out a named branch in a
 * local Git repository by running {@code git checkout}.
 *
 * <p>Config keys: {@code repoDir} (required), {@code branch} (optional — defaults to {@code main}).
 */
public class GitCheckoutAction implements IAction {

    @Override public String getId()          { return "git-checkout"; }
    @Override public String getName()        { return "Git Checkout"; }
    @Override public String getDescription() { return "Checks out a branch in a local git repository."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("repoDir", "", "branch", "main");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("repoDir", "").isBlank())
            errors.add("repoDir must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String repoDir = config.get("repoDir");
        String branch  = config.getOrDefault("branch", "main");

        if (repoDir == null || repoDir.isBlank())
            throw new IllegalArgumentException("repoDir must not be blank");
        if (branch.isBlank()) branch = "main";
        repoDir = EclipseVariables.resolve(repoDir);
        ProcessRunner.run(
            List.of("git", "-C", repoDir, "checkout", branch),
            null, context);
    }
}
```

- [ ] **Step 4: Run tests — confirm all pass**

```
mvn test -pl com.example.automation.tests -Dtest=GitCheckoutActionTest -B
```

Expected: `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation/src/main/java/com/example/automation/actions/GitCheckoutAction.java
git add com.example.automation.tests/src/main/java/com/example/automation/tests/GitCheckoutActionTest.java
git commit -m "feat: default branch to main and allow blank branch in GitCheckoutAction"
```

---

## Task 5: Version bump 1.5.0 → 1.6.0 + full build + p2 site update

**Files:** 6× `pom.xml`, 2× `META-INF/MANIFEST.MF`, `feature.xml`

- [ ] **Step 1: Bump version in all pom.xml files**

In each of the six files below, replace `1.5.0-SNAPSHOT` with `1.6.0-SNAPSHOT`:

```
com.example.automation.parent/pom.xml
com.example.automation.parent/com.example.automation/pom.xml
com.example.automation.parent/com.example.automation.feature/pom.xml
com.example.automation.parent/com.example.automation.site/pom.xml
com.example.automation.parent/com.example.automation.tests/pom.xml
com.example.automation.parent/com.example.automation.coverage/pom.xml
```

Bash shortcut (run from repo root):

```bash
sed -i 's/1\.5\.0-SNAPSHOT/1.6.0-SNAPSHOT/g' \
  com.example.automation.parent/pom.xml \
  com.example.automation.parent/com.example.automation/pom.xml \
  com.example.automation.parent/com.example.automation.feature/pom.xml \
  com.example.automation.parent/com.example.automation.site/pom.xml \
  com.example.automation.parent/com.example.automation.tests/pom.xml \
  com.example.automation.parent/com.example.automation.coverage/pom.xml
```

- [ ] **Step 2: Bump version in both MANIFEST.MF files**

In `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`:
```
Bundle-Version: 1.6.0.qualifier
```

In `com.example.automation.parent/com.example.automation.tests/META-INF/MANIFEST.MF`:
```
Bundle-Version: 1.6.0.qualifier
```

- [ ] **Step 3: Bump version in `feature.xml`**

In `com.example.automation.parent/com.example.automation.feature/feature.xml`:
```xml
    version="1.6.0.qualifier">
```

- [ ] **Step 4: Run the full build — all tests must pass**

```
cd com.example.automation.parent
mvn clean verify -B
```

Expected: `Tests run: 273, Failures: 0, Errors: 0` (265 existing + 6 parsing tests + 4 SWT tests from Task 2 — 2 more for GitCheckoutAction = 10 new tests).

Wait — count: existing 265 + 6 (`parseRemoteBranches`) + 4 (SWT) + 2 new action tests (`validate_allowsBlankBranch`, `defaultConfig_branchIsMain`, `execute_blankBranch_checkoutsMain` = 3 new) = 278. Exact number may vary slightly; what matters is `Failures: 0, Errors: 0, BUILD SUCCESS`.

- [ ] **Step 5: Commit all version files and the p2 site**

```
git add \
  com.example.automation.parent/pom.xml \
  com.example.automation.parent/com.example.automation/pom.xml \
  com.example.automation.parent/com.example.automation.feature/pom.xml \
  com.example.automation.parent/com.example.automation.site/pom.xml \
  com.example.automation.parent/com.example.automation.tests/pom.xml \
  com.example.automation.parent/com.example.automation.coverage/pom.xml \
  com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF \
  com.example.automation.parent/com.example.automation.tests/META-INF/MANIFEST.MF \
  com.example.automation.parent/com.example.automation.feature/feature.xml \
  com.example.automation.parent/local-repo/

git commit -m "chore: bump version to 1.6.0 and update p2 site"
```

- [ ] **Step 6: Push**

```
git push
```
