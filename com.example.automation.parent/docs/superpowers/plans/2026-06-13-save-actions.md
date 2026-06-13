# Save Actions Action Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `SetSaveActionsAction` that enables or disables Eclipse's workspace-level JDT save actions (organize imports and format edited lines) via two boolean config keys.

**Architecture:** A single new `IAction` class follows the same shape as `SetCodeFormatterAction` — two config keys (`organizeImports`, `formatEditedLines`) both defaulting to `"true"`, writes four keys to `InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui")`, flushes, logs. Registered in `plugin.xml`.

**Tech Stack:** Java 17, Eclipse OSGi/Tycho, `org.eclipse.core.runtime` (already in `Require-Bundle`), JUnit 4

---

### Task 1: `SetSaveActionsAction`

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetSaveActionsAction.java`
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml` (add one `<extension>` block after `SetCodeFormatterAction`)
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetSaveActionsActionTest.java`

**Background for the implementer:**

This action follows the exact same pattern as `SetCodeFormatterAction` and `SetMavenSettingsAction` — the simplest `IAction` shape: no file I/O, no XML parsing, just write to Eclipse preferences and flush.

The Eclipse save actions feature (Preferences → Java → Editor → Save Actions) is controlled by four keys in the `org.eclipse.jdt.ui` preferences node:

| Preference key | Role |
|----------------|------|
| `sp_cleanup.on_save_use_additional_actions` | Master switch — must be `"true"` for any save actions to run |
| `sp_cleanup.organize_imports` | Organize imports on save |
| `sp_cleanup.format_source_code` | Format source code on save |
| `sp_cleanup.format_source_code_changes_only` | When `"true"`, only format edited lines (not entire file) |

The action writes `"true"` or `"false"` strings (what Eclipse itself stores) by calling `String.valueOf(boolean)`. `Boolean.parseBoolean()` handles the config values: `"true"` (any case) = true, anything else = false.

`validate()` always returns `List.of()` — no required fields.

`IActionContext` has 4 abstract methods: `getOutputStream()`, `getErrorStream()`, `setProgress(int)`, `isCancelled()`. Tests stub them with a `nullCtx()` helper.

`@Before`/`@After` in the test class captures and restores all 4 preference keys to avoid polluting other tests in the same Eclipse instance. See `SetCodeFormatterActionTest` for the exact same pattern with a single key.

Every `IAction` must be registered in `plugin.xml` as an `<extension>` block. Add the new one after the `SetCodeFormatterAction` entry.

---

- [ ] **Step 1: Write the failing tests**

Create `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetSaveActionsActionTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.example.automation.actions.SetSaveActionsAction;
import com.example.automation.api.IActionContext;

public class SetSaveActionsActionTest {

    private static final String PREF_NODE = "org.eclipse.jdt.ui";
    private static final String[] PREF_KEYS = {
        "sp_cleanup.on_save_use_additional_actions",
        "sp_cleanup.organize_imports",
        "sp_cleanup.format_source_code",
        "sp_cleanup.format_source_code_changes_only"
    };

    private Map<String, String> originalValues;

