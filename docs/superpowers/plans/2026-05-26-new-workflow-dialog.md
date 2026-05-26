# New Workflow Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stub `onNew()` in `AutomationView` with a `TitleAreaDialog` that collects a display name and optional description, auto-derives a unique workflow ID, saves the workflow, and selects it in the combo.

**Architecture:** `NewWorkflowDialog` extends `TitleAreaDialog`; it receives a `Set<String>` of existing IDs and exposes `getResult()` returning a ready-to-save `Workflow`. `AutomationView.onNew()` opens the dialog and delegates save/refresh to a new `saveNew()` helper. ID generation is a `public static` method `deriveId()` so it can be unit-tested without an Eclipse runtime.

**Tech Stack:** Java 17, Eclipse SWT/JFace (`TitleAreaDialog`), OSGi/Tycho, JUnit 4.

---

## File Structure

| File | Role |
|---|---|
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/NewWorkflowDialogTest.java` | **New.** 5 plain JUnit 4 unit tests for `deriveId()` — no running display needed. |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/NewWorkflowDialog.java` | **New.** `TitleAreaDialog` with Name field, ID preview label, Description field, live validation, and `getResult()`. |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java` | **Modified.** Add two imports; replace `onNew()` stub; add `saveNew(Workflow)` helper. |

No `MANIFEST.MF` changes — `org.eclipse.jface` and `org.eclipse.swt` are already in `Require-Bundle`.

---

### Task 1: Write failing NewWorkflowDialog unit tests

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/NewWorkflowDialogTest.java`

- [ ] **Step 1: Create the test file**

```java
package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

import com.example.automation.NewWorkflowDialog;

public class NewWorkflowDialogTest {

    @Test
    public void deriveId_basic() {
        assertEquals("my-workflow", NewWorkflowDialog.deriveId("My Workflow", Set.of()));
    }

    @Test
    public void deriveId_specialChars() {
        assertEquals("build-release", NewWorkflowDialog.deriveId("Build & Release!!", Set.of()));
    }

    @Test
    public void deriveId_leadingTrailingSpecial() {
        assertEquals("hello", NewWorkflowDialog.deriveId("  !!hello!!", Set.of()));
    }

    @Test
    public void deriveId_conflict() {
        assertEquals("my-workflow-2",
            NewWorkflowDialog.deriveId("My Workflow", Set.of("my-workflow")));
    }

    @Test
    public void deriveId_multipleConflicts() {
        assertEquals("my-workflow-3",
            NewWorkflowDialog.deriveId("My Workflow", Set.of("my-workflow", "my-workflow-2")));
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```
cd com.example.automation.parent
mvn verify -q
```

Expected: `BUILD FAILURE` — `NewWorkflowDialog` does not exist yet.

- [ ] **Step 3: Commit**

```
git add com.example.automation.tests/src/main/java/com/example/automation/tests/NewWorkflowDialogTest.java
git commit -m "test: add failing NewWorkflowDialog deriveId unit tests"
```

---

### Task 2: Implement NewWorkflowDialog

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/NewWorkflowDialog.java`

- [ ] **Step 1: Create NewWorkflowDialog.java**

