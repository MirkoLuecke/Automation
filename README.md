# Automation Plugin for Eclipse

An Eclipse plugin that lets you define, manage, and run multi-step automation workflows from within the IDE. Workflows are sequences of steps; each step is an action (shell command, git operation, Maven build, etc.) with its own configuration.

**Requirements:** Eclipse 2023-06 or later, Java 17.

---

## Installation

### 1. Build the update site

From a terminal in the repository root:

```
cd com.example.automation.parent
mvn clean verify
```

This produces the p2 update site at:
`com.example.automation.parent/com.example.automation.site/target/repository`

### 2. Install in Eclipse

1. Open Eclipse and go to **Help → Install New Software…**
2. Click **Add…**, then **Local…**, and browse to the `repository` folder above.
3. Select **Automation** from the feature list and click **Next → Finish**.
4. Restart Eclipse when prompted.

> **After a rebuild:** Eclipse caches p2 repository metadata. If you rebuild the plugin and reinstall, go to **Manage…** in the Install Software dialog, remove the existing entry, and re-add it. Otherwise Eclipse may report version errors or "no categorized items."

After installation, open the view via **Project → Automation** or **Window → Show View → Other… → Automation**.

---

## Concepts

| Term | Meaning |
|---|---|
| **Workflow** | A named, ordered list of steps. Stored as a JSON file in the workflow storage folder. |
| **Step** | A single unit of work: one action type plus its configuration values. |
| **Action** | A built-in capability (Shell Command, Git Clone, Maven Run, etc.) registered with the plugin. |

Workflow JSON files are plain text. You can open, read, and edit them in any text editor.

---

## The Automation View

Open via **Project > Automation**. The view has three areas:

**Header** — Shows the name (bold) and description of the currently open workflow. Displays "(no workflows)" when none exist.

**Toolbar** — Icon buttons (hover for tooltip):

| Button | Tooltip | Action |
|---|---|---|
| New Workflow | New Workflow | Opens a dialog to create a new workflow with a name and description. |
| Edit Workflow | Edit Workflow | Edit the name and description of the currently loaded workflow. |
| Open Workflow | Open Workflow | Opens a picker dialog to select an existing workflow. |
| Add Step | Add Step | Opens a dialog showing all available action types; adds the selected action as a new step. |
| Delete Step | Delete Step | Removes the selected step(s). |
| Move Step Up | Move Step Up | Moves the selected step one position up. |
| Move Step Down | Move Step Down | Moves the selected step one position down. |
| Run Workflow | Run Workflow | Runs all steps in order. |
| Run Selected Steps | Run Selected Steps | Runs only the selected steps, in workflow order. |
| Stop | Stop | Cancels the currently running workflow. |

**Step Table** — Three columns:

| Column | Content |
|---|---|
| Status | Colored indicator: white (not run), yellow (running), green (success), red (failed). |
| Name | The action type name (e.g., "Shell Command"). |
| Config | A summary of the step's configuration values. |

---

## Managing Workflows

**Creating a workflow:** Click **New Workflow**. Enter a name and optional description. The workflow ID is derived from the name automatically. The new workflow is saved immediately.

**Opening a workflow:** Click **Open Workflow**. A dialog lists all available workflows with their names and descriptions. Select one and click OK (or double-click).

**Adding a step:** Click **Add Step**. A dialog lists all registered action types with their descriptions. Select one and click OK (or double-click). The step is appended to the end of the current workflow.

**Reordering steps:** Select a single step and use **Move Step Up** or **Move Step Down**.

**Deleting a step:** Select one or more steps and click **Delete Step**.

**Running a workflow:** Click **Run Workflow** to run all steps. Click **Run Selected Steps** to run only the selected rows (in workflow order, regardless of selection order). Output appears in the **Automation** console.

---

## Editing Step Configuration

Select a step in the table. Open the Properties View via **Window > Show View > Properties**.

The Properties View shows two sections:

- **Step** — The action type ID (read-only).
- **Config** — One row per configuration field. Click a value cell to edit it. Press Enter or click elsewhere to confirm.

