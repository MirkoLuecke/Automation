package com.example.automation;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.preferences.AutomationPreferences;

public class WorkflowJob extends Job {

    private final String workflowName;
    private final List<Step> steps;
    private final ActionRegistry registry;
    private final Consumer<Runnable> uiExec;
    private final Runnable onRefresh;
    private final Runnable onDone;
    private final PrintStream stdout;
    private final PrintStream stderr;

    public WorkflowJob(String workflowName,
                       List<Step> steps,
                       ActionRegistry registry,
                       Consumer<Runnable> uiExec,
                       Runnable onRefresh,
                       Runnable onDone,
                       OutputStream stdout,
                       OutputStream stderr) {
        super("Running workflow: " + workflowName);
        this.workflowName = workflowName;
        this.steps        = steps;
        this.registry     = registry;
        this.uiExec       = uiExec;
        this.onRefresh    = onRefresh;
        this.onDone       = onDone;
        this.stdout       = stdout instanceof PrintStream ps ? ps : new PrintStream(stdout);
        this.stderr       = stderr instanceof PrintStream ps ? ps : new PrintStream(stderr);
        setRule(null);
        addJobChangeListener(new JobChangeAdapter() {
            @Override public void done(IJobChangeEvent event) { uiExec.accept(onDone); }
        });
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String workingDir = resolveWorkingDir(svm);

        monitor.beginTask(workflowName, steps.size());
        for (Step step : steps) {
            if (monitor.isCanceled()) break;
            monitor.subTask(step.getName() != null ? step.getName() : step.getActionId());
            setStatus(step, StepStatus.YELLOW);
            Map<String, String> resolvedConfig = resolveConfig(step.getConfig(), svm);
            IAction action = registry.getAction(step.getActionId());
            if (action == null) {
                stderr.println("Unknown action: " + step.getActionId());
                setStatus(step, StepStatus.RED);
                break;
            }
            try {
                executeWithRetry(step, action, resolvedConfig, workingDir, monitor);
                if (!monitor.isCanceled()) setStatus(step, StepStatus.GREEN);
            } catch (Exception e) {
                Platform.getLog(WorkflowJob.class).error("Step failed", e);
                stderr.println("Step failed: " + e);
                e.printStackTrace(stderr);
                setStatus(step, StepStatus.RED);
                break;
            }
            refreshWorkspace(monitor);
            monitor.worked(1);
        }
        return Status.OK_STATUS;
    }

    private void executeWithRetry(Step step, IAction action,
                                   Map<String, String> resolvedConfig,
                                   String workingDir,
                                   IProgressMonitor monitor) throws Exception {
        IActionContext ctx = new ActionContextImpl(resolvedConfig, workingDir, step, stdout, stderr, uiExec, onRefresh, monitor);
        try {
            action.execute(resolvedConfig, ctx);
        } catch (Exception e) {
            if (step.isRetryOnError()) {
                monitor.subTask("Retrying " + (step.getName() != null ? step.getName() : step.getActionId())
                    + " in " + step.getRetryWaitSeconds() + "s…");
                long deadline = System.currentTimeMillis() + step.getRetryWaitSeconds() * 1000L;
                while (System.currentTimeMillis() < deadline && !monitor.isCanceled()) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                action.execute(resolvedConfig, ctx);
            } else {
                throw e;
            }
        }
    }

    private void refreshWorkspace(IProgressMonitor monitor) {
        // Disable auto-build before refreshLocal() so that detecting filesystem changes
        // (e.g. files written by an external mvn process) does not trigger concurrent
        // Maven Project Builder jobs for every module — those race on Plexus classloading
        // and produce TextFileChange NoClassDefFoundError dialogs (one per module).
        IJobManager jm = Job.getJobManager();
        IWorkspaceDescription wsDesc = ResourcesPlugin.getWorkspace().getDescription();
        boolean wasAutoBuilding = wsDesc.isAutoBuilding();
        if (wasAutoBuilding) {
            try {
                wsDesc.setAutoBuilding(false);
                ResourcesPlugin.getWorkspace().setDescription(wsDesc);
            } catch (CoreException e) {
                Platform.getLog(WorkflowJob.class).warn("Could not disable auto-build before workspace refresh", e);
            }
        }
        try {
            jm.join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            ResourcesPlugin.getWorkspace().getRoot()
                .refreshLocal(IResource.DEPTH_INFINITE, monitor);
        } catch (CoreException e) {
            Platform.getLog(WorkflowJob.class).warn("Workspace refresh failed", e);
        } finally {
            if (wasAutoBuilding) {
                try {
                    IWorkspaceDescription freshDesc = ResourcesPlugin.getWorkspace().getDescription();
                    freshDesc.setAutoBuilding(true);
                    ResourcesPlugin.getWorkspace().setDescription(freshDesc);
                } catch (CoreException e) {
                    Platform.getLog(WorkflowJob.class).warn("Could not re-enable auto-build after workspace refresh", e);
                }
            }
        }
        try {
            jm.join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String resolveWorkingDir(IStringVariableManager svm) {
        String raw = AutomationPreferences.getDefaultWorkingDir();
        try {
            return svm.performStringSubstitution(raw);
        } catch (CoreException e) {
            Platform.getLog(WorkflowJob.class)
                .warn("Could not resolve working directory preference: " + raw, e);
            return System.getProperty("user.home");
        }
    }

    private Map<String, String> resolveConfig(Map<String, String> config, IStringVariableManager svm) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String value = entry.getValue();
            try {
                value = svm.performStringSubstitution(value);
            } catch (CoreException e) {
                Platform.getLog(WorkflowJob.class)
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
        private final Step step;
        private final PrintStream stdout;
        private final PrintStream stderr;
        private final Consumer<Runnable> uiExec;
        private final Runnable onRefresh;
        private final IProgressMonitor monitor;

        ActionContextImpl(Map<String, String> config, String workingDirectory,
                          Step step, PrintStream stdout, PrintStream stderr,
                          Consumer<Runnable> uiExec, Runnable onRefresh,
                          IProgressMonitor monitor) {
            this.config           = config;
            this.workingDirectory = workingDirectory;
            this.step             = step;
            this.stdout           = stdout;
            this.stderr           = stderr;
            this.uiExec           = uiExec;
            this.onRefresh        = onRefresh;
            this.monitor          = monitor;
        }

        @Override public Map<String, String> getConfig()        { return config; }
        @Override public String getWorkingDirectory()           { return workingDirectory; }
        @Override public Step getStep()                         { return step; }
        @Override public PrintStream getStdout()                { return stdout; }
        @Override public PrintStream getStderr()                { return stderr; }
        @Override public OutputStream getOutputStream()         { return stdout; }
        @Override public OutputStream getErrorStream()          { return stderr; }
        @Override public Consumer<Runnable> getUiExecutor()     { return uiExec; }
        @Override public boolean isCancelled()                  { return monitor.isCanceled(); }
        @Override public void setProgress(int percent)          { step.setProgress(percent); uiExec.accept(onRefresh); }
    }
}
