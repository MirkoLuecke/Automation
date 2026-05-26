package com.example.automation;

import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;

public class WorkflowRunner {

    private final List<Step> steps;
    private final ActionRegistry registry;
    private final Consumer<Runnable> uiRunner;
    private final Runnable refresh;
    private final Runnable onDone;

    private volatile boolean cancelled = false;

    public WorkflowRunner(List<Step> steps, ActionRegistry registry,
                          Consumer<Runnable> uiRunner, Runnable refresh, Runnable onDone) {
        this.steps    = steps;
        this.registry = registry;
        this.uiRunner = uiRunner;
        this.refresh  = refresh;
        this.onDone   = onDone;
    }

    public Thread start() {
        Thread t = new Thread(this::execute, "WorkflowRunner");
        t.setDaemon(true);
        t.start();
        return t;
    }

    public void cancel() {
        cancelled = true;
    }

    private void execute() {
        for (Step step : steps) {
            if (cancelled) break;

            step.setStatus(StepStatus.YELLOW);
            step.setProgress(0);
            uiRunner.accept(refresh);

            IAction action = registry.getAction(step.getActionId());
            if (action == null) {
                step.setStatus(StepStatus.RED);
                uiRunner.accept(refresh);
                break;
            }

            try {
                action.execute(step.getConfig(), new ActionContextImpl(step));
                if (!cancelled) {
                    step.setStatus(StepStatus.GREEN);
                    uiRunner.accept(refresh);
                }
            } catch (Exception e) {
                step.setStatus(StepStatus.RED);
                uiRunner.accept(refresh);
                break;
            }
        }
        uiRunner.accept(onDone);
    }

    private class ActionContextImpl implements IActionContext {

        private final Step step;

        ActionContextImpl(Step step) { this.step = step; }

        @Override
        public void setProgress(int percent) {
            step.setProgress(percent);
            uiRunner.accept(refresh);
        }

        @Override
        public boolean isCancelled() { return cancelled; }

        @Override
        public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }

        @Override
        public OutputStream getErrorStream() { return OutputStream.nullOutputStream(); }
    }
}
