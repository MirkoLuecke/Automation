package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.EclipseVariables;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that executes an arbitrary shell command.
 * Uses {@code powershell.exe -NonInteractive -Command} on Windows and
 * {@code sh -c} on other systems.
 *
 * <p>Config keys: {@code command} (required), {@code workingDir} (optional).
 */
public class ShellCommandAction implements IAction {

    @Override public String getId()          { return "shell-command"; }
    @Override public String getName()        { return "Shell Command"; }
    @Override public String getDescription() {
        return "Executes a shell command. Uses powershell.exe on Windows and sh on Linux/macOS.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("command", "", "workingDir", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("command", "").isBlank())
            errors.add("command must not be blank");
        return errors;
    }

    /**
     * Builds the OS-appropriate command list for the given shell command string.
     * Returns a {@code powershell.exe} invocation on Windows and a {@code sh} invocation elsewhere.
     *
     * @param command the shell command to execute
     * @return command list ready to pass to {@link ProcessBuilder}
     */
    public static List<String> buildCommand(String command) {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
            ? List.of("powershell.exe", "-NonInteractive", "-Command", command)
            : List.of("sh", "-c", command);
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String command    = config.get("command");
        if (command == null || command.isBlank())
            throw new IllegalArgumentException("command must not be blank");
        String workingDir = config.getOrDefault("workingDir", "");

        List<String> cmd = buildCommand(command);

        File dir = workingDir.isBlank()
            ? new File(context.getWorkingDirectory())
            : new File(EclipseVariables.resolve(workingDir));

        ProcessRunner.run(cmd, dir, context);
    }
}
