# Action Extension Point and API — Design Spec

**Date:** 2026-05-25
**Status:** Approved
**Sub-project:** 2 of 8 (Workflow Automation)

---

## Overview

Define the `IAction` contract that all workflow actions implement, declare an Eclipse extension point so third-party plugins can contribute actions, and provide an `ActionRegistry` that discovers and caches contributed actions at runtime.

Properties View UI and the two built-in actions (Refresh Project, Maven Build) are explicitly out of scope — they are covered in sub-projects 6 and 8 respectively.

---

## Action Contract

Package: `com.example.automation.api`

### `IAction`

```java
public interface IAction {
    String getId();
    String getName();
    String getDescription();
    Map<String, String> getDefaultConfig();           // never null; return empty map if no defaults
    List<String> validate(Map<String, String> config); // empty list = valid; non-empty = error messages
    void execute(Map<String, String> config, IActionContext context) throws Exception;
}
```

| Method | Contract |
|---|---|
| `getId()` | Unique identifier (e.g. `com.example.automation.action.maven`). Enforced by contributor; no runtime uniqueness check. |
| `getName()` | Human-readable label shown in the UI table. Mandatory, non-blank. |
| `getDescription()` | Short description. Mandatory, non-blank. |
| `getDefaultConfig()` | Initial config values for new steps. Never `null`. |
| `validate(config)` | Called before `execute()`. Empty list = valid; non-empty entries are shown to the user. |
| `execute(config, context)` | Runs the action. Throws `Exception` on fatal error — the execution engine catches it and marks the step RED. Must check `context.isCancelled()` periodically. |

### `IActionContext`

Passed to `execute()` by the execution engine (sub-project 4). The execution engine wires `getOutputStream()` and `getErrorStream()` to per-action Eclipse consoles (sub-project 7). The action only sees `OutputStream` — no Eclipse console API dependency.

```java
public interface IActionContext {
    void setProgress(int percent);   // 0–100; reported to Eclipse Progress View and table status column
    boolean isCancelled();           // check periodically inside execute(); return early if true
    OutputStream getOutputStream();  // action writes stdout here (live-streamed to console)
    OutputStream getErrorStream();   // action writes stderr here (live-streamed to console)
}
```

---

## Extension Point

### Declaration (`plugin.xml`)

```xml
<extension-point
    id="actions"
    name="Automation Actions"
    schema="schema/actions.exsd"/>
```

Full qualified extension point id: `com.example.automation.actions`

### Schema (`schema/actions.exsd`)

Defines a single `<action>` element with one required attribute:

| Attribute | Type | Use | Description |
|---|---|---|---|
| `class` | `java:com.example.automation.api.IAction` | required | Fully qualified class name of the `IAction` implementation. Must have a no-arg constructor. |

### Contributor Example

Any third-party plugin adds to its own `plugin.xml`:

```xml
<extension point="com.example.automation.actions">
  <action class="com.example.other.MyAction"/>
</extension>
```

Eclipse PDE validates the `class` attribute against the schema and provides autocompletion in the XML editor.

---

## ActionRegistry

Package: `com.example.automation.api`

### API

```java
public class ActionRegistry {
    // Production singleton — reads Platform.getExtensionRegistry()
    public static ActionRegistry getInstance();

    // Package-accessible constructor for tests — bypasses extension registry
    ActionRegistry(List<IAction> actions);

    public IAction getAction(String id);          // null if not found
    public List<IAction> getAllActions();          // unmodifiable, registration order
}
```

### Behaviour

- `getInstance()` loads all contributions lazily on first call via `IExtensionRegistry.getConfigurationElementsFor("com.example.automation.actions")` and caches the result.
- Each contribution is instantiated via `IConfigurationElement.createExecutableExtension("class")` — the standard Eclipse pattern for executable extensions.
- If instantiation of a single action fails, the error is logged via `ILog` and that action is skipped; remaining actions still load.
- Duplicate ids: last registration wins (filesystem/bundle order is non-deterministic; contributors are responsible for unique ids).
- `getAllActions()` returns an unmodifiable view.

---

## Files Changed

| File | Change |
|---|---|
| `com.example.automation/src/main/java/com/example/automation/api/IAction.java` | New |
| `com.example.automation/src/main/java/com/example/automation/api/IActionContext.java` | New |
| `com.example.automation/src/main/java/com/example/automation/api/ActionRegistry.java` | New |
| `com.example.automation/schema/actions.exsd` | New |
| `com.example.automation/plugin.xml` | Add `<extension-point>` declaration |
| `com.example.automation/META-INF/MANIFEST.MF` | Add `com.example.automation.api` to `Export-Package` |
| `com.example.automation/build.properties` | Add `schema/` to `bin.includes` |
| `com.example.automation.tests/src/.../ActionRegistryTest.java` | New |
| `com.example.automation.tests/src/.../IActionContractTest.java` | New |

---

## Test Coverage

### `ActionRegistryTest` (JUnit 4, no Eclipse runtime)

Uses the package-accessible `ActionRegistry(List<IAction>)` constructor with two fake `IAction` stubs.

| Test | Verifies |
|---|---|
| `getAllActionsReturnsBoth` | Registry returns all injected actions |
| `getActionFindsById` | `getAction("known-id")` returns the correct action |
| `getActionReturnsNullForUnknown` | `getAction("missing")` returns `null` |
| `duplicateIdLastWins` | Two actions with same id — last one returned |

### `IActionContractTest` (JUnit 4, no Eclipse runtime)

Uses a minimal `IAction` stub and a fake `IActionContext`.

| Test | Verifies |
|---|---|
| `defaultConfigNotNull` | `getDefaultConfig()` never returns null |
| `validateEmptyListForValidConfig` | Returns empty list for a valid config |
| `validateNonEmptyForInvalidConfig` | Returns at least one error message for invalid config |
| `executeChecksIsCancelled` | `execute()` checks `context.isCancelled()` and returns early |
| `executeReportsProgress` | `execute()` calls `context.setProgress()` at least once |

---

## What Is NOT in Scope

- Built-in actions (Refresh Project, Maven Build) — sub-project 8
- Properties View UI per action — sub-project 6
- Execution engine that calls `execute()` — sub-project 4
- Console wiring (`getOutputStream()` → Eclipse console) — sub-project 7
- Runtime uniqueness enforcement of action ids
