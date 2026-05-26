# Properties View Integration — Design Spec

**Date:** 2026-05-26
**Status:** Approved
**Sub-project:** 6 of 8 (Workflow Automation)

---

## Overview

When the user selects a step in the `AutomationView` table, the Eclipse Properties View shows that step's properties: a read-only "Action" field (the action ID) and one editable text field per config key. Editing a value saves immediately via `WorkflowRepository`.

---

## Architecture

Three files change; two are new:

| File | Change |
|---|---|
| `com.example.automation/…/AutomationView.java` | Call `getSite().setSelectionProvider(viewer)`; register `StepAdapterFactory` on create; unregister on dispose |
| `com.example.automation/…/StepAdapterFactory.java` | **New.** `IAdapterFactory` — holds `Runnable save`; creates `StepPropertySource` per request |
| `com.example.automation/…/StepPropertySource.java` | **New.** `IPropertySource` — exposes step properties; calls save callback on `setPropertyValue` |

No `plugin.xml` changes. No model changes. `StepPropertySource` takes an injected `ActionRegistry` (same pattern as `WorkflowRunner`) so it can be unit-tested without the OSGi extension point.

### Why programmatic registration

`StepAdapterFactory` is registered programmatically (`Platform.getAdapterManager().registerAdapters`) rather than via `org.eclipse.core.runtime.adapters` in `plugin.xml`. A `plugin.xml`-registered factory must have a no-arg constructor and cannot receive context. The save callback requires access to the view's `currentWorkflow` field, which is only available from `AutomationView`. Programmatic registration with a `Supplier<Workflow>` is the correct Eclipse idiom when an adapter needs view-specific context.

---

## StepPropertySource

### Properties

| Property ID | Descriptor type | Value | Editable |
|---|---|---|---|
| `"action"` | `PropertyDescriptor` (read-only) | `step.getActionId()` | No |
| Each key from `IAction.getDefaultConfig()` | `TextPropertyDescriptor` | `step.getConfig().getOrDefault(key, "")` | Yes |

If the action is not found in the registry, falls back to the keys already present in `step.getConfig()`. If both are empty, only the read-only "Action" property appears.

### Constructor

```java
public StepPropertySource(Step step, ActionRegistry registry, Runnable save)
```

### IPropertySource methods

**`getPropertyDescriptors()`:** Builds the descriptor array — one read-only `PropertyDescriptor` for `"action"`, then one `TextPropertyDescriptor` per config key. Config keys come from `registry.getAction(step.getActionId()).getDefaultConfig()` if the action is known, otherwise from `step.getConfig().keySet()`.

**`getPropertyValue(Object id)`:** Returns `step.getActionId()` for `"action"`; returns `step.getConfig().getOrDefault(id, "")` for config keys.

**`setPropertyValue(Object id, Object value)`:** Puts the new `String` value into `step.getConfig()`, then calls `save.run()`. No validation at edit time — validation is the action's responsibility at execution (`IAction.validate()`).

**`resetToDefault(Object id)`:** Restores the key to `IAction.getDefaultConfig().get(id)` if the action is known and the key exists there, then calls `save.run()`.

**`isPropertySet(Object id)`:** Returns `true` if the key is present in `step.getConfig()` with a value different from the action's default (or present at all if the action is unknown).

**`getEditableValue()`:** Returns `null` (standard no-op for non-replaceable objects).

---

## StepAdapterFactory

```java
public class StepAdapterFactory implements IAdapterFactory {

    private final Runnable save;

    public StepAdapterFactory(Runnable save) {
        this.save = save;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
        if (adapterType == IPropertySource.class && adaptableObject instanceof Step step) {
            return (T) new StepPropertySource(step, ActionRegistry.getInstance(), save);
        }
        return null;
    }

    @Override
    public Class<?>[] getAdapterList() {
        return new Class<?>[] { IPropertySource.class };
    }
}
```

---

## AutomationView Changes

### New field

```java
private StepAdapterFactory adapterFactory;
```

### createPartControl() addition

After `createTable(parent)`:

```java
getSite().setSelectionProvider(viewer);
adapterFactory = new StepAdapterFactory(this::save);
Platform.getAdapterManager().registerAdapters(adapterFactory, Step.class);
```

### dispose() update

```java
@Override
public void dispose() {
    if (adapterFactory != null) Platform.getAdapterManager().unregisterAdapters(adapterFactory);
    if (activeRunner != null) activeRunner.cancel();
    super.dispose();
}
```

The existing `save()` method is reused as the save callback — no changes to it.

---

## Test Coverage

### StepPropertySourceTest (new, plain JUnit 4 — no Eclipse runtime)

Uses a test `ActionRegistry` constructed with `new ActionRegistry(List.of(stubAction))` and a `boolean[]` flag to verify save was called.

| Test | Verifies |
|---|---|
| `actionProperty_isReadOnly` | `"action"` descriptor is a `PropertyDescriptor` (not `TextPropertyDescriptor`); `getPropertyValue("action")` returns the action ID |
| `configProperty_returnsCurrentValue` | `getPropertyValue("key")` returns the value from `step.getConfig()` |
| `setPropertyValue_updatesConfigAndSaves` | After `setPropertyValue("key", "new")`, `step.getConfig().get("key")` equals `"new"` and save was called |
| `resetToDefault_restoresDefaultAndSaves` | After `setPropertyValue` then `resetToDefault`, value returns to `IAction.getDefaultConfig()` value and save was called |
| `unknownAction_fallsBackToExistingConfig` | When action ID not in registry, keys from `step.getConfig()` appear as editable properties |

### AutomationViewTest (no new test)

Properties View property sheet automation in SWTBot requires navigating a complex cell editor widget — fragile and adds little coverage over the unit tests above.

---

## Data Flow

```
User clicks a step row in AutomationView
  └─ TableViewer fires selection change
  └─ getSite().setSelectionProvider(viewer) → workbench selection updated

Properties View (Eclipse built-in)
  └─ receives selection change from workbench
  └─ asks Platform.getAdapterManager() for IPropertySource on selected Step
  └─ StepAdapterFactory.getAdapter(step, IPropertySource.class)
       └─ returns new StepPropertySource(step, registry, save)
  └─ calls getPropertyDescriptors() → builds descriptor array
  └─ calls getPropertyValue(id) → reads from Step

User edits a value in Properties View
  └─ StepPropertySource.setPropertyValue(id, newValue)
       └─ step.getConfig().put(id, newValue)
       └─ save.run() → WorkflowRepository.save(currentWorkflow)
```

---

## What Is NOT in Scope

- New Workflow dialog — sub-project 7
- Built-in action implementations — sub-project 8
- Editing the action ID in the Properties View (action type selection belongs in a dialog)
- Config key validation in the Properties View (handled by `IAction.validate()` at execution time)
- Adding or removing config keys from the Properties View
