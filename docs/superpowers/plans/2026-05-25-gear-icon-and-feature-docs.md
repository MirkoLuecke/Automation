# Gear Icon and Feature Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bundle the Eclipse gear icon (`configs.png`) from `org.eclipse.ui.ide` into the plugin and wire it to the view tab and menu item; add description and copyright to `feature.xml` so users see documentation in the "Install New Software" wizard.

**Architecture:** The icon is extracted from the locally cached `org.eclipse.ui.ide` jar (already downloaded during the initial build), copied into `com.example.automation/icons/`, referenced in `plugin.xml` for both the view and menu command, and declared in `build.properties` so Tycho packages it. The feature documentation is a pure XML edit to `feature.xml`.

**Tech Stack:** Maven 3.8.1, Tycho 3.0.5, Eclipse 2023-06 target platform, PowerShell / Bash (icon extraction via `jar` tool)

---

## File Map

| File | Change |
|---|---|
| `com.example.automation.parent/com.example.automation/icons/configs.png` | New — 16×16 gear icon extracted from `org.eclipse.ui.ide` |
| `com.example.automation.parent/com.example.automation/icons/configs@2x.png` | New — 32×32 HiDPI gear icon |
| `com.example.automation.parent/com.example.automation/build.properties` | Add `icons/` to `bin.includes` |
| `com.example.automation.parent/com.example.automation/plugin.xml` | Add `icon="icons/configs.png"` to `<view>` and to `<command>` in menu contribution |
| `com.example.automation.parent/com.example.automation.feature/feature.xml` | Add `<description>` and `<copyright>` |

---

> **Note on testing:** Icon appearance is visual and not covered by the SWTBot tests. Verification is the Tycho build succeeding and the icons being present in the built jar.

---

### Task 1: Extract gear icons from cached Eclipse jar

**Files:**
- Create: `com.example.automation.parent/com.example.automation/icons/configs.png`
- Create: `com.example.automation.parent/com.example.automation/icons/configs@2x.png`

- [ ] **Step 1: Create the icons directory**

```powershell
New-Item -ItemType Directory -Path "C:\Users\mirko\test\com.example.automation.parent\com.example.automation\icons" -Force
```

- [ ] **Step 2: Find the org.eclipse.ui.ide jar in the Maven cache**

```bash
find "$HOME/.m2/repository/.cache/tycho" -name "org.eclipse.ui.ide_*.jar" | head -5
```

Note the full path of the jar — you will use it in Step 3. It will be something like:
```
/c/Users/mirko/.m2/repository/.cache/tycho/https/.../plugins/org.eclipse.ui.ide_3.21.0.v20230526-1500.jar
```

- [ ] **Step 3: List available configs icon variants inside the jar**

Replace `<JAR>` with the path from Step 2:

```bash
jar tf "<JAR>" | grep "configs"
```

Expected output (look for `elcl16` and/or `dlcl16` variants):
```
icons/full/elcl16/configs.png
icons/full/elcl16/configs@2x.png
icons/full/dlcl16/configs.png
icons/full/dlcl16/configs@2x.png
```

Use `elcl16` (enabled, full color) if present. Fall back to `dlcl16` only if `elcl16` is absent.

- [ ] **Step 4: Extract the icons into a temporary directory**

```bash
mkdir -p /tmp/eclipse-icons
cd /tmp/eclipse-icons
jar xf "<JAR>" "icons/full/elcl16/configs.png" "icons/full/elcl16/configs@2x.png"
```

If `elcl16` is not listed (Step 3 showed only `dlcl16`), extract those instead:
```bash
jar xf "<JAR>" "icons/full/dlcl16/configs.png" "icons/full/dlcl16/configs@2x.png"
```

- [ ] **Step 5: Copy icons into the plugin**

```bash
cp /tmp/eclipse-icons/icons/full/elcl16/configs.png \
   "C:/Users/mirko/test/com.example.automation.parent/com.example.automation/icons/configs.png"

cp /tmp/eclipse-icons/icons/full/elcl16/configs@2x.png \
   "C:/Users/mirko/test/com.example.automation.parent/com.example.automation/icons/configs@2x.png"
```

(Replace `elcl16` with `dlcl16` if that was the fallback chosen in Step 4.)

- [ ] **Step 6: Verify the files are present**