    @Before
    public void captureOriginals() throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        originalValues = new HashMap<>();
        for (String key : PREF_KEYS)
            originalValues.put(key, prefs.get(key, null));
    }

    @After
    public void restorePreferences() throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        for (String key : PREF_KEYS) {
            String original = originalValues.get(key);
            if (original == null)
                prefs.remove(key);
            else
                prefs.put(key, original);
        }
        prefs.flush();
    }

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()           { return false; }
        };
    }

    private static IEclipsePreferences prefs() {
        return InstanceScope.INSTANCE.getNode(PREF_NODE);
    }

    @Test
    public void apply_defaults_enablesBothFeatures() throws Exception {
        new SetSaveActionsAction().execute(
            Map.of("organizeImports", "true", "formatEditedLines", "true"), nullCtx());

        assertEquals("true", prefs().get("sp_cleanup.on_save_use_additional_actions", null));
        assertEquals("true", prefs().get("sp_cleanup.organize_imports", null));
        assertEquals("true", prefs().get("sp_cleanup.format_source_code", null));
        assertEquals("true", prefs().get("sp_cleanup.format_source_code_changes_only", null));
    }

    @Test
    public void apply_organizeImportsOnly() throws Exception {
        new SetSaveActionsAction().execute(
            Map.of("organizeImports", "true", "formatEditedLines", "false"), nullCtx());

        assertEquals("true",  prefs().get("sp_cleanup.on_save_use_additional_actions", null));
        assertEquals("true",  prefs().get("sp_cleanup.organize_imports", null));
        assertEquals("false", prefs().get("sp_cleanup.format_source_code", null));
        assertEquals("false", prefs().get("sp_cleanup.format_source_code_changes_only", null));
    }

    @Test
    public void apply_bothDisabled_masterSwitchOff() throws Exception {
        new SetSaveActionsAction().execute(
            Map.of("organizeImports", "false", "formatEditedLines", "false"), nullCtx());

        assertEquals("false", prefs().get("sp_cleanup.on_save_use_additional_actions", null));
        assertEquals("false", prefs().get("sp_cleanup.organize_imports", null));
        assertEquals("false", prefs().get("sp_cleanup.format_source_code", null));
        assertEquals("false", prefs().get("sp_cleanup.format_source_code_changes_only", null));
    }

    @Test
    public void validate_alwaysEmpty() {
        assertTrue(new SetSaveActionsAction().validate(Map.of()).isEmpty());
        assertTrue(new SetSaveActionsAction().validate(
            Map.of("organizeImports", "false", "formatEditedLines", "false")).isEmpty());
    }
}
```

- [ ] **Step 2: Note that the tests will not compile yet**

`SetSaveActionsAction` does not exist. Expected — proceed to Step 3.

- [ ] **Step 3: Create `SetSaveActionsAction.java`**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetSaveActionsAction.java`:

```java
package com.example.automation.actions;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that configures Eclipse workspace-level
 * JDT save actions: organize imports and format edited lines.
 *
 * <p>Config keys: {@code organizeImports} (default {@code "true"}),
 * {@code formatEditedLines} (default {@code "true"}).
 */
public class SetSaveActionsAction implements IAction {

    private static final String PREF_NODE = "org.eclipse.jdt.ui";

    @Override public String getId()          { return "set-save-actions"; }
    @Override public String getName()        { return "Set Save Actions"; }
    @Override public String getDescription() {
        return "Configures Eclipse save actions: organize imports and format edited lines.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("organizeImports", "true", "formatEditedLines", "true");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        return List.of();
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        context.setProgress(0);

        boolean organizeImports   = Boolean.parseBoolean(config.getOrDefault("organizeImports",   "true"));
        boolean formatEditedLines = Boolean.parseBoolean(config.getOrDefault("formatEditedLines", "true"));
        boolean master            = organizeImports || formatEditedLines;

        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        prefs.put("sp_cleanup.on_save_use_additional_actions",  String.valueOf(master));
        prefs.put("sp_cleanup.organize_imports",                String.valueOf(organizeImports));
        prefs.put("sp_cleanup.format_source_code",              String.valueOf(formatEditedLines));
        prefs.put("sp_cleanup.format_source_code_changes_only", String.valueOf(formatEditedLines));
        prefs.flush();

        context.getStdout().println("Save actions configured:"
            + " organizeImports=" + organizeImports
            + ", formatEditedLines=" + formatEditedLines);
        context.setProgress(100);
    }
}
```

- [ ] **Step 4: Register the action in `plugin.xml`**

In `com.example.automation.parent/com.example.automation/plugin.xml`, add the following block after the `SetCodeFormatterAction` entry and before the `<extension point="org.eclipse.ui.preferencePages">` block:

```xml
  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.SetSaveActionsAction"/>
  </extension>
```

- [ ] **Step 5: Verify the build compiles**

Run from `com.example.automation.parent/`:

```
mvn compile -pl com.example.automation -am -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetSaveActionsAction.java
git add com.example.automation.parent/com.example.automation/plugin.xml
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetSaveActionsActionTest.java
git commit -m "feat: add SetSaveActionsAction for configuring Eclipse save actions"
```
