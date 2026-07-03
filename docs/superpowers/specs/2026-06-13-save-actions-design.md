# Save Actions Action — Design Spec

**Goal:** Add a new `IAction` that configures Eclipse's workspace-level JDT save actions — organize imports and format edited lines — via two boolean config keys, both enabled by default.

---

## Scope

**In scope:**
- One new action class `SetSaveActionsAction` in `com.example.automation.actions`
- Config keys: `organizeImports` (default `"true"`) and `formatEditedLines` (default `"true"`)
- Applies workspace-wide via `InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui")`
- Writes four JDT UI preference keys and flushes

**Out of scope:**
- Project-level save action settings
- Other save action types (e.g. remove trailing whitespace, sort members)
- Format entire file (only "edited lines only" is supported)

---

## Config Keys

| Key | Default | Description |
|-----|---------|-------------|
| `organizeImports` | `"true"` | Enable organize imports on save |
| `formatEditedLines` | `"true"` | Enable format edited lines on save |

Values are parsed case-insensitively: `"true"` (any case) = enabled, anything else = disabled. `validate()` always returns an empty list — invalid strings simply parse as false.

---

## Architecture

One new file, no interface changes, no new bundle dependencies.

### New: `SetSaveActionsAction`

**File:** `com.example.automation/src/main/java/com/example/automation/actions/SetSaveActionsAction.java`

Implements `IAction`. Follows the same shape as `SetMavenSettingsAction` and `SetCodeFormatterAction`.

| Method | Behaviour |
|--------|-----------|
| `getId()` | `"set-save-actions"` |
| `getName()` | `"Set Save Actions"` |
| `getDescription()` | `"Configures Eclipse save actions: organize imports and format edited lines."` |
| `getDefaultConfig()` | `Map.of("organizeImports", "true", "formatEditedLines", "true")` |
| `validate(config)` | Always returns empty list |
| `execute(config, context)` | Parse config → write 4 prefs → flush → log → set progress |

**`execute()` logic:**

1. Parse `organizeImports` and `formatEditedLines` from config (case-insensitive `"true"` = true).
2. Compute master: `true` if either feature is enabled.
3. Write to `InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui")`:
   - `sp_cleanup.on_save_use_additional_actions` → `"true"` or `"false"` (master)
   - `sp_cleanup.organize_imports` → `"true"` or `"false"`
   - `sp_cleanup.format_source_code` → `"true"` or `"false"`
   - `sp_cleanup.format_source_code_changes_only` → `"true"` when `formatEditedLines` is true, `"false"` otherwise
4. Call `flush()`.
5. Print summary to `context.getStdout()`.
6. Set progress: 0 at start, 100 at end.

No new `Require-Bundle` entries needed: `org.eclipse.core.runtime` (already declared) provides `InstanceScope` and `IEclipsePreferences`.

### Modified: `plugin.xml`

Add one `<extension>` block (same pattern as all other actions):

```xml
<extension point="com.example.automation.actions">
  <action class="com.example.automation.actions.SetSaveActionsAction"/>
</extension>
```

---

## Preference Key Reference

All written to the `org.eclipse.jdt.ui` preferences node (Preferences → Java → Editor → Save Actions):

| Preference key | Meaning |
|----------------|---------|
| `sp_cleanup.on_save_use_additional_actions` | Master switch: enables save actions |
| `sp_cleanup.organize_imports` | Organize imports on save |
| `sp_cleanup.format_source_code` | Format source code on save |
| `sp_cleanup.format_source_code_changes_only` | Format edited lines only (vs. entire file) |

---

## Testing

**File:** `com.example.automation.tests/src/main/java/com/example/automation/tests/SetSaveActionsActionTest.java`

Plain JUnit (no SWTBot). `@Before`/`@After` captures and restores all four `org.eclipse.jdt.ui` preference keys to avoid test pollution.

| Test | Description |
|------|-------------|
| `apply_defaults_enablesBothFeatures` | Execute with `organizeImports=true`, `formatEditedLines=true`; assert all 4 prefs written correctly |
| `apply_organizeImportsOnly` | `formatEditedLines=false`; master switch `"true"`, `format_source_code` and `format_source_code_changes_only` both `"false"` |
| `apply_bothDisabled_masterSwitchOff` | Both `"false"`; master switch `"false"`, all feature keys `"false"` |
| `validate_alwaysEmpty` | `validate()` returns empty list for any config |