```bash
ls -la "C:/Users/mirko/test/com.example.automation.parent/com.example.automation/icons/"
```

Expected: both `configs.png` and `configs@2x.png` are listed with non-zero file sizes.

- [ ] **Step 7: Commit**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation/icons/"
git commit -m "feat: add gear icon extracted from org.eclipse.ui.ide"
```

---

### Task 2: Update build.properties to package the icons

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/build.properties`

Current content:
```properties
source.. = src/main/java/
output.. = target/classes/
bin.includes = META-INF/,\
               .,\
               plugin.xml
```

- [ ] **Step 1: Add `icons/` to bin.includes**

Replace the file with:

```properties
source.. = src/main/java/
output.. = target/classes/
bin.includes = META-INF/,\
               .,\
               plugin.xml,\
               icons/
```

- [ ] **Step 2: Commit**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation/build.properties"
git commit -m "feat: include icons/ folder in plugin jar"
```

---

### Task 3: Wire icon into plugin.xml

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/plugin.xml`

- [ ] **Step 1: Add icon to the view declaration and the menu command**

Replace the entire `plugin.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>

  <extension point="org.eclipse.ui.views">
    <view
        id="com.example.automation.view"
        name="Automation"
        class="com.example.automation.AutomationView"
        icon="icons/configs.png"
        restorable="true"/>
  </extension>

  <extension point="org.eclipse.ui.commands">
    <command
        id="com.example.automation.showView"
        name="Automation"/>
  </extension>

  <extension point="org.eclipse.ui.handlers">
    <handler
        commandId="com.example.automation.showView"
        class="com.example.automation.ShowAutomationViewHandler"/>
  </extension>

  <extension point="org.eclipse.ui.menus">
    <menuContribution locationURI="menu:project?after=additions">
      <command
          commandId="com.example.automation.showView"
          label="Automation"
          icon="icons/configs.png"/>
    </menuContribution>
  </extension>

</plugin>
```

The `icon="icons/configs.png"` path is relative to the plugin root. Eclipse resolves `configs@2x.png` automatically for HiDPI displays — no extra declaration needed.

- [ ] **Step 2: Commit**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation/plugin.xml"
git commit -m "feat: add gear icon to view tab and menu item"
```

---

### Task 4: Add documentation to feature.xml

**Files:**
- Modify: `com.example.automation.parent/com.example.automation.feature/feature.xml`

- [ ] **Step 1: Add description and copyright**

Replace the entire `feature.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feature
    id="com.example.automation.feature"
    label="Automation"
    version="1.0.0.qualifier">

  <description>
    Adds an &quot;Automation&quot; item to the Project menu.
    Clicking it opens the Automation view.
  </description>

  <copyright>
    Copyright 2026 com.example
  </copyright>

  <plugin
      id="com.example.automation"
      download-size="0"
      install-size="0"
      version="0.0.0"
      unpack="false"/>

</feature>
```

- [ ] **Step 2: Commit**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation.feature/feature.xml"
git commit -m "feat: add description and copyright to feature.xml"
```

---

### Task 5: Build and verify

- [ ] **Step 1: Run the full build**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected last lines:
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Step 2: Verify the icon is inside the built plugin jar**

```bash
jar tf "com.example.automation/target/com.example.automation-1.0.0-SNAPSHOT.jar" | grep "configs"
```

Expected:
```
icons/configs.png
icons/configs@2x.png
```

If these lines are missing, `icons/` was not added to `bin.includes` — recheck Task 2.

- [ ] **Step 3: Verify the feature jar contains the description**

```bash
jar xf "com.example.automation.feature/target/com.example.automation.feature-1.0.0-SNAPSHOT.jar" \
    feature.xml -C /tmp/feature-check && cat /tmp/feature-check/feature.xml
```

Expected: `<description>` and `<copyright>` elements are present in the output.

- [ ] **Step 4: Troubleshooting**

| Symptom | Cause | Fix |
|---|---|---|
| `configs.png` missing from jar | `icons/` not in `bin.includes` | Check `build.properties` — ensure the trailing backslash and `icons/` line are present |
| Icon not shown in Eclipse after install | Plugin jar doesn't include icons | Verify Step 2 of this task; reinstall the plugin |
| `elcl16/configs.png` not found in jar | Not all variants exist in this Eclipse version | Use `dlcl16` fallback as described in Task 1 Step 3 |
