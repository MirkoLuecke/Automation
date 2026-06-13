# Set Code Formatter Action — Design Spec

**Goal:** Add a new `IAction` that reads an Eclipse formatter XML profile file and applies its settings to the workspace-level JDT preferences.

---

## Scope

**In scope:**
- One new action class `SetCodeFormatterAction` in `com.example.automation.actions`
- Config key: `filePath` (required — path to an Eclipse formatter XML export file)
- Applies the first `<profile kind="CodeFormatterProfile">` found in the file
- Writes settings workspace-wide via `InstanceScope.INSTANCE.getNode("org.eclipse.jdt.core")`
- Logs profile name and number of settings applied

**Out of scope:**
- Project-level formatter settings (`.settings/org.eclipse.jdt.core.prefs`)
- Selecting a named profile when the file contains multiple profiles
- Any format other than the Eclipse formatter XML export format

---

## Input File Format

Standard Eclipse formatter XML export (Preferences → Java → Code Style → Formatter → Export…):

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<profiles version="21">
    <profile kind="CodeFormatterProfile" name="MyProfile" version="21">
        <setting id="org.eclipse.jdt.core.formatter.tabulation.char" value="tab"/>
        <setting id="org.eclipse.jdt.core.formatter.tabulation.size" value="4"/>
        <!-- ... more settings ... -->
    </profile>
</profiles>
```

---

## Architecture

One new file, no interface changes, no new bundle dependencies.

### New: `SetCodeFormatterAction`

**File:** `com.example.automation/src/main/java/com/example/automation/actions/SetCodeFormatterAction.java`

Implements `IAction`. Follows the same shape as `SetMavenSettingsAction`.

| Method | Behaviour |
|--------|-----------|
| `getId()` | `"set-code-formatter"` |
| `getName()` | `"Set Code Formatter"` |
| `getDescription()` | `"Applies an Eclipse formatter XML profile to the workspace JDT settings."` |
| `getDefaultConfig()` | `Map.of("filePath", "")` |
| `validate(config)` | Error if `filePath` is blank |
| `execute(config, context)` | Parse XML → apply settings → log → set progress |

**`execute()` logic:**

1. Resolve and validate the file exists.
2. Parse with `DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)`.
3. Find the first `<profile kind="CodeFormatterProfile">` element — throw `Exception` with a clear message if none is found.
4. Collect all child `<setting id="..." value="...">` elements into a `Map<String, String>`.
5. Write each entry to `InstanceScope.INSTANCE.getNode("org.eclipse.jdt.core")` and call `flush()`.
6. Print profile name and setting count to `context.getStdout()`.

No new `Require-Bundle` entries needed: `org.eclipse.core.runtime` (already declared) provides `InstanceScope` and `IEclipsePreferences`; XML parsing uses `javax.xml.parsers` from the JDK.

---

## Error Handling

| Condition | Behaviour |
|-----------|-----------|
| `filePath` blank | `validate()` returns error; `execute()` throws `IllegalArgumentException` |
| File does not exist | `execute()` throws `Exception("Formatter file not found: <path>")` |
| No `CodeFormatterProfile` in XML | `execute()` throws `Exception("No CodeFormatterProfile found in: <file>")` |
| Malformed XML | `execute()` lets the parser exception propagate (message shown to user) |
| Preferences `flush()` fails | `execute()` lets the `BackingStoreException` propagate |

---

## Testing

**File:** `com.example.automation.tests/src/main/java/com/example/automation/tests/SetCodeFormatterActionTest.java`

Plain JUnit (no SWTBot). Tests run inside the Eclipse harness so `InstanceScope` is available.

| Test | Description |
|------|-------------|
| `apply_writesSettingsToPreferences` | Write a minimal formatter XML to a temp file; call `execute()`; assert the preference key was written to `InstanceScope.INSTANCE.getNode("org.eclipse.jdt.core")` |
| `apply_missingFile_throws` | Call `execute()` with a non-existent path; assert `Exception` is thrown with "not found" in the message |
| `validate_blankFilePath_returnsError` | Assert `validate(Map.of("filePath", ""))` returns a non-empty error list |

After each test that writes to preferences, restore the original value (or remove the key) in a `@After` cleanup to avoid polluting other tests.
