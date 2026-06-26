# Workflow UX Improvements — Design Spec

## Goal

Seven focused improvements to the Automation plugin's workflow view, step model,
and properties view, plus a version bump to enable in-place updates via the p2
update site.

---

## Scope

| # | Feature | Files touched |
|---|---------|---------------|
| 1 | Bold step flag | `Step`, `StepLabelProvider`, `StepPropertySource` |
| 2 | Workspace-parent default for dir/file configs | `AutomationView`, `StepPropertySource` |
| 3 | Duplicate Step button | `AutomationView` |
| 4 | Multi-select Move Up / Move Down / Duplicate | `AutomationView` |
| 5 | Edit step name in Properties view | `StepPropertySource` |
| 6 | Fix ProjectComboBoxCellEditor (always fresh, all projects) | `ProjectComboBoxCellEditor` |
| 7 | Version bump 1.1.0 → 1.2.0 | `MANIFEST.MF`, `feature.xml`, all `pom.xml` files |

---

## Backward Compatibility

All changes are backward compatible. Gson leaves fields absent from existing
workflow JSON at their Java defaults: `bold` → `false`, `name` → `null`.
`StepLabelProvider.Name` already handles `name == null` by falling back to the
action's display name, then to the raw action ID. No migration or data conversion
is needed.

---

## Feature Specifications

### 1. Bold Step Flag

**Model (`Step.java`):**
Add `private boolean bold;` with `isBold()` / `setBold(boolean)`. Default
`false`. Gson persists it; absent from old JSON → `false` (backward compatible).

**Rendering (`StepLabelProvider.Name`):**
Implement `IFontProvider`. Override `getFont(Object element)`:
- If `step.isBold()` → return `JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT)`
- Otherwise → return `null` (JFace interprets `null` as the default viewer font)

**Properties view (`StepPropertySource`):**
Add property `PROP_BOLD = "bold"` in the "Step" category, after `PROP_NAME`,
before the Config properties. Use a `ComboBoxPropertyDescriptor` with items
`["No", "Yes"]`:
- `getPropertyValue("bold")` → `step.isBold() ? 1 : 0`
- `setPropertyValue("bold", value)` → `step.setBold((Integer) value == 1)` + save
- `resetPropertyValue("bold")` → `step.setBold(false)` + save
- `isPropertySet("bold")` → `step.isBold()` (non-default = true)

---

### 2. Workspace-Parent Default for Dir/File Configs

**When to apply:** For every config key where `isDirField(key)` or
`isFileField(key)` returns `true` AND the action's `getDefaultConfig()` value
for that key is blank (empty string or null). All current actions have blank
dir/file defaults, so workspace parent always applies in practice.

**Workspace parent computation (shared static helper):**
```java
static String workspaceParent() {
    return ResourcesPlugin.getWorkspace().getRoot()
        .getLocation().toFile().getParent();
}
```

**Pre-fill at step creation (`AutomationView.onAddStep()`):**
After `dialog.getResult()`, iterate `action.getDefaultConfig().keySet()`. For
each key satisfying the condition above, call
`step.getConfig().put(key, workspaceParent())` if the step's current value for
that key is blank. This ensures the user sees a useful default immediately when
a step is added.

**Reset in properties view (`StepPropertySource.resetPropertyValue()`):**
For dir/file/path keys satisfying the condition above, reset to
`workspaceParent()` rather than the action's blank default. For all other keys,
existing behaviour (use `action.getDefaultConfig()`) is unchanged.

---

### 3. Duplicate Step Button

**Toolbar:** New `duplicateStepItem` inserted after `moveDownItem`, before the
run separator. Icon: `ISharedImages.IMG_TOOL_COPY`. Tooltip: `"Duplicate Step"`.

**Deep copy:**
```java
static Step deepCopy(Step src) {
    Step copy = new Step(src.getActionId());
    copy.setName(src.getName());
    copy.setBold(src.isBold());
    copy.setConfig(new HashMap<>(src.getConfig()));
    return copy;
}
```

**`onDuplicate()` logic:**
1. Get sorted selected indices from `viewer.getTable().getSelectionIndices()`.
2. Build ordered list of deep-copied steps.
3. Insert them into `currentWorkflow.getSteps()` starting at `lastSelectedIndex + 1`.
4. Save, refresh viewer, select the newly inserted rows.

---

### 4. Multi-Select Move Up / Move Down / Duplicate

**Contiguity helper:**
```java
static boolean isContiguous(int[] sortedIndices) {
    if (sortedIndices.length == 0) return false;
    return sortedIndices[sortedIndices.length - 1] - sortedIndices[0]
           == sortedIndices.length - 1;
}
```

**`updateButtonStates()` changes:**
- Replace `selCount == 1 && selIdx > 0` with `isContiguous(selIndices) && selIndices[0] > 0` for Move Up.
- Replace `selCount == 1 && selIdx < stepCount - 1` with `isContiguous(selIndices) && selIndices[selIndices.length-1] < stepCount - 1` for Move Down.
- Duplicate enabled when `isContiguous(selIndices)` (any size ≥ 1).

**`onMoveUp()` — block move:**
Extract the contiguous block, remove it from the list, re-insert at
`firstIndex − 1`. Re-select the block at its new positions.

**`onMoveDown()` — block move:**
Extract the contiguous block, remove it from the list, re-insert at
`firstIndex + 1`. Re-select the block at its new positions.

---

### 5. Edit Step Name in Properties View

`StepPropertySource` currently exposes only `PROP_ACTION` (read-only) in the
"Step" category. Add `PROP_NAME = "name"` as a `TextPropertyDescriptor` in the
same category, displayed before `PROP_BOLD`:

