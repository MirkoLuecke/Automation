package com.example.automation.actions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class WriteFileAction implements IAction {

    @Override public String getId()          { return "write-file"; }
    @Override public String getName()        { return "Write File"; }
    @Override public String getDescription() { return "Writes text content to a file, creating parent directories as needed. Eclipse variables are supported in both path and content."; }

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
        String filePath = config.getOrDefault("filePath", "");
        String content  = config.getOrDefault("content", "");
        context.setProgress(0);
        writeFile(filePath, content);
        context.getStdout().println("Written: " + Path.of(filePath).toAbsolutePath());
        context.setProgress(100);
    }

    /** Public because accessed from the tests bundle; not part of the public API (see Export-Package x-internal in MANIFEST.MF). */
    public static void writeFile(String filePath, String content) throws Exception {
        Path path = Path.of(filePath);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
