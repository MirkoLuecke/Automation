package com.example.automation.actions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.EclipseVariables;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that writes text content to a file,
 * creating any missing parent directories. Eclipse string variables in config values
 * are resolved by the workflow runner before this action is invoked.
 *
 * <p>Config keys: {@code filePath} (required), {@code content} (optional, defaults to empty string).
 */
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
        filePath = EclipseVariables.resolve(filePath);
        writeFile(filePath, content);
        context.getStdout().println("Written: " + Path.of(filePath).toAbsolutePath());
        context.setProgress(100);
    }

    /**
     * Writes {@code content} to {@code filePath}, creating parent directories as needed.
     * Public because it is called from the tests bundle; not part of the exported API
     * (see {@code x-internal} in {@code MANIFEST.MF}).
     *
     * @param filePath the target file path; must not be blank
     * @param content  the UTF-8 text to write; may be empty
     * @throws Exception if the file cannot be created or written
     */
    public static void writeFile(String filePath, String content) throws Exception {
        Path path = Path.of(filePath);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
