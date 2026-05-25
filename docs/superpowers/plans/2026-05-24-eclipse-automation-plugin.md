# Eclipse Automation Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Eclipse plugin that adds an "Automation" item to the Project menu, which opens an "Automation" ViewPart displaying the text "Preview", packaged as a p2 update site buildable with `mvn clean verify`.

**Architecture:** Five-project Maven/Tycho structure — parent aggregator POM, eclipse-plugin project, eclipse-feature project, eclipse-repository (update site) project, and an eclipse-test-plugin project with SWTBot UI tests. The plugin contributes a command, handler, and menu item via `plugin.xml` extension points, and registers a ViewPart that renders a single label.

**Tech Stack:** Java 17 bytecode (built with OpenJDK 20), Maven 3.8.1, Tycho 3.0.5, Eclipse 2023-06 target platform (`https://download.eclipse.org/releases/2023-06`), SWT, SWTBot (UI testing)

---

## File Map

| File | Purpose |
|---|---|
| `com.example.automation.parent/pom.xml` | Root aggregator POM; Tycho bootstrap, compiler config, target platform repository |
| `com.example.automation.parent/.mvn/jvm.config` | JVM `--add-opens` flags required by Maven 3.8.x when running on Java 9+ |
| `com.example.automation.parent/com.example.automation/pom.xml` | Plugin module POM (`eclipse-plugin` packaging) |
| `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF` | OSGi bundle manifest — declares ID, version, dependencies, activator |
| `com.example.automation.parent/com.example.automation/build.properties` | Tells Tycho/PDE what source dirs and resources to include in the jar |
| `com.example.automation.parent/com.example.automation/plugin.xml` | Declares the four extension points: view, command, handler, menu contribution |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/Activator.java` | OSGi bundle activator; provides plugin ID constant and singleton instance |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/AutomationView.java` | ViewPart with a single Label showing "Preview" |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/ShowAutomationViewHandler.java` | Command handler that calls `showView()` to open or bring the view to front |
| `com.example.automation.parent/com.example.automation.feature/pom.xml` | Feature module POM (`eclipse-feature` packaging) |
| `com.example.automation.parent/com.example.automation.feature/feature.xml` | Feature descriptor; groups the plugin as an installable unit |
| `com.example.automation.parent/com.example.automation.site/pom.xml` | Update site module POM (`eclipse-repository` packaging) |
| `com.example.automation.parent/com.example.automation.site/category.xml` | p2 category definition; groups the feature under "Automation" in the Install dialog |
| `com.example.automation.parent/com.example.automation.tests/pom.xml` | Test module POM (`eclipse-test-plugin` packaging); configures `tycho-surefire-plugin` with UI harness |
| `com.example.automation.parent/com.example.automation.tests/META-INF/MANIFEST.MF` | Test bundle manifest; depends on the plugin under test and SWTBot bundles |
| `com.example.automation.parent/com.example.automation.tests/build.properties` | Tells Tycho what source dirs to include |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/AutomationPluginUITest.java` | Three SWTBot UI tests: menu item presence, view opens, "Preview" label visible |

---

> **Testing approach:** SWTBot UI tests (Task 7 + 8) drive a real Eclipse workbench launched by `tycho-surefire-plugin`. They verify the menu item, view opening, and label text end-to-end. Tests run during `mvn clean verify` (the `integration-test` phase). The first run downloads the SWTBot p2 repository in addition to the Eclipse target platform.

---

### Task 1: Bootstrap parent project

**Files:**
- Create: `com.example.automation.parent/pom.xml`
- Create: `com.example.automation.parent/.mvn/jvm.config`

- [ ] **Step 1: Initialize git and create the parent directory**

```powershell
cd C:\Users\mirko\test
git init
New-Item -ItemType Directory -Path com.example.automation.parent\.mvn -Force
```

- [ ] **Step 2: Create `.mvn/jvm.config`**

Create `com.example.automation.parent/.mvn/jvm.config` with this exact content (no trailing newline needed):

```
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED
```

These flags prevent `InaccessibleObjectException` errors when Maven's internal reflection runs on Java 9+.

