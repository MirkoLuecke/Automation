# Gear Icon for Menu Item and View — Design Spec

**Date:** 2026-05-25
**Status:** Approved

---

## Overview

Add the Eclipse standard gear icon (`configs.png`) to both the "Automation" menu item in the Project menu and the "Automation" ViewPart tab. The icon is extracted from Eclipse's own `org.eclipse.ui.ide` plugin and bundled inside the `com.example.automation` plugin for self-containment.

---

## Icon Source

| Property | Value |
|---|---|
| Source plugin | `org.eclipse.ui.ide` (bundled in Eclipse 2023-06) |
| Icon family | `elcl16` (enabled, full color, 16×16) |
| Files | `configs.png` (16×16), `configs@2x.png` (32×32 HiDPI) |
| License | Eclipse Public License 2.0 — compatible with our plugin's use |
| Extraction method | `jar xf` from the cached Maven artifact |

The `elcl16` variant is used because our menu item is always enabled; the `dlcl16` (disabled/grayed) variant is not needed.

---

## Files Changed

| File | Change |
|---|---|
| `com.example.automation/icons/configs.png` | New — 16×16 gear icon |
| `com.example.automation/icons/configs@2x.png` | New — 32×32 HiDPI gear icon |
| `com.example.automation/build.properties` | Add `icons/` to `bin.includes` |
| `com.example.automation/plugin.xml` | Add `icon="icons/configs.png"` to `<view>` and to `<command>` in the menu contribution |

---

## plugin.xml Changes

### View declaration

```xml
<view
    id="com.example.automation.view"
    name="Automation"
    class="com.example.automation.AutomationView"
    icon="icons/configs.png"
    restorable="true"/>
```

### Menu contribution

```xml
<menuContribution locationURI="menu:project?after=additions">
  <command
      commandId="com.example.automation.showView"
      label="Automation"
      icon="icons/configs.png"/>
</menuContribution>
```

---

## build.properties Change

```properties
source.. = src/main/java/
output.. = target/classes/
bin.includes = META-INF/,\
               .,\
               plugin.xml,\
               icons/
```

---

## What is NOT in scope

- No change to the "Preview" label inside the view
- No change to the SWTBot tests (icon presence is a visual concern, not tested)
- No HiDPI-specific plugin.xml wiring — Eclipse resolves `@2x` automatically when the file is present alongside the base icon

---

## Open Questions

None.
