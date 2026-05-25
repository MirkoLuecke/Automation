# Eclipse Automation Plugin — Design Spec

**Date:** 2026-05-24
**Status:** Approved

---

## Overview

A minimal Eclipse plugin that adds an "Automation" menu item to the top-level "Project" menu. The item is always enabled. Clicking it opens a proper Eclipse ViewPart titled "Automation" that displays the text "Preview". The plugin is distributed as a p2 update site built with Maven/Tycho.

---

## Target Environment

| Property | Value |
|---|---|
| Eclipse version | 2023-06 (4.28.0) |
| Maven version | 3.8.1 |
| Build tool | Maven + Tycho 3.0.5 |
| Build JDK | OpenJDK 20.0.1 |
| Plugin bytecode target | Java 17 |
| Target platform | `https://download.eclipse.org/releases/2023-06` |
| Base package / plugin ID | `com.example.automation` |

---

## Project Structure

```
com.example.automation.parent/
├── pom.xml                              ← root aggregator POM (packaging: pom)
├── com.example.automation/              ← eclipse-plugin
│   ├── META-INF/MANIFEST.MF
│   ├── plugin.xml
│   ├── pom.xml
│   └── src/main/java/com/example/automation/
│       ├── Activator.java
│       ├── AutomationView.java
│       └── ShowAutomationViewHandler.java
├── com.example.automation.feature/      ← eclipse-feature
│   ├── feature.xml
│   └── pom.xml
├── com.example.automation.site/         ← eclipse-repository
│   ├── category.xml
│   └── pom.xml
└── com.example.automation.tests/        ← eclipse-test-plugin (SWTBot UI tests)
    ├── META-INF/MANIFEST.MF
    ├── build.properties
    ├── pom.xml
    └── src/main/java/com/example/automation/tests/
        └── AutomationPluginUITest.java
```

---

## Plugin Components

### Activator.java

Standard OSGi bundle activator extending `AbstractUIPlugin`. Provides:
- Plugin ID constant: `com.example.automation`
- Static singleton instance accessible via `Activator.getDefault()`

### AutomationView.java

Extends `ViewPart`. Registered with ID `com.example.automation.view` and title "Automation".

`createPartControl()` creates a single `Label` widget with the text `"Preview"` using `SWT.NONE`, laid out to fill the parent composite. No other controls.

### ShowAutomationViewHandler.java

Implements `AbstractHandler`. The `execute()` method calls:

```java
HandlerUtil.getActiveWorkbenchWindow(event)
    .getActivePage()
    .showView("com.example.automation.view");
```

This opens the view if not already open, or brings it to front if it is.

---

## Extension Points (plugin.xml)

| Extension point | Key attributes |
|---|---|
| `org.eclipse.ui.views` | ID: `com.example.automation.view`, name: "Automation", class: `AutomationView` |
| `org.eclipse.ui.commands` | ID: `com.example.automation.showView`, name: "Automation" |
| `org.eclipse.ui.handlers` | commandId: `com.example.automation.showView`, class: `ShowAutomationViewHandler` |
| `org.eclipse.ui.menus` | locationURI: `menu:project?after=additions`, command: `com.example.automation.showView` |

The menu item is always enabled (no `visibleWhen` or `enabledWhen` expression).

---

## Feature & Update Site

### feature.xml (`com.example.automation.feature`)

- Feature ID: `com.example.automation.feature`
- Version: `1.0.0.qualifier`
- Includes plugin: `com.example.automation`

### category.xml (`com.example.automation.site`)

- Category ID: `com.example.automation.category`
- Category label: "Automation"
- Includes feature: `com.example.automation.feature`

### Build Output

Running `mvn clean verify` from the parent directory compiles, runs UI tests, and produces a p2 repository at:

```
com.example.automation.site/target/repository/
├── artifacts.jar       ← artifact index (file names, sizes, checksums)
├── content.jar         ← installable unit metadata (features, dependencies)
├── features/
│   └── com.example.automation.feature_1.0.0.*.jar
└── plugins/
    └── com.example.automation_1.0.0.*.jar
```

Users install by pointing Eclipse's "Install New Software" at the `repository/` directory or a hosted URL of it.

---

## Testing

UI tests are written with **SWTBot** and run as part of `mvn clean verify`. SWTBot launches a real Eclipse workbench instance and drives it programmatically from a non-UI thread.

**Test project:** `com.example.automation.tests` (`eclipse-test-plugin` packaging)

**SWTBot p2 repository:** `https://download.eclipse.org/technology/swtbot/releases/latest/`

**Key SWTBot bundles required:**
- `org.eclipse.swtbot.eclipse.finder` — provides `SWTWorkbenchBot`, `SWTBotView`
- `org.eclipse.swtbot.swt.finder` — provides `SWTBotJunit4ClassRunner`, `SWTBotLabel`, `SWTBotMenu`

**Test cases in `AutomationPluginUITest`:**

| Test | Assertion |
|---|---|
| `automationMenuItemIsPresent` | "Automation" item exists in Project menu and `isEnabled()` returns true |
| `automationMenuItemOpensAutomationView` | Clicking the item opens a view with title "Automation" |
| `automationViewDisplaysPreviewText` | The opened view contains a label with text "Preview" |

Tests run with `@RunWith(SWTBotJunit4ClassRunner.class)` to ensure execution off the UI thread (required by SWTBot). `tycho-surefire-plugin` is configured with `useUIHarness=true` and `useUIThread=false` in the test project's pom.xml.

---

## What is NOT in scope

- No perspective, toolbar contribution, or keyboard binding
- No preferences page
- No actual automation logic — the view shows only the static text "Preview"

---

## Open Questions

None.