- [ ] **Step 3: Create `com.example.automation.parent/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.example.automation</groupId>
  <artifactId>com.example.automation.parent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <modules>
    <module>com.example.automation</module>
    <module>com.example.automation.feature</module>
    <module>com.example.automation.site</module>
    <module>com.example.automation.tests</module>
  </modules>

  <properties>
    <tycho.version>3.0.5</tycho.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <repositories>
    <repository>
      <id>eclipse-2023-06</id>
      <layout>p2</layout>
      <url>https://download.eclipse.org/releases/2023-06</url>
    </repository>
    <repository>
      <id>swtbot</id>
      <layout>p2</layout>
      <url>https://download.eclipse.org/technology/swtbot/releases/latest/</url>
    </repository>
  </repositories>

  <build>
    <plugins>
      <plugin>
        <groupId>org.eclipse.tycho</groupId>
        <artifactId>tycho-maven-plugin</artifactId>
        <version>${tycho.version}</version>
        <extensions>true</extensions>
      </plugin>
      <plugin>
        <groupId>org.eclipse.tycho</groupId>
        <artifactId>tycho-compiler-plugin</artifactId>
        <version>${tycho.version}</version>
        <configuration>
          <source>17</source>
          <target>17</target>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.eclipse.tycho</groupId>
        <artifactId>target-platform-configuration</artifactId>
        <version>${tycho.version}</version>
        <configuration>
          <environments>
            <environment>
              <os>win32</os>
              <ws>win32</ws>
              <arch>x86_64</arch>
            </environment>
            <environment>
              <os>linux</os>
              <ws>gtk</ws>
              <arch>x86_64</arch>
            </environment>
            <environment>
              <os>macosx</os>
              <ws>cocoa</ws>
              <arch>x86_64</arch>
            </environment>
          </environments>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 4: Commit**

```powershell
cd C:\Users\mirko\test
git add com.example.automation.parent/pom.xml com.example.automation.parent/.mvn/jvm.config
git commit -m "feat: add parent POM and Maven JVM config"
```

---

### Task 2: Create plugin project scaffold

**Files:**
- Create: `com.example.automation.parent/com.example.automation/pom.xml`
- Create: `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`
- Create: `com.example.automation.parent/com.example.automation/build.properties`
- Create: `com.example.automation.parent/com.example.automation/plugin.xml`

- [ ] **Step 1: Create directory structure**

```powershell
New-Item -ItemType Directory -Path "com.example.automation.parent\com.example.automation\META-INF" -Force
New-Item -ItemType Directory -Path "com.example.automation.parent\com.example.automation\src\main\java\com\example\automation" -Force
```

- [ ] **Step 2: Create `com.example.automation/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example.automation</groupId>
    <artifactId>com.example.automation.parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>com.example.automation</artifactId>
  <packaging>eclipse-plugin</packaging>
</project>
```

- [ ] **Step 3: Create `META-INF/MANIFEST.MF`**

**Critical:** this file must end with a blank line (trailing newline). OSGi will fail to parse it otherwise.

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Automation
Bundle-SymbolicName: com.example.automation;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Activator: com.example.automation.Activator
Require-Bundle: org.eclipse.ui,
 org.eclipse.core.runtime,
 org.eclipse.core.commands
Bundle-RequiredExecutionEnvironment: JavaSE-17
Bundle-ActivationPolicy: lazy

```

`singleton:=true` is required because the bundle contributes extension points. `Require-Bundle` lists the three bundles whose packages we import directly. Continuation lines start with exactly one space.

- [ ] **Step 4: Create `build.properties`**

```properties
source.. = src/main/java/
output.. = target/classes/
bin.includes = META-INF/,\
               .,\
               plugin.xml
```

`source..` tells Tycho where Java source lives. `bin.includes` declares what gets packaged into the plugin jar: the manifest, the compiled classes (`.`), and the plugin descriptor.

- [ ] **Step 5: Create `plugin.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>

  <extension point="org.eclipse.ui.views">
    <view
        id="com.example.automation.view"
        name="Automation"
        class="com.example.automation.AutomationView"
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
          label="Automation"/>
    </menuContribution>
  </extension>

</plugin>
```

`locationURI="menu:project?after=additions"` inserts the item inside the top-level Project menu, after the standard `additions` separator. No `visibleWhen` or `enabledWhen` means the item is always visible and enabled.

- [ ] **Step 6: Commit**

