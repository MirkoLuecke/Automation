package com.example.automation.actions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class ExecuteRunConfigAction implements IAction {

    @Override public String getId()          { return "execute-run-config"; }
    @Override public String getName()        { return "Execute Run Configuration"; }
    @Override public String getDescription() { return "Executes an existing Eclipse launch configuration by name."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("configName", "", "mode", "run");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("configName", "").isBlank())
            errors.add("configName must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String configName = config.get("configName");
        if (configName == null || configName.isBlank())
            throw new IllegalArgumentException("configName must not be blank");

        String mode = config.getOrDefault("mode", "run");
        if (mode.isBlank()) mode = "run";

        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfiguration launchConfig = null;
        for (ILaunchConfiguration c : mgr.getLaunchConfigurations()) {
            if (configName.equals(c.getName())) { launchConfig = c; break; }
        }
        if (launchConfig == null)
            throw new Exception("Launch configuration not found: " + configName);

        context.setProgress(0);
        ILaunch launch = launchConfig.launch(mode, null, false, true);

        for (IProcess process : launch.getProcesses()) {
            OutputStream out = context.getOutputStream();
            OutputStream err = context.getErrorStream();
            process.getStreamsProxy().getOutputStreamMonitor()
                .addListener((text, mon) -> writeQuietly(out, text));
            process.getStreamsProxy().getErrorStreamMonitor()
                .addListener((text, mon) -> writeQuietly(err, text));
            writeQuietly(out,
                process.getStreamsProxy().getOutputStreamMonitor().getContents());
        }

        while (!launch.isTerminated()) {
            if (context.isCancelled()) { launch.terminate(); return; }
            Thread.sleep(100);
        }

        for (IProcess process : launch.getProcesses()) {
            if (process.getExitValue() != 0)
                throw new Exception("Launch failed with exit code " + process.getExitValue());
        }
        context.setProgress(100);
    }

    private static void writeQuietly(OutputStream out, String text) {
        if (text == null || text.isEmpty()) return;
        try { out.write(text.getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) {}
    }
}
