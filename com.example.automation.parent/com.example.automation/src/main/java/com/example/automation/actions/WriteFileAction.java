package com.example.automation.actions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class WriteFileAction implements IAction {

    @Override public String getId()          { return "write-file"; }
    @Override public String getName()        { return "Write File"; }
    @Override public String getDescription() {
        return "Writes text content to a file, creating parent directories as needed. Eclipse variables are supported in both path and content.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("filePath", "", "content", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("filePath", "").isBlank())
            errors.add("filePath must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String filePath = svm.performStringSubstitution(config.getOrDefault("filePath", ""));
        String content  = svm.performStringSubstitution(config.getOrDefault("content", ""));
        writeFile(filePath, content);
        context.setProgress(100);
        context.getStdout().println("Written: " + Path.of(filePath).toAbsolutePath());
    }

    public static void writeFile(String filePath, String content) throws Exception {
        Path path = Path.of(filePath);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