```powershell
cd C:\Users\mirko\test
git add com.example.automation.parent/com.example.automation/
git commit -m "feat: add plugin project scaffold (POM, manifest, build.properties, plugin.xml)"
```

---

### Task 3: Implement plugin Java classes

**Files:**
- Create: `...com.example.automation/src/main/java/com/example/automation/Activator.java`
- Create: `...com.example.automation/src/main/java/com/example/automation/AutomationView.java`
- Create: `...com.example.automation/src/main/java/com/example/automation/ShowAutomationViewHandler.java`

All three files live in `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/`.

- [ ] **Step 1: Create `Activator.java`**

```java
package com.example.automation;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.example.automation";

    private static Activator plugin;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return plugin;
    }
}
```

- [ ] **Step 2: Create `AutomationView.java`**

```java
package com.example.automation;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.ViewPart;

public class AutomationView extends ViewPart {

    public static final String ID = "com.example.automation.view";

    @Override
    public void createPartControl(Composite parent) {
        Label label = new Label(parent, SWT.NONE);
        label.setText("Preview");
    }

    @Override
    public void setFocus() {}
}
```

`ViewPart` requires both `createPartControl` and `setFocus` to be implemented. `setFocus` is intentionally empty — there is only one widget and Eclipse does not need explicit focus delegation here.

- [ ] **Step 3: Create `ShowAutomationViewHandler.java`**

```java
package com.example.automation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

public class ShowAutomationViewHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            HandlerUtil.getActiveWorkbenchWindow(event)
                .getActivePage()
                .showView(AutomationView.ID);
        } catch (PartInitException e) {
            throw new ExecutionException("Failed to open Automation view", e);
        }
        return null;
    }
}
```

`showView` opens the view if not already visible, or brings it to front if it is. Returning `null` from `execute` is the correct OSGi command contract — the return value is reserved for future use.

- [ ] **Step 4: Commit**

```powershell
cd C:\Users\mirko\test
git add com.example.automation.parent/com.example.automation/src/
git commit -m "feat: implement Activator, AutomationView, ShowAutomationViewHandler"
```

---

### Task 4: Create feature project

**Files:**
- Create: `com.example.automation.parent/com.example.automation.feature/pom.xml`
- Create: `com.example.automation.parent/com.example.automation.feature/feature.xml`

- [ ] **Step 1: Create directory**

```powershell
New-Item -ItemType Directory -Path "com.example.automation.parent\com.example.automation.feature" -Force
```

- [ ] **Step 2: Create `com.example.automation.feature/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example.automation</groupId>
    <artifactId>com.example.automation.parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>com.example.automation.feature</artifactId>
  <packaging>eclipse-feature</packaging>
</project>
```

- [ ] **Step 3: Create `feature.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feature
    id="com.example.automation.feature"
    label="Automation"
    version="1.0.0.qualifier">

  <plugin
      id="com.example.automation"
      download-size="0"
      install-size="0"
      version="0.0.0"
      unpack="false"/>

</feature>
```

`version="0.0.0"` in the `<plugin>` element is a Tycho convention meaning "use whatever version the plugin resolves to at build time" — Tycho replaces it with the actual version. `unpack="false"` keeps the plugin as a jar rather than an exploded directory.

- [ ] **Step 4: Commit**

```powershell
cd C:\Users\mirko\test
git add com.example.automation.parent/com.example.automation.feature/
git commit -m "feat: add feature project"
```

---

### Task 5: Create update site project

**Files:**
- Create: `com.example.automation.parent/com.example.automation.site/pom.xml`
- Create: `com.example.automation.parent/com.example.automation.site/category.xml`

- [ ] **Step 1: Create directory**

```powershell
New-Item -ItemType Directory -Path "com.example.automation.parent\com.example.automation.site" -Force
```

- [ ] **Step 2: Create `com.example.automation.site/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example.automation</groupId>
    <artifactId>com.example.automation.parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>com.example.automation.site</artifactId>
  <packaging>eclipse-repository</packaging>
</project>
```

- [ ] **Step 3: Create `category.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<site>
  <feature
      url="features/com.example.automation.feature_1.0.0.qualifier.jar"
      id="com.example.automation.feature"
      version="1.0.0.qualifier">
    <category name="com.example.automation.category"/>
  </feature>
  <category-def name="com.example.automation.category" label="Automation"/>
</site>
```

