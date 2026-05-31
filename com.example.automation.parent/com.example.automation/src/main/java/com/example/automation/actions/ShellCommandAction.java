package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

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

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String command    = config.get("command");
        if (command == null || command.isBlank())
            throw new IllegalArgumentException("command must not be blank");
        String workingDir = config.getOrDefault("workingDir", "");

        List<String> cmd = System.getProperty("os.name").toLowerCase().contains("win")
            ? List.of("powershell.exe", "-NonInteractive", "-Command", command)
            : List.of("sh", "-c", command);

        File dir = workingDir.isBlank()
            ? new File(context.getWorkingDirectory())
            : new File(workingDir);

        ProcessRunner.run(cmd, dir, context);
    }
}