**Special behaviour for Shell Command:** The `command` field uses a multi-line text editor (five rows tall, with a vertical scrollbar). Press **Ctrl+Enter** to commit the value. The Enter key alone inserts a newline inside the command.

---

## Action Reference

### Shell Command

Executes a shell command. On Windows: `powershell.exe -NonInteractive -Command <command>`. On Linux/macOS: `sh -c <command>`.

| Field | Required | Description |
|---|---|---|
| `command` | Yes | The command to run. May be multi-line (separate commands with newlines). Eclipse variables are supported. |
| `workingDir` | No | Working directory for the command. If blank, uses the **Default working directory** from the preference page. Eclipse variables are supported. |

### Git Clone

Clones a remote git repository to a local directory.

| Field | Required | Description |
|---|---|---|
| `url` | Yes | Repository URL (HTTPS or SSH). |
| `targetDir` | Yes | Local directory to clone into. Eclipse variables are supported. |
| `branch` | No | Branch to check out. If blank, the remote's default branch is used. |

### Git Checkout

Checks out a branch in an existing local repository.

| Field | Required | Description |
|---|---|---|
| `repoDir` | Yes | Path to the local repository. Eclipse variables are supported. |
| `branch` | Yes | Branch name to check out. |

### Maven Run with Progress

Runs a Maven build from the command line and tracks progress from output. On Windows: `powershell.exe -NonInteractive -Command mvn <goals>`. On Linux/macOS: `sh -c mvn <goals>`.

| Field | Required | Description |
|---|---|---|
| `goals` | Yes | Maven goals and arguments, e.g. `clean install` or `-s /path/settings.xml clean install`. Eclipse variables are supported. |
| `workingDir` | No | Working directory. If blank, uses the **Default working directory** from the preference page. Eclipse variables are supported. |

### Maven Update Project

Runs **Maven > Update Project** on a workspace project (equivalent to right-clicking and choosing Maven > Update Project).

| Field | Required | Description |
|---|---|---|
| `projectName` | Yes | Project name as shown in the Package Explorer. |

Requires M2E.

### Import Maven Project

Imports an existing Maven project from disk into the Eclipse workspace.

| Field | Required | Description |
|---|---|---|
| `pomPath` | Yes | Path to `pom.xml` or its parent directory. Eclipse variables are supported. |

Requires M2E.

### Execute Run Configuration

Executes any existing Eclipse launch configuration by name.

| Field | Required | Description |
|---|---|---|
| `configName` | Yes | Name of the launch configuration. |
| `mode` | No | Launch mode: `run` (default), `debug`, or `profile`. |

### Refresh Project

Refreshes a single project in the Eclipse workspace (same as pressing F5 on the project).

| Field | Required | Description |
|---|---|---|
| `projectName` | Yes | Project name as shown in the Package Explorer. |

### Refresh All

Refreshes all projects in the Eclipse workspace. No configuration fields.

### Write File

Writes text content to a file. Creates parent directories if they do not already exist. Overwrites any existing file at the given path.

| Field | Required | Description |
|---|---|---|
| `filePath` | Yes | Path to the file to write. Eclipse variables are supported. |
| `content` | No | Text content to write. Eclipse variables are supported. Multi-line content is supported via the built-in multi-line editor. |

### Set Maven Settings

Sets the Maven user settings file in Eclipse's M2E configuration. Equivalent to changing the **User Settings** field in **Window > Preferences > Maven**.

| Field | Required | Description |
|---|---|---|
| `filePath` | Yes | Path to the settings.xml file. Eclipse variables are supported. |

Requires M2E (Maven Integration for Eclipse) to be installed.

---

## Eclipse Variable Substitution

Any configuration field of any action can contain Eclipse string substitution variables. Variables are resolved at **execution time** — the stored workflow always contains the raw expression, never the resolved value. This makes workflows portable across machines and workspaces.

**Syntax:** `${variable_name}` or `${variable_name:argument}`

**Commonly useful variables:**

