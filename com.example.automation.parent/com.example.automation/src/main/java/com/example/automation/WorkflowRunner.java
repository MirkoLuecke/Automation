package com.example.automation;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.preferences.AutomationPreferences;

/**
 * Executes a list of {@link com.example.automation.model.Step} objects sequentially
 * in a background daemon thread. Updates step status, streams output to the provided
 * streams, resolves Eclipse string variables in config values, and notifies the UI
 * on each status change and on completion.
 */
public class WorkflowRunner {

    private final List<Step>     steps;
    private final ActionRegistry registry;
    private final Consumer<Runnable> uiExec;
    private final Runnable       onRefresh;
    private final Runnable       onDone;
    private final PrintStream    stdout;
    private final PrintStream    stderr;

    private volatile boolean cancelled;
    private Thread thread;

    public WorkflowRunner(List<Step> steps,
                          ActionRegistry registry,
                          Consumer<Runnable> uiExec,
                          Runnable onRefresh,
                          Runnable onDone,
                          OutputStream stdout,
                          OutputStream stderr) {
        this.steps     = steps;
        this.registry  = registry;
        this.uiExec    = uiExec;
        this.onRefresh = onRefresh;
        this.onDone    = onDone;
        this.stdout    = stdout instanceof PrintStream ? (PrintStream) stdout : new PrintStream(stdout);
        this.stderr    = stderr instanceof PrintStream ? (PrintStream) stderr : new PrintStream(stderr);
    }

    /**
     * Starts executing steps in a background daemon thread. Returns immediately;
     * the {@code onDone} callback is invoked on the UI thread when execution finishes.
     */
    public void start() {
        thread = new Thread(this::execute, "WorkflowRunner");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Requests cancellation of the running workflow. The background thread is
     * interrupted and the current action's {@link com.example.automation.api.IActionContext#isCancelled()}
     * will return {@code true}.
     */
    public void cancel() {
        cancelled = true;
        if (thread != null) thread.interrupt();
    }

    private void execute() {
        IStringVariableManager svm =
                VariablesPlugin.getDefault().getStringVariableManager();
        String workingDir = resolveWorkingDir(svm);

        for (Step step : steps) {
            if (cancelled) break;
            setStatus(step, StepStatus.YELLOW);
            Map<String, String> resolvedConfig = resolveConfig(step.getConfig(), svm);
            IAction action = registry.getAction(step.getActionId());
            if (action == null) {
                stderr.println("Unknown action: " + step.getActionId());
                setStatus(step, StepStatus.RED);
                break;
            }
            try {
                action.execute(resolvedConfig, new ActionContextImpl(resolvedConfig, workingDir,
                        step, stdout, stderr, uiExec, onRefresh, () -> cancelled));
                if (!cancelled) setStatus(step, StepStatus.GREEN);
            } catch (Exception e) {
                Platform.getLog(WorkflowRunner.class).error("Step failed", e);
                stderr.println("Step failed: " + e);
                e.printStackTrace(stderr);
                setStatus(step, StepStatus.RED);
                break;
            }
        }
        uiExec.accept(onDone);
    }

    private String resolveWorkingDir(IStringVariableManager svm) {
        String raw = AutomationPreferences.getDefaultWorkingDir();
        try {
            return svm.performStringSubstitution(raw);
        } catch (CoreException e) {
            Platform.getLog(WorkflowRunner.class)
                    .warn("Could not resolve working directory preference: " + raw, e);
            return System.getProperty("user.home");
        }
    }

    private Map<String, String> resolveConfig(Map<String, String> config,
                                               IStringVariableManager svm) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String value = entry.getValue();
            try {
                value = svm.performStringSubstitution(value);
            } catch (CoreException e) {
                Platform.getLog(WorkflowRunner.class)
                        .warn("Variable substitution failed for value: " + entry.getValue());
            }
            result.put(entry.getKey(), value);
        }
        return result;
    }

    private void setStatus(Step step, StepStatus status) {
        step.setStatus(status);
        uiExec.accept(onRefresh);
    }

    private static class ActionContextImpl implements IActionContext {
        private final Map<String, String> config;
        private final String workingDirectory;
        private final Step   step;
        private final PrintStream stdout;
        private final PrintStream stderr;
        private final Consumer<Runnable> uiExec;
        private final Runnable onRefresh;
        private final java.util.function.BooleanSupplier cancelledSupplier;

        ActionContextImpl(Map<String, String> config, String workingDirectory,
                          Step step, PrintStream stdout, PrintStream stderr,
                          Consumer<Runnable> uiExec, Runnable onRefresh,
                          java.util.function.BooleanSupplier cancelledSupplier) {
            this.config             = config;
            this.workingDirectory   = workingDirectory;
            this.step               = step;
            this.stdout             = stdout;
            this.stderr             = stderr;
            this.uiExec             = uiExec;
            this.onRefresh          = onRefresh;
            this.cancelledSupplier  = cancelledSupplier;
        }

        @Override public Map<String, String> getConfig()    { return config; }
        @Override public String getWorkingDirectory()      { return workingDirectory; }
        @Override public Step getStep()                    { return step; }
        @Override public PrintStream getStdout()           { return stdout; }
        @Override public PrintStream getStderr()           { return stderr; }
        @Override public OutputStream getOutputStream()    { return stdout; }
        @Override public OutputStream getErrorStream()     { return stderr; }
        @Override public Consumer<Runnable> getUiExecutor() { return uiExec; }
        @Override public boolean isCancelled()             { return cancelledSupplier.getAsBoolean(); }
        @Override public void setProgress(int percent)     { step.setProgress(percent); uiExec.accept(onRefresh); }
    }
}