This groups the feature under an "Automation" category in Eclipse's "Install New Software" dialog. Tycho resolves the `qualifier` placeholder to a timestamp at build time.

- [ ] **Step 4: Commit**

```powershell
cd C:\Users\mirko\test
git add com.example.automation.parent/com.example.automation.site/
git commit -m "feat: add update site project"
```

---

### Task 6: Build and verify

- [ ] **Step 1: Run the full build (compile + tests) from the parent directory**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

The first run downloads the Eclipse 2023-06 target platform and the SWTBot repository (~400–600 MB total). Subsequent runs use the local Maven cache. During the `integration-test` phase, `tycho-surefire-plugin` launches a headless Eclipse workbench to run the SWTBot tests — an Eclipse window briefly appears on screen. This is expected.

Expected last lines:
```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

- [ ] **Step 2: Verify the update site was produced**

```powershell
Get-ChildItem -Recurse com.example.automation.site\target\repository
```

Expected output contains:
```
artifacts.jar
content.jar
features\com.example.automation.feature_1.0.0.*.jar
plugins\com.example.automation_1.0.0.*.jar
```

If these four artifacts are present, the p2 update site is complete and ready to use.

- [ ] **Step 3: Troubleshooting — if the build fails**

Common failure modes:

| Symptom | Cause | Fix |
|---|---|---|
| `InaccessibleObjectException` | Missing JVM flags | Verify `.mvn/jvm.config` exists and has the `--add-opens` lines |
| `Cannot resolve ... org.eclipse.ui` | Network or target platform issue | Check internet access to `download.eclipse.org`; retry |
| `Bundle ... cannot be resolved` | Wrong Require-Bundle | Check `MANIFEST.MF` bundle IDs for typos |
| `plugin.xml` parse error | Malformed XML | Validate `plugin.xml` — check extension point IDs |
| `MANIFEST.MF` parse error | Missing trailing newline | Ensure `MANIFEST.MF` ends with a blank line |

- [ ] **Step 4: Commit docs and finalize**

```powershell
cd C:\Users\mirko\test
git add docs/
git commit -m "docs: add design spec and implementation plan"
```

---

### Task 7: Create test project scaffold

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/pom.xml`
- Create: `com.example.automation.parent/com.example.automation.tests/META-INF/MANIFEST.MF`
- Create: `com.example.automation.parent/com.example.automation.tests/build.properties`

- [ ] **Step 1: Create directory structure**

```powershell
New-Item -ItemType Directory -Path "com.example.automation.parent\com.example.automation.tests\META-INF" -Force
New-Item -ItemType Directory -Path "com.example.automation.parent\com.example.automation.tests\src\main\java\com\example\automation\tests" -Force
```

- [ ] **Step 2: Create `com.example.automation.tests/pom.xml`**

`tycho-surefire-plugin` is configured here (not in the parent POM) so that `useUIHarness` only applies to this test project.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example.automation</groupId>
    <artifactId>com.example.automation.parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>com.example.automation.tests</artifactId>
  <packaging>eclipse-test-plugin</packaging>

  <build>
    <plugins>
      <plugin>
        <groupId>org.eclipse.tycho</groupId>
        <artifactId>tycho-surefire-plugin</artifactId>
        <version>${tycho.version}</version>
        <configuration>
          <useUIHarness>true</useUIHarness>
          <useUIThread>false</useUIThread>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

`useUIHarness=true` — starts a full Eclipse workbench for the tests.
`useUIThread=false` — runs test methods on a non-UI thread, which is required by SWTBot (it drives the UI from a background thread using display sync calls).

- [ ] **Step 3: Create `META-INF/MANIFEST.MF`**

**Critical:** must end with a blank line (trailing newline).

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Automation Tests
Bundle-SymbolicName: com.example.automation.tests
Bundle-Version: 1.0.0.qualifier
Require-Bundle: com.example.automation,
 org.eclipse.swtbot.eclipse.finder,
 org.eclipse.swtbot.swt.finder,
 org.junit,
 org.hamcrest.core
Bundle-RequiredExecutionEnvironment: JavaSE-17