| Variable | Resolves to |
|---|---|
| `${workspace_loc}` | Absolute path of the current Eclipse workspace directory |
| `${workspace_loc:/ProjectName}` | Absolute path of the project named `ProjectName` in the workspace |
| `${project_loc}` | Absolute path of the currently selected project in the workbench |
| `${project_name}` | Name of the currently selected project |
| `${resource_loc}` | Absolute path of the currently selected resource (file or folder) |
| `${resource_name}` | File name of the currently selected resource |
| `${container_loc}` | Absolute path of the parent folder of the selected resource |
| `${env_var:NAME}` | Value of the OS environment variable `NAME` |
| `${system_property:NAME}` | Value of the Java system property `NAME` (e.g., `${system_property:user.home}`) |
| `${string_prompt:message}` | Prompts the user to type a value at run time; shows `message` as the prompt |
| `${build_type}` | Current build type (`incremental`, `full`, `auto`, or `none`) |

**Example — clone a repo next to the workspace:**
```
targetDir = ${workspace_loc}/../my-project
```

**Example — run a command with a project-relative working directory:**
```
command   = mvn clean install
workingDir = ${workspace_loc:/my-project}
```

**Full variable list:** In Eclipse, go to **Run > String Substitution…** to see all variables available on your installation, including any contributed by other plugins.

---

## Preference Page

Open via **Window > Preferences > Automation**.

| Setting | Default | Description |
|---|---|---|
| Default working directory | `${workspace_loc}/..` | The working directory used by Shell Command steps when their `workingDir` field is blank. Also used as the base for resolving relative paths in other actions that support `context.getWorkingDirectory()`. |
| Workflow storage location | `${workspace_loc}/../automation` | The directory where workflow JSON files are stored and loaded from. |

Both fields accept Eclipse variable expressions. Changes take effect immediately on the next workflow load or save — no restart required.

**Deploy bundled workflows button:** Copies the workflows that were shipped with this plugin installation into the configured storage folder. Existing files with the same name are overwritten. Use this button after changing the storage location to repopulate it with the bundled workflows. Hover the button for a full description.

---

## Bundled Workflows

The plugin ships with sample workflows that are automatically copied to your workflow storage folder the first time Eclipse starts after installation.

| Workflow | Description |
|---|---|
| Setup Automation Plugin | Clones the Automation plugin repository next to the workspace, creates a project-local Maven settings file, imports the Maven project into Eclipse, runs `mvn clean install`, updates the Maven project configuration, and refreshes the workspace. |

If automatic deployment fails (e.g., the storage directory is not writable at startup), open **Window > Preferences > Automation** and click **Deploy bundled workflows**.

---

## Sharing Workflows Between Users

Workflows are plain JSON files stored in the workflow storage folder (see **Window > Preferences > Automation** for the exact path on your machine).

**To share a workflow:**

1. Open your workflow storage folder in a file explorer.
2. Copy the `.json` file(s) for the workflow(s) you want to share.
3. Send the file(s) to your colleague (email, shared drive, git repository, etc.).

**To receive a shared workflow:**

1. Place the received `.json` file in your own workflow storage folder.
2. In Eclipse, close and reopen the Automation view (or click **Open Workflow** — it re-reads the folder each time).
3. The shared workflow appears in the picker.

> **Tip:** Workflows that use `${workspace_loc}` or `${project_loc:/ProjectName}` are inherently portable — the variables resolve to each user's own paths at run time, so no path editing is needed after sharing.

---

## Using Workflows from Another Workspace

You can point the Automation view at any folder on disk — including a folder that belongs to a different Eclipse workspace.

1. Go to **Window > Preferences > Automation**.
2. Change **Workflow storage location** to the path of the other workspace's automation folder. You can use a relative expression such as:
   ```
   ${workspace_loc}/../other-workspace/automation
   ```
   or an absolute path such as:
   ```
   /home/alice/other-workspace/automation
   ```
3. Click **Apply and Close**.
4. Reload the Automation view (close and reopen, or click **Open Workflow**).

The view now reads workflows from that folder. Any saves or new workflows you create will also go there.

> **Note:** If you change the storage location and want the bundled sample workflows in the new location, click **Deploy bundled workflows** on the preference page.
