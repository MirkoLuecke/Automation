# UI Polish 2 — Icons, Edit Workflow, Docs

## Goal

Fix toolbar icon inconsistencies (Move Up/Down arrows, duplicate Run icons), add an Edit Workflow button that reuses the New Workflow dialog, and update the README installation section with concrete update site instructions.

## Architecture

Four self-contained changes to `AutomationView`, `NewWorkflowDialog`, `Activator`, and `README.md`. No new OSGi dependencies. Two small PNG icons are bundled in the existing `icons/` folder.

## Tech Stack

Java 17, SWT/JFace, Eclipse `ISharedImages`, Eclipse platform image registry via `AbstractUIPlugin.imageDescriptorFromPlugin`, `TitleAreaDialog`.

---

## 1. Icons

### Move Up / Move Down

- **Move Up** already uses `ISharedImages.IMG_TOOL_UP` (up arrow) — no change.
- **Move Down** currently uses `ISharedImages.IMG_TOOL_FORWARD` (right arrow, wrong). Fix: load Eclipse's own `down_nav.png` via:
  ```java
  AbstractUIPlugin.imageDescriptorFromPlugin(
      "org.eclipse.ui", "icons/full/elcl16/down_nav.png")
  ```
  Register in `Activator`'s `ImageRegistry` under key `"down_nav"`. Fall back to `IMG_TOOL_FORWARD` if the descriptor resolves to null (Eclipse version guard).

### Run Workflow / Run Selected Steps

- **Run Workflow** keeps `DebugUITools.getImage(IDebugUIConstants.IMG_ACT_RUN)` (solid green play button).
- **Run Selected Steps** gets a new bundled icon: `icons/run_selected.png` + `icons/run_selected@2x.png` — a green play triangle with a small dotted selection rectangle in the bottom-right corner, 16×16 px, matching Eclipse icon style. Loaded via `Activator.getDefault().getImageRegistry().get("run_selected")`.

### Edit Workflow button icon

- Bundled `icons/edit.png` + `icons/edit@2x.png` — a small pencil icon, 16×16 px. No suitable public Eclipse standard constant exists for a generic edit/pencil icon. Loaded the same way as `run_selected`.

### Activator image registration

`Activator.start()` initialises three entries in its `ImageRegistry`:

| Key | Source |
|-----|--------|
| `"down_nav"` | `imageDescriptorFromPlugin("org.eclipse.ui", "icons/full/elcl16/down_nav.png")` |
| `"run_selected"` | `imageDescriptorFromPlugin(PLUGIN_ID, "icons/run_selected.png")` |
| `"edit"` | `imageDescriptorFromPlugin(PLUGIN_ID, "icons/edit.png")` |

`AutomationView` retrieves images via `Activator.getDefault().getImageRegistry().get(key)`.

---

## 2. Edit Workflow Dialog

### `NewWorkflowDialog` — edit mode

Add a third constructor parameter `Workflow toEdit` (nullable):

```java
// existing — new workflow
public NewWorkflowDialog(Shell parent, Set<String> existingIds) { ... }

// new — edit existing workflow
public NewWorkflowDialog(Shell parent, Set<String> existingIds, Workflow toEdit) { ... }
```

Behaviour when `toEdit != null`:
- Dialog shell title: `"Edit Workflow"`
- `TitleAreaDialog` title: `"Edit Workflow"`
- `TitleAreaDialog` message: `"Edit the name and description of the workflow."`
- Name field pre-filled with `toEdit.getDisplayName()`
- Description field pre-filled with `toEdit.getDescription()` (empty string if null)
- ID derivation skipped — the existing `toEdit.getWorkflowId()` is kept
- Uniqueness check excludes the current workflow's own ID (so saving without changing the name is always valid)
- `getResult()`: sets `toEdit.setName(nameField)` and `toEdit.setDescription(descField)`, returns `toEdit`

Existing `NewWorkflowDialog(Shell, Set<String>)` behaviour is completely unchanged.

### `AutomationView` — toolbar button

New `editWorkflowItem` toolbar button inserted immediately after `newWorkflowItem` (before the Open button):

```
[New Workflow] [Edit Workflow] [Open Workflow] | [Add Step] ...
```

- Icon: `Activator.getDefault().getImageRegistry().get("edit")`
- Tooltip: `"Edit Workflow"`
- Enabled when: a workflow is loaded (`currentWorkflow != null`) AND no runner is active
- `onEditWorkflow()`:
  1. Collect `existingIds` excluding `currentWorkflow.getWorkflowId()`
  2. Open `new NewWorkflowDialog(shell, existingIds, currentWorkflow)`
  3. On `Window.OK`: call `repository().save(currentWorkflow)`, then `updateHeader()` and `updateButtonStates()`
- `updateButtonStates()`: add `editWorkflowItem.setEnabled(!running && hasWorkflow)`

---

## 3. Documentation

### README — Installation section

Replace the current vague "enter the URL of the update site" with:

1. Build the update site locally:
   ```
   cd com.example.automation.parent
   mvn clean verify
   ```
2. Open Eclipse → **Help → Install New Software…**
3. Click **Add…**, then **Local…**, and browse to:
   ```
   com.example.automation.parent/com.example.automation.site/target/repository
   ```
4. Select **Automation** from the feature list and click **Next → Finish**.
5. Restart Eclipse when prompted.

Add a note: if Eclipse reports "no categorised items" or cannot find a version after a rebuild, remove the repository entry in **Manage…** and re-add it (Eclipse caches p2 metadata).

---

## Testing

- `NewWorkflowDialogTest`: add tests for edit-mode constructor — pre-filled fields, unchanged ID, name uniqueness excludes own ID.
- `AutomationViewTest`: existing SWTBot tests must still pass (no regression).
- Manual: verify all four toolbar icons render correctly, edit dialog opens pre-filled, save persists name/description change and header updates.
