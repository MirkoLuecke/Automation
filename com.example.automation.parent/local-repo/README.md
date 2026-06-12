# Local Installation Packages

This folder provides everything needed to install and develop the Automation Plugin
on a **Eclipse IDE for Java Developers 2023-06** without an internet connection.

---

## Contents

| Path | What it is | In git? |
|------|-----------|---------|
| `p2/automation-plugin/` | Automation Plugin update site (built by `mvn clean install`) | Yes |
| `p2/pde/` | Eclipse Plug-in Development Environment mirror | **No** — generate once (see below) |
| `p2/swtbot/` | SWTBot for Eclipse mirror (needed for development) | **No** — generate once (see below) |
| `mirror-pom.xml` | Tycho mirror configuration that generates `p2/pde/` and `p2/swtbot/` | Yes |
| `patch-p2-category.ps1` | Adds p2 category IUs so Install New Software shows items grouped | Yes |
| `update-automation-plugin.cmd` | Refreshes `p2/automation-plugin/` after a new build | Yes |

---

## One-time setup: generate the mirrors (requires internet)

One developer runs this once on a machine with internet access, then shares the
`local-repo/` folder (USB drive, network share, etc.) with colleagues.

```
cd com.example.automation.parent
mvn package -f local-repo/mirror-pom.xml
```

This downloads:
- Eclipse Plug-in Development Environment → `local-repo/p2/pde/` (~18 MB)
- SWTBot for Eclipse → `local-repo/p2/swtbot/` (~5 MB)

---

## Installing in Eclipse (colleagues — no internet required)

### Prerequisites

- Eclipse IDE for Java Developers 2023-06
- Java 17 JDK configured as the active JRE in Eclipse

---

### Step 1 — Install the Eclipse Plug-in Development Environment (PDE)

PDE is required by the Automation Plugin but is not included in the Java edition of Eclipse.
Skip this step if PDE is already installed (check **Help → About Eclipse IDE → Installation Details**
for "Eclipse Plug-in Development Environment").

1. Open **Help → Install New Software…**
2. Click **Add…** → **Local…** and browse to `local-repo\p2\pde\`
   Give it the name `Local PDE` and click **Add**.
3. Wait for the software list to load, then tick **Eclipse Plug-in Development Environment**.
4. Click **Next** twice, accept the license, and click **Finish**.
5. Restart Eclipse when prompted.

---

### Step 2 — Install the Automation Plugin

1. Open **Help → Install New Software…**
2. Click **Add…** → **Local…** and browse to `local-repo\p2\automation-plugin\`
   Give it the name `Automation Plugin` and click **Add**.
3. Tick **Automation** and click **Next** twice, then **Finish**.
4. Restart Eclipse when prompted.

---

### Step 3 — Verify the installation

After restarting, the **Automation** view should appear in the **Window → Show View** menu
under the *Automation* category. Open it and confirm the view loads without errors.

---

## Developer setup (importing the project into Eclipse)

Developers who want to open and modify the source code need two additional components
installed in their Eclipse before importing the project.

### Step A — Install PDE (if not already installed, see Step 1 above)

### Step B — Install SWTBot

The test suite uses SWTBot. Without it, Eclipse cannot resolve the test project.

1. Open **Help → Install New Software…**
2. Click **Add…** → **Local…** and browse to `local-repo\p2\swtbot\`
   Give it the name `Local SWTBot` and click **Add**.
3. Tick **SWTBot for Eclipse** and click **Next** twice, then **Finish**.
4. Restart Eclipse when prompted.

### Step C — Import the project

1. **File → Import… → Maven → Existing Maven Projects**
2. Browse to the `com.example.automation.parent` directory.
3. Select all projects and click **Finish**.

---

## Keeping the Automation Plugin update site current

After every `mvn clean install`, run the update script to refresh `p2/automation-plugin/`
with the latest build:

```
cd com.example.automation.parent
local-repo\update-automation-plugin.cmd
```

Then re-share or redeploy the `local-repo\p2\automation-plugin\` folder to colleagues.