```

- `com.example.automation` — the plugin under test
- `org.eclipse.swtbot.eclipse.finder` — provides `SWTWorkbenchBot` and `SWTBotView`
- `org.eclipse.swtbot.swt.finder` — provides `SWTBotJunit4ClassRunner`, `SWTBotLabel`, `SWTBotMenu`
- `org.junit` and `org.hamcrest.core` — test assertions

- [ ] **Step 4: Create `build.properties`**

```properties
source.. = src/main/java/
output.. = target/classes/
bin.includes = META-INF/,\
               .
```

No `plugin.xml` entry here — the test project has no extension points.

- [ ] **Step 5: Commit**

```powershell
cd C:\Users\mirko\test
git add com.example.automation.parent/com.example.automation.tests/
git commit -m "feat: add test project scaffold"
```

---

### Task 8: Write SWTBot UI tests

**Files:**
- Create: `...com.example.automation.tests/src/main/java/com/example/automation/tests/AutomationPluginUITest.java`

- [ ] **Step 1: Create `AutomationPluginUITest.java`**

```java
package com.example.automation.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotLabel;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SWTBotJunit4ClassRunner.class)
public class AutomationPluginUITest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = new SWTWorkbenchBot();
        try {
            bot.viewByTitle("Welcome").close();
        } catch (WidgetNotFoundException e) {
            // Welcome view not open in this Eclipse instance, ignore
        }
    }

    @Test
    public void automationMenuItemIsPresent() {
        SWTBotMenu item = bot.menu("Project").menu("Automation");
        assertTrue("Automation menu item should be enabled", item.isEnabled());
    }

    @Test
    public void automationMenuItemOpensAutomationView() {
        bot.menu("Project").menu("Automation").click();
        SWTBotView view = bot.viewByTitle("Automation");
        assertNotNull("Automation view should be open", view);
    }

    @Test
    public void automationViewDisplaysPreviewText() {
        bot.menu("Project").menu("Automation").click();
        bot.viewByTitle("Automation").show();
        SWTBotLabel label = bot.label("Preview");
        assertEquals("Preview", label.getText());
    }
}
```

**How the tests work:**

- `@RunWith(SWTBotJunit4ClassRunner.class)` — runs each `@Test` method on a background thread. SWTBot dispatches UI interactions via `Display.syncExec` internally, so it is safe to call SWTBot APIs from this non-UI thread.
- `@BeforeClass setUp` — creates the bot once per test class and closes the Welcome screen if present (it can obscure the workbench menu bar).
- `automationMenuItemIsPresent` — navigates to Project → Automation and asserts `isEnabled()`. This fails fast if the menu contribution was not registered.
- `automationMenuItemOpensAutomationView` — clicks the item and asserts a view with title "Automation" is found. `viewByTitle` throws `WidgetNotFoundException` if the view does not open, causing the test to fail with a clear message.
- `automationViewDisplaysPreviewText` — opens the view, calls `show()` to bring it to front, then finds the "Preview" label in the workbench shell. `bot.label("Preview")` searches all visible labels in the current shell.

- [ ] **Step 2: Commit**

```powershell
cd C:\Users\mirko\test
git add com.example.automation.parent/com.example.automation.tests/src/
git commit -m "test: add SWTBot UI tests for menu item, view, and label"
```

- [ ] **Step 3: Run verify and confirm tests pass**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected output (after BUILD SUCCESS):
```
[INFO] Running com.example.automation.tests.AutomationPluginUITest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

- [ ] **Step 4: Troubleshooting — SWTBot-specific failures**

| Symptom | Cause | Fix |
|---|---|---|
| `WidgetNotFoundException: Could not find menu: Project` | Eclipse opened without the standard workbench menus | Ensure `useUIHarness=true` is set; check that the application under test is the Eclipse IDE workbench |
| `WidgetNotFoundException: Could not find menu: Automation` | Plugin not installed in the test workbench | Check that `com.example.automation` is listed as a dependency in the test MANIFEST.MF |
| `WidgetNotFoundException: Could not find label: Preview` | View opened but label not found | Add `view.show()` before `bot.label()`, or increase the SWTBot timeout with `SWTBotPreferences.TIMEOUT = 10000` in `setUp` |
| `org.eclipse.swtbot.swt.finder` bundle not resolved | SWTBot p2 repository not reachable | Check internet access to `download.eclipse.org/technology/swtbot` |
