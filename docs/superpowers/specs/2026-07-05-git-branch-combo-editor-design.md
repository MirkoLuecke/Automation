# Git Branch Combo Editor — Design Spec

## Goal

Replace the plain text field for the `branch` config key in `GitCheckoutAction` with an
editable combo box that lists available remote branches fetched from the local git
repository at `repoDir`. The combo is refreshed each time the user opens it; if
`repoDir` is not yet configured or is not a valid git repository, a readable
placeholder explains why the list is empty. The field remains freely editable so the
user can type any branch name manually.

---

## Scope

| # | Feature | Files touched |
|---|---------|---------------|
| 1 | Branch combo cell editor | `GitBranchComboEditor` (new) |
| 2 | Wire combo into Properties view | `StepPropertySource` (modified) |
| 3 | Default branch + blank-means-main | `GitCheckoutAction` (modified) |
| 4 | Unit tests | `GitBranchComboEditorTest` (new), `GitCheckoutActionTest` (modified) |
| 5 | Version bump | 1.5.0 → 1.6.0 |

---

## Feature Specifications

### 1. `GitBranchComboEditor`

```java
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
        // focus listener, key listener (same as ProjectComboBoxCellEditor)
        return combo;
    }

    @Override
    protected void doSetFocus() {
        populateItems();
        combo.setFocus();
    }

    @Override
    protected Object doGetValue() { return combo.getText(); }

    @Override
    protected void doSetValue(Object value) {
        combo.setText(value instanceof String s ? s : "");
    }

    private void populateItems() {
        String current = combo.getText();
        List<String> branches = fetchBranches();
        combo.setItems(branches.toArray(new String[0]));
        if (!current.isEmpty()) combo.setText(current);
    }

    private List<String> fetchBranches() {
        String repoDir = resolveRepoDir();
        if (repoDir == null) return placeholder("(configure repoDir first)");
        try {
            Process proc = Runtime.getRuntime().exec(
                new String[]{"git", "branch", "-r"}, null, new File(repoDir));
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            List<String> branches = parseRemoteBranches(output);
            return branches.isEmpty() ? placeholder("(no remote branches found)") : branches;
        } catch (Exception e) {
            return placeholder("(no remote branches found)");
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

    private static List<String> placeholder(String text) {
        return List.of(text);
    }

    public static List<String> parseRemoteBranches(String gitOutput) {
        // see parsing spec below
    }
}
```

#### `parseRemoteBranches(String gitOutput)`

Input: stdout of `git branch -r`.

Algorithm:
1. Split by `\n`, trim each line.
2. Discard lines containing ` -> ` (the `HEAD` pointer entry, e.g. `origin/HEAD -> origin/main`).
3. Discard blank lines.
4. Strip everything up to and including the **first** `/` to remove the remote name prefix
   (`origin/feature/foo` → `feature/foo`).
5. Deduplicate (same branch name appearing under multiple remotes keeps one entry).
6. Sort alphabetically.
7. Return the resulting list (may be empty).

Example:
```
  origin/HEAD -> origin/main     ← discarded
  origin/develop                 → develop
  origin/feature/my-feature      → feature/my-feature
  origin/main                    → main
  upstream/main                  → main  (deduplicated)
```
Result: `["develop", "feature/my-feature", "main"]`

---

### 2. `StepPropertySource` — Wiring

In `createConfigDescriptor(String key)`, add one new special case before the existing
suffix-matching logic:

```java
if ("git-checkout".equals(actionId) && "branch".equals(key)) {
    // editable combo backed by remote branch list
    return new PropertyDescriptor(key, label) {
        @Override
        public CellEditor createPropertyEditor(Composite parent) {
            return new GitBranchComboEditor(parent,
                () -> step.getConfig().get("repoDir"));
        }
    };
}
```

The supplier `() -> step.getConfig().get("repoDir")` reads the live config value each
time `populateItems()` runs, so changing `repoDir` and then opening the branch dropdown
always reflects the latest value.

---

### 3. `GitCheckoutAction` — Default Branch & Blank Handling

**`getDefaultConfig()`:** change `"branch"` default from `""` to `"main"`.

```java
@Override
public Map<String, String> getDefaultConfig() {
    return Map.of("repoDir", "", "branch", "main");
}
```

**`execute()`:** treat a blank `branch` as `"main"`:

```java
String branch = config.getOrDefault("branch", "main");
if (branch.isBlank()) branch = "main";
// run: git checkout <branch>
```

**`validate()`:** remove the non-blank requirement for `branch` (blank is valid and
means `"main"`). `repoDir` remains required.

---

## Error Handling Summary

| Situation | Combo items |
|-----------|-------------|
| `repoDir` blank or null | `["(configure repoDir first)"]` |
| `repoDir` contains unresolvable Eclipse variables | `["(configure repoDir first)"]` |
| `repoDir` is not a git repository | `["(no remote branches found)"]` |
| `git` not on PATH | `["(no remote branches found)"]` |
| No remote configured in repo | `["(no remote branches found)"]` |
| `git branch -r` exits non-zero | `["(no remote branches found)"]` |

In all cases the combo remains fully editable — the user can always type any branch name.

If the user accidentally selects a placeholder entry, `GitCheckoutAction.validate()`
will reject it (the placeholder strings are not valid branch names).

---

## Testing

### `GitBranchComboEditorTest` — pure unit tests (no SWT, no git)

| Test | Covers |
|------|--------|
| `parseRemoteBranches_stripsRemotePrefix` | `"  origin/main\n"` → `["main"]` |
| `parseRemoteBranches_excludesHeadPointer` | HEAD line filtered out |
| `parseRemoteBranches_emptyOutput_returnsEmpty` | `""` → `[]` |
| `parseRemoteBranches_preservesSubpathInBranchName` | `"  origin/feature/foo\n"` → `["feature/foo"]` |
| `parseRemoteBranches_deduplicatesAcrossRemotes` | `origin/main` + `upstream/main` → `["main"]` |
| `parseRemoteBranches_sortsAlphabetically` | `develop` before `main` |

### `GitBranchComboEditorTest` — SWT tests (require Display)

| Test | Covers |
|------|--------|
| `combo_showsConfigurePlaceholder_whenRepoDirBlank` | Supplier returns `""` → placeholder present |
| `combo_showsNotFoundPlaceholder_whenRepoDirNotGitRepo` | Plain temp dir → placeholder present |
| `combo_populatedWithoutExplicitSetFocus` | Items set in `createControl()`, no `setFocus()` call |
| `combo_preservesCurrentValueAfterSetFocus` | `setValue("develop")` → `setFocus()` → `getValue()` == `"develop"` |

### `GitCheckoutActionTest` — modified

| Test | Covers |
|------|--------|
| `validate_allowsBlankBranch` | Empty `branch` passes validation |
| `defaultConfig_branchIsMain` | `getDefaultConfig()` returns `"main"` for `branch` |

---

## File Change Summary

| File | Change type |
|------|-------------|
| `GitBranchComboEditor.java` (new) | Editable combo with remote branch listing |
| `StepPropertySource.java` | One new special case for `("git-checkout", "branch")` |
| `GitCheckoutAction.java` | Default `"main"`, blank-means-main in execute, relax validate |
| `GitBranchComboEditorTest.java` (new) | Parsing unit tests + SWT placeholder tests |
| `GitCheckoutActionTest.java` | Two new tests for blank branch and default config |
| Version files | 1.5.0 → 1.6.0 (pom.xml ×5, MANIFEST.MF ×2, feature.xml) |
