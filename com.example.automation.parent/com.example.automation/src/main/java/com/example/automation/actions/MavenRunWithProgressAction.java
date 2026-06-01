package com.example.automation.actions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class MavenRunWithProgressAction implements IAction {

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\[[0-9;]*[mK]");

    @Override public String getId()          { return "maven-run-with-progress"; }
    @Override public String getName()        { return "Maven Run with Progress"; }
    @Override public String getDescription() {
        return "Runs a Maven build from the command line and tracks progress from output. Uses powershell.exe on Windows and sh on Linux/macOS.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("goals", "", "workingDir", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("goals", "").isBlank())
            errors.add("goals must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String goals = config.getOrDefault("goals", "");
        if (goals.isBlank()) throw new IllegalArgumentException("goals must not be blank");
        String workingDir = config.getOrDefault("workingDir", "");
        File dir = workingDir.isBlank()
            ? new File(context.getWorkingDirectory())
            : new File(workingDir);

        List<String> cmd = ShellCommandAction.buildCommand("mvn " + goals);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        context.setProgress(0);
        Process process = pb.start();

        MavenProgressParser parser = new MavenProgressParser();
        boolean[] buildFailed = {false};

        Thread stdoutThread = new Thread(() -> {
            try {
                processOutputStream(process.getInputStream(), context, parser, buildFailed);
            } catch (IOException ignored) {}
        });

        Thread stderrThread = new Thread(() -> {
            try { process.getErrorStream().transferTo(context.getErrorStream()); }
            catch (IOException ignored) {}
        });

        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);
        stdoutThread.start();
        stderrThread.start();

        while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
            if (context.isCancelled()) {
                process.destroyForcibly();
                stdoutThread.join();
                stderrThread.join();
                return;
            }
        }
        stdoutThread.join();
        stderrThread.join();

        if (buildFailed[0]) throw new Exception("Maven build failed.");
        int exit = process.exitValue();
        if (exit != 0) throw new Exception("mvn exited with code " + exit);
        context.setProgress(100);
    }

    public void processOutputStream(InputStream in, IActionContext context,
                                    MavenProgressParser parser, boolean[] buildFailed)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int maxProgress = 0;
            while ((line = reader.readLine()) != null) {
                String clean = ANSI_ESCAPE.matcher(line).replaceAll("");
                context.getStdout().println(clean);
                if (clean.contains("BUILD FAILURE")) buildFailed[0] = true;
                OptionalInt progress = parser.parse(clean);
                if (progress.isPresent() && progress.getAsInt() > maxProgress) {
                    maxProgress = progress.getAsInt();
                    context.setProgress(maxProgress);
                }
            }
        }
    }
}