```java
package com.example.automation;

import java.util.Set;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.example.automation.model.Workflow;

public class NewWorkflowDialog extends TitleAreaDialog {

    private final Set<String> existingIds;
    private Text nameText;
    private Text descriptionText;
    private Label idPreviewLabel;
    private Workflow result;

    public NewWorkflowDialog(Shell parent, Set<String> existingIds) {
        super(parent);
        this.existingIds = existingIds;
    }

    @Override
    public void create() {
        super.create();
        setTitle("New Workflow");
        setMessage("Enter a display name.");
        getButton(OK).setEnabled(false);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        container.setLayout(new GridLayout(2, false));

        new Label(container, SWT.NONE).setText("Name:");
        nameText = new Text(container, SWT.BORDER | SWT.SINGLE);
        nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(container, SWT.NONE); // spacer under "Name:" label
        idPreviewLabel = new Label(container, SWT.NONE);
        idPreviewLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        idPreviewLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

        new Label(container, SWT.NONE).setText("Description:");
        descriptionText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        GridData descData = new GridData(SWT.FILL, SWT.FILL, true, true);
        descData.heightHint = 60;
        descriptionText.setLayoutData(descData);

        nameText.addModifyListener(e -> {
            String name = nameText.getText().trim();
            if (name.isEmpty()) {
                setMessage("Enter a display name.");
                idPreviewLabel.setText("");
                getButton(OK).setEnabled(false);
            } else {
                String id = deriveId(name, existingIds);
                idPreviewLabel.setText("ID: " + id);
                setMessage(null);
                getButton(OK).setEnabled(true);
            }
        });

        return area;
    }

    @Override
    protected void okPressed() {
        String name = nameText.getText().trim();
        String id = deriveId(name, existingIds);
        String description = descriptionText.getText().trim();
        result = new Workflow(id, name, description);
        super.okPressed();
    }

    public Workflow getResult() {
        return result;
    }

    public static String deriveId(String displayName, Set<String> existingIds) {
        String base = displayName.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (!existingIds.contains(base)) {
            return base;
        }
        int counter = 2;
        while (existingIds.contains(base + "-" + counter)) {
            counter++;
        }
        return base + "-" + counter;
    }
}
```

- [ ] **Step 2: Run to confirm 40 tests pass**

```
cd com.example.automation.parent
mvn verify -q
```

Expected: `Tests run: 40, Failures: 0, Errors: 0, Skipped: 0`

(35 existing + 5 new `NewWorkflowDialogTest` tests)

- [ ] **Step 3: Commit**

```
git add com.example.automation/src/main/java/com/example/automation/NewWorkflowDialog.java
git commit -m "feat: add NewWorkflowDialog with TitleAreaDialog and deriveId"
```

---

### Task 3: Wire AutomationView

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java`

- [ ] **Step 1: Add two missing imports**

In `AutomationView.java`, after the existing `import java.util.List;` line, add:

```java
import java.util.Set;
```

After the existing `import org.eclipse.ui.part.ViewPart;` line, add:

```java
import org.eclipse.jface.window.Window;
```

The existing `import java.util.stream.Collectors;` is already present — do not add it again.

- [ ] **Step 2: Replace the onNew() stub**

The current `onNew()` is:

```java
    private void onNew() {
        Platform.getLog(getClass()).info("New workflow: not yet implemented (sub-project 7)");
    }
```

Replace it with:

```java
    private void onNew() {
        Set<String> existingIds = workflows.stream()
            .map(Workflow::getWorkflowId)
            .collect(Collectors.toSet());
        NewWorkflowDialog dialog = new NewWorkflowDialog(getSite().getShell(), existingIds);
        if (dialog.open() == Window.OK) {
            saveNew(dialog.getResult());
        }
    }
```

- [ ] **Step 3: Add saveNew() after onNew()**

Directly after the new `onNew()` method, add:

```java
    private void saveNew(Workflow wf) {
        try {
            new WorkflowRepository().save(wf);
        } catch (Exception e) {
            Platform.getLog(getClass()).error("Failed to save new workflow", e);
            return;
        }
        loadWorkflows();
        for (int i = 0; i < workflows.size(); i++) {
            if (wf.getWorkflowId().equals(workflows.get(i).getWorkflowId())) {
                workflowCombo.select(i);
                currentWorkflow = workflows.get(i);
                viewer.setInput(currentWorkflow.getSteps());
                break;
            }
        }
        updateButtonStates();
    }
```

- [ ] **Step 4: Run all tests**

```
cd com.example.automation.parent
mvn verify -q
```

Expected: `Tests run: 40, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```
git add com.example.automation/src/main/java/com/example/automation/AutomationView.java
git commit -m "feat: wire NewWorkflowDialog into AutomationView.onNew()"
```
