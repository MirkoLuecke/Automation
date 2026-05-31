# Shell Command — PowerShell on Windows

## Goal

Replace `cmd.exe` with `powershell.exe` as the Windows shell for the existing Shell Command action, and update the README to reflect the change.

## Architecture

One-line change in `ShellCommandAction.execute()`. No new classes, no new config keys, no OSGi dependencies. The action ID, name, and config schema are unchanged.

## Tech Stack

Java 17, `ProcessBuilder` (existing `ProcessRunner` helper), Windows PowerShell 5.1 (`powershell.exe`), POSIX `sh`.

---

## 1. `ShellCommandAction`

### `execute()` — OS dispatch

```java
List<String> cmd = System.getProperty("os.name").toLowerCase().contains("win")
    ? List.of("powershell.exe", "-NonInteractive", "-Command", command)
    : List.of("sh", "-c", command);
```

`-NonInteractive` suppresses the progress bar and interactive prompts that PowerShell sometimes shows inside non-terminal processes.

### `getDescription()`

```java
"Executes a shell command. Uses powershell.exe on Windows and sh on Linux/macOS."
```

---

## 2. README

Line currently reads:

> On Windows: `cmd.exe /c <command>`. On Linux/macOS: `sh -c <command>`.

Replace with:

> On Windows: `powershell.exe -NonInteractive -Command <command>`. On Linux/macOS: `sh -c <command>`.

---

## 3. Tests

### Existing tests — no change needed

`validate_rejectsBlankCommand`, `validate_acceptsNonBlankCommand`, and `defaultConfig_containsCommandAndWorkingDirKeys` remain valid without modification.

### New test — description mentions powershell

```java
@Test
public void description_mentionsPowershell() {
    String desc = new ShellCommandAction().getDescription().toLowerCase();
    assertTrue(desc.contains("powershell"));
}
```

This is lightweight and ensures the description stays accurate if someone edits it.

---

## Out of scope

- `pwsh` (PowerShell 7) — not guaranteed to be installed.
- A separate cmd.exe action — not needed unless a user requests it.
- Execution-policy flags — `-NonInteractive -Command` works regardless of execution policy for inline commands.