- `getPropertyValue("name")` → `step.getName() != null ? step.getName() : ""`
- `setPropertyValue("name", value)` → `step.setName(((String) value).isBlank() ? null : (String) value)` + save
- `resetPropertyValue("name")` → `step.setName(null)` + save
- `isPropertySet("name")` → `step.getName() != null && !step.getName().isBlank()`

Setting name to blank resets it to `null` so the label falls back to the
action's display name, consistent with existing `StepLabelProvider.Name`
behaviour.

---

### 6. Fix ProjectComboBoxCellEditor

**Root cause:** Project names are loaded in `createControl()`, which is called
once when the cell editor widget is first created. If projects are added/removed
after that point, the combo list is stale.

**Fix:** Move the population logic from `createControl()` to `doSetFocus()`.
Every time the user activates the combo cell, the list is rebuilt from
`ResourcesPlugin.getWorkspace().getRoot().getProjects()`. No filtering — all
projects (open and closed) are included. `createControl()` still creates the
`Combo` widget, just with an empty item list initially.

```java
@Override
protected void doSetFocus() {
    IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
    String[] names = Arrays.stream(projects)
        .map(IProject::getName)
        .sorted(Comparator.naturalOrder())
        .toArray(String[]::new);
    combo.setItems(names);
    combo.setFocus();
}
```

---

### 7. Version Bump 1.1.0 → 1.2.0

Change every occurrence of `1.1.0` → `1.2.0` (and `1.1.0-SNAPSHOT` →
`1.2.0-SNAPSHOT` in Maven files) in:

| File | Change |
|------|--------|
| `com.example.automation/META-INF/MANIFEST.MF` | `Bundle-Version: 1.2.0.qualifier` |
| `com.example.automation.feature/feature.xml` | `version="1.2.0.qualifier"` (feature + plugin element) |
| `com.example.automation.feature/pom.xml` | `<version>1.2.0-SNAPSHOT</version>` |
| `com.example.automation/pom.xml` | `<version>1.2.0-SNAPSHOT</version>` |
| `com.example.automation.site/pom.xml` | `<version>1.2.0-SNAPSHOT</version>` |
| `com.example.automation.parent/pom.xml` | parent `<version>1.2.0-SNAPSHOT</version>` |
| Any other child `pom.xml` declaring the version | same |

Tycho maps `1.2.0.qualifier` to a timestamped qualifier at build time (e.g.
`1.2.0.202606261310`). Eclipse p2 will recognise this as a newer version than
any installed `1.1.0.*` and offer it as an update.

---

## Testing

### Unit tests (no OSGi harness — pure Java)

| Test class | What it covers |
|-----------|----------------|
| `StepTest` | `bold` defaults to `false`; Gson round-trip preserves `bold=true`; old JSON without `bold` field deserialises to `false` |
| `StepPropertySourceTest` | `name` property appears in "Step" category and is settable/resettable; `bold` property appears as index 0/1 and is settable/resettable; workspace-parent default applied on reset for dir/file keys |
| `AutomationViewUnitTest` (new) | `isContiguous` helper: contiguous returns true, non-contiguous returns false, single-element returns true; `deepCopy` preserves all fields and produces independent config map. Both helpers must be package-private statics on `AutomationView` (or extracted to a package-private `StepOperations` helper class) so they are testable without instantiating the ViewPart. |

### SWTBot integration tests (existing OSGi harness)

All new tests go in the existing `com.example.automation.tests` bundle.

| Test class | Scenarios |
|-----------|-----------|
| `BoldStepTest` | Add step → set bold=Yes in Properties → table row font is bold; set bold=No → font reverts |
| `DuplicateStepTest` | Duplicate single step → copy inserted below original; duplicate contiguous 2-step selection → both copies inserted below last selected; Duplicate button disabled for non-contiguous selection |
| `MultiSelectMoveTest` | Select rows 1+2, Move Up → block shifts to rows 0+1; Select rows 0+1, Move Up → button disabled; Select rows 1+2, Move Down → block shifts to rows 2+3; non-contiguous selection → Move Up/Down disabled |
| `StepNameEditTest` | Double-click step → Properties view opens; edit name field → table Name column updates; clear name → falls back to action display name |
| `ProjectComboBoxTest` | Properties view for a step with `projectName` config → combo contains workspace project names; reflects projects present at the time of opening (not stale) |

---

## File Change Summary

| File | Change type |
|------|-------------|
| `model/Step.java` | Add `bold` field |
| `StepLabelProvider.java` | `Name` implements `IFontProvider` |
| `StepPropertySource.java` | Add `name` + `bold` properties; workspace-parent reset for dir/file |
| `AutomationView.java` | Duplicate button; multi-select move; pre-fill workspace-parent defaults |
| `ProjectComboBoxCellEditor.java` | Move population to `doSetFocus()` |
| `MANIFEST.MF` | Version 1.2.0.qualifier |
| `feature.xml` | Version 1.2.0.qualifier |
| All `pom.xml` files | Version 1.2.0-SNAPSHOT |
| `StepTest.java` (new or extend) | Bold field unit tests |
| `StepPropertySourceTest.java` (new) | Property source unit tests |
| `AutomationViewUnitTest.java` (new) | `isContiguous` + `deepCopy` unit tests |
| `BoldStepTest.java` (new) | SWTBot bold rendering tests |
| `DuplicateStepTest.java` (new) | SWTBot duplicate tests |
| `MultiSelectMoveTest.java` (new) | SWTBot multi-select move tests |
| `StepNameEditTest.java` (new) | SWTBot step name edit tests |
| `ProjectComboBoxTest.java` (new) | SWTBot project combo tests |
