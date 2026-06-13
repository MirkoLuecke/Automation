# Set Code Formatter Action Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `SetCodeFormatterAction` that reads an Eclipse formatter XML profile file and applies its settings to the workspace-level JDT preferences.

**Architecture:** A single new `IAction` class follows the same shape as `SetMavenSettingsAction` — one config key `filePath`, parse XML with `DocumentBuilderFactory`, write settings to `InstanceScope.INSTANCE.getNode("org.eclipse.jdt.core")`, flush. Registered in `plugin.xml` like every other action.

**Tech Stack:** Java 17, Eclipse OSGi/Tycho, `javax.xml.parsers` (JDK built-in), `org.eclipse.core.runtime` (already in `Require-Bundle`), JUnit 4

---

### Task 1: `SetCodeFormatterAction`

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetCodeFormatterAction.java`
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml` (add one `<extension>` block)
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetCodeFormatterActionTest.java`

**Background for the implementer:**

Every `IAction` in this plugin is contributed via the `com.example.automation.actions` extension point in `plugin.xml`. Each contribution is a single `<extension>` block with a `<action class="..."/>` element — see the existing 12 entries in `plugin.xml` for the pattern.

`SetCodeFormatterAction` follows the same shape as `SetMavenSettingsAction`:
- One config key `filePath`
- `validate()` rejects blank `filePath`
- `execute()` validates the file exists, parses it, writes to Eclipse preferences, logs, and sets progress

The Eclipse formatter XML format (exported from Preferences → Java → Code Style → Formatter → Export…) looks like:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<profiles version="21">
    <profile kind="CodeFormatterProfile" name="MyProfile" version="21">
        <setting id="org.eclipse.jdt.core.formatter.tabulation.char" value="tab"/>
        <setting id="org.eclipse.jdt.core.formatter.tabulation.size" value="4"/>
    </profile>
</profiles>
```

The action finds the first `<profile kind="CodeFormatterProfile">` element and writes all its `<setting>` entries to `InstanceScope.INSTANCE.getNode("org.eclipse.jdt.core")`.

`IActionContext` has only 4 abstract methods: `getOutputStream()`, `getErrorStream()`, `setProgress(int)`, `isCancelled()`. All other methods have default implementations. The test uses a minimal anonymous implementation.

The test restores the `org.eclipse.jdt.core.formatter.tabulation.char` preference in `@After` to avoid polluting other tests that run in the same Eclipse instance.

---

- [ ] **Step 1: Write the failing tests**

Create `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetCodeFormatterActionTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.SetCodeFormatterAction;
import com.example.automation.api.IActionContext;

public class SetCodeFormatterActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String PREF_NODE = "org.eclipse.jdt.core";
    private static final String TEST_KEY  = "org.eclipse.jdt.core.formatter.tabulation.char";

    private String originalValue;

    @Before
    public void captureOriginal() throws Exception {
        originalValue = InstanceScope.INSTANCE.getNode(PREF_NODE).get(TEST_KEY, null);
    }

    @After
    public void restorePreference() throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        if (originalValue == null)
            prefs.remove(TEST_KEY);
        else
            prefs.put(TEST_KEY, originalValue);
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

    @Test
    public void apply_writesSettingsToPreferences() throws Exception {
        File xml = tmp.newFile("formatter.xml");
        Files.writeString(xml.toPath(),
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
            "<profiles version=\"21\">\n" +
            "  <profile kind=\"CodeFormatterProfile\" name=\"TestProfile\" version=\"21\">\n" +
            "    <setting id=\"" + TEST_KEY + "\" value=\"space\"/>\n" +
            "  </profile>\n" +
            "</profiles>\n");

        new SetCodeFormatterAction().execute(
            Map.of("filePath", xml.getAbsolutePath()), nullCtx());

        assertEquals("space",
            InstanceScope.INSTANCE.getNode(PREF_NODE).get(TEST_KEY, null));
    }

    @Test
    public void apply_missingFile_throws() {
        try {
            new SetCodeFormatterAction().execute(
                Map.of("filePath", "/nonexistent/path/formatter.xml"), nullCtx());
            fail("Expected exception for missing file");
        } catch (Exception e) {
            assertTrue("Message should mention 'not found'",
                e.getMessage().contains("not found"));
        }
    }

    @Test
    public void validate_blankFilePath_returnsError() {
        List<String> errors = new SetCodeFormatterAction().validate(Map.of("filePath", ""));
        assertFalse("validate() must reject blank filePath", errors.isEmpty());
    }
}
```

- [ ] **Step 2: Note that the tests will not compile yet**

`SetCodeFormatterAction` does not exist yet. This is expected — proceed to Step 3.

- [ ] **Step 3: Create `SetCodeFormatterAction.java`**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetCodeFormatterAction.java`:

```java
package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that reads an Eclipse formatter XML
 * profile and applies its settings to the workspace-level JDT preferences.
 *
 * <p>Config keys: {@code filePath} (required — path to an Eclipse formatter XML export file).
 */
public class SetCodeFormatterAction implements IAction {

    @Override public String getId()          { return "set-code-formatter"; }
    @Override public String getName()        { return "Set Code Formatter"; }
    @Override public String getDescription() {
        return "Applies an Eclipse formatter XML profile to the workspace JDT settings.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("filePath", "");
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
        String filePath = config.getOrDefault("filePath", "");
        if (filePath.isBlank()) throw new IllegalArgumentException("filePath must not be blank");

        File file = new File(filePath);
        if (!file.exists())
            throw new Exception("Formatter file not found: " + file.getAbsolutePath());

        context.setProgress(0);

        Document doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file);

        NodeList profiles = doc.getElementsByTagName("profile");
        Element profile = null;
        for (int i = 0; i < profiles.getLength(); i++) {
            Element candidate = (Element) profiles.item(i);
            if ("CodeFormatterProfile".equals(candidate.getAttribute("kind"))) {
                profile = candidate;
                break;
            }
        }
        if (profile == null)
            throw new Exception("No CodeFormatterProfile found in: " + file.getName());

        String profileName = profile.getAttribute("name");
        NodeList settings = profile.getElementsByTagName("setting");

        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode("org.eclipse.jdt.core");
        for (int i = 0; i < settings.getLength(); i++) {
            Element setting = (Element) settings.item(i);
            prefs.put(setting.getAttribute("id"), setting.getAttribute("value"));
        }
        prefs.flush();

        context.getStdout().println("Applied formatter profile '" + profileName
            + "' (" + settings.getLength() + " settings) to workspace JDT preferences.");
        context.setProgress(100);
    }
}
```

- [ ] **Step 4: Register the action in `plugin.xml`**

In `com.example.automation.parent/com.example.automation/plugin.xml`, add the following block after the existing `SetActiveTargetPlatformAction` entry (before the `<extension point="org.eclipse.ui.preferencePages">` block):

```xml
  <extension point="com.example.automation.actions">
    <action class="com.example.automation.actions.SetCodeFormatterAction"/>
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
git add com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/actions/SetCodeFormatterAction.java
git add com.example.automation.parent/com.example.automation/plugin.xml
git add com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/SetCodeFormatterActionTest.java
git commit -m "feat: add SetCodeFormatterAction for applying Eclipse formatter profiles"
```
