package com.example.automation.actions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class MavenRunWithProgressAction implements IAction {

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
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    context.getStdout().println(line);
                    if (line.contains("BUILD FAILURE")) buildFailed[0] = true;
                    OptionalInt progress = parser.parse(line);
                    if (progress.isPresent()) context.setProgress(progress.getAsInt());
                }
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
}
