# JavaDoc — Design Spec

**Goal:** Add Javadoc to all 36 production Java files in `com.example.automation/src/main/java/`, covering public API only (level A).

---

## Scope

**In scope:**
- Class-level Javadoc on every class and interface
- `@param`, `@return`, `@throws` on every public method
- `{@link}` references where helpful

**Out of scope:**
- Private and package-private methods (no Javadoc added)
- `@author`, `@version`, `@since` tags (never)
- `package-info.java` files (none created)
- Fields (no Javadoc on fields)

---

## Comment Style Rules

### Class-level

One-sentence minimum describing what the class does and its role in the plugin. Two or three sentences when the class has non-obvious lifecycle or design constraints.

```java
/**
 * Registry for actions contributed via the {@code com.example.automation.actions}
 * extension point. Loaded once on first access; use {@link #getInstance()} in
 * production code and the list constructor only in tests.
 */
public class ActionRegistry { ... }
```

```java
/**
 * Context passed to an {@link IAction} during execution, providing I/O streams,
 * progress reporting, and cancellation support.
 */
public interface IActionContext { ... }
```

```java
/**
 * A named sequence of {@link Step} objects that the workflow runner executes in order.
 */
public class Workflow { ... }
```

### Method-level

Add Javadoc only when a tag carries real information beyond what the signature already says. Omit prose if the first sentence would just restate the method name.

**`@param`** — add when the parameter contract is non-obvious (e.g., nullable, percentage, units).

**`@return`** — add on every non-void public method. One phrase, no trailing period.

**`@throws`** — add for every checked exception in the `throws` clause.

```java
/**
 * Returns the action registered under the given ID.
 *
 * @param id the action ID as declared in the extension point; must not be null
 * @return the action, or {@code null} if no action is registered under that ID
 */
public IAction getAction(String id) { ... }
```

```java
/**
 * Executes this action with the given configuration and context.
 *
 * @param config key-value pairs from the workflow step; keys defined by each action
 * @param context provides I/O streams, progress reporting, and cancellation
 * @throws Exception if the action fails; message is shown to the user
 */
void execute(Map<String, String> config, IActionContext context) throws Exception;
```

Getters/setters on plain data classes (e.g., `Workflow`, `Step`) get `@return` only — no prose:

```java
/** @return the unique workflow identifier */
public String getWorkflowId() { return workflowId; }
```

---

## Package Breakdown

All files are under `com.example.automation/src/main/java/com/example/automation/`.

| Task | Package path | Files |
|------|-------------|-------|
| 1 | `api/` | ActionRegistry, IAction, IActionContext |
| 2 | `model/` | Step, StepStatus, Workflow |
| 3 | `actions/` (part 1) | ExecuteRunConfigAction, GitCheckoutAction, GitCloneAction, ImportMavenProjectAction, MavenProgressParser, MavenRunWithProgressAction, MavenUpdateProjectAction |
| 4 | `actions/` (part 2) | ProcessRunner, RefreshAllAction, RefreshProjectAction, SetActiveTargetPlatformAction, SetMavenSettingsAction, ShellCommandAction, WriteFileAction |
| 5 | root + prefs/persistence | Activator, AddStepDialog, AutomationView, BundledWorkflowInstaller, MultiLineTextCellEditor, NewWorkflowDialog, ShowAutomationViewHandler, StepAdapterFactory, StepLabelProvider, StepPropertySource, WorkflowPickerDialog, WorkflowRunner, AutomationPreferenceInitializer, AutomationPreferencePage, AutomationPreferences, WorkflowRepository |

**Note on `ProcessRunner`:** It is package-private (`class ProcessRunner`, not `public class`) so it gets a class-level Javadoc but no method-level Javadoc (its only non-private method `run` is package-private).

---

## Verification

After adding Javadoc, run:

```
mvn javadoc:javadoc -pl com.example.automation
```

Expected: BUILD SUCCESS, zero errors. Warnings about missing tags on private methods are acceptable and expected.
