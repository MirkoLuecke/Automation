package com.example.automation.actions;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class MavenRunWithProgressAction implements IAction {

    @Override public String getId()          { return "maven-run-with-progress"; }
    @Override public String getName()        { return "Maven Run with Progress"; }
    @Override public String getDescription() { return "Executes a Maven launch configuration and parses progress from console output."; }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("configName", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("configName", "").isBlank())
            errors.add("configName must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        new Execution(config.get("configName"), context).run();
    }

    private static class Execution {
        private final String configName;
        private final IActionContext context;
        private final MavenProgressParser parser = new MavenProgressParser();
        private final StringBuilder lineBuffer   = new StringBuilder();
        private volatile boolean buildFailed     = false;

        Execution(String configName, IActionContext context) {
            this.configName = configName;
            this.context    = context;
        }

        void run() throws Exception {
            if (configName == null || configName.isBlank())
                throw new IllegalArgumentException("configName must not be blank");

            ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
            ILaunchConfiguration launchConfig = null;
            for (ILaunchConfiguration c : mgr.getLaunchConfigurations()) {
                if (configName.equals(c.getName())) { launchConfig = c; break; }
            }
            if (launchConfig == null)
                throw new Exception("Launch configuration not found: " + configName);

            context.setProgress(0);
            ILaunch launch = launchConfig.launch(ILaunchManager.RUN_MODE, null, false, true);

            for (IProcess process : launch.getProcesses()) {
                OutputStream err = context.getErrorStream();
                process.getStreamsProxy().getOutputStreamMonitor()
                    .addListener((text, mon) -> onOutput(text));
                process.getStreamsProxy().getErrorStreamMonitor()
                    .addListener((text, mon) -> writeQuietly(err, text));
                onOutput(process.getStreamsProxy().getOutputStreamMonitor().getContents());
            }

            while (!launch.isTerminated()) {
                if (context.isCancelled()) { launch.terminate(); return; }
                Thread.sleep(100);
            }

            flushBuffer();

            if (buildFailed) throw new Exception("Maven build failed.");
            for (IProcess process : launch.getProcesses()) {
                if (process.getExitValue() != 0)
                    throw new Exception("Maven run failed with exit code " + process.getExitValue());
            }
            context.setProgress(100);
        }

        private void onOutput(String text) {
            writeQuietly(context.getOutputStream(), text);
            synchronized (lineBuffer) {
                lineBuffer.append(text);
                int idx;
                while ((idx = lineBuffer.indexOf("\n")) >= 0) {
                    String line = lineBuffer.substring(0, idx).stripTrailing();
                    lineBuffer.delete(0, idx + 1);
                    processLine(line);
                }
            }
        }

        private void processLine(String line) {
            if (line.contains("BUILD FAILURE")) buildFailed = true;
            OptionalInt progress = parser.parse(line);
            if (progress.isPresent()) context.setProgress(progress.getAsInt());
        }

        private void flushBuffer() {
            String remaining;
            synchronized (lineBuffer) {
                remaining = lineBuffer.toString().stripTrailing();
            }
            if (!remaining.isEmpty()) processLine(remaining);
        }

        private static void writeQuietly(OutputStream out, String text) {
            if (text == null || text.isEmpty()) return;
            try { out.write(text.getBytes()); } catch (IOException ignored) {}
        }
    }
}
