package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;

import com.example.automation.WorkflowJob;
import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;

public class WorkflowJobTest {

    @FunctionalInterface
    interface ThrowingConsumer {
        void accept(IActionContext ctx) throws Exception;
    }

    private static IAction stub(String id, ThrowingConsumer body) {
        return new IAction() {
            @Override public String getId()          { return id; }
            @Override public String getName()        { return id; }
            @Override public String getDescription() { return ""; }
            @Override public Map<String, String> getDefaultConfig() { return Map.of(); }
            @Override public List<String> validate(Map<String, String> c) { return List.of(); }
            @Override public void execute(Map<String, String> config, IActionContext ctx) throws Exception {
                body.accept(ctx);
            }
        };
    }

    private void run(List<Step> steps, ActionRegistry registry) throws Exception {
        WorkflowJob job = new WorkflowJob("test", steps, registry,
            Runnable::run, () -> {}, () -> {}, OutputStream.nullOutputStream(), OutputStream.nullOutputStream());
        job.schedule();
        job.join();
    }

    @Test
    public void successfulStep_statusGreen() throws Exception {
        Step step = new Step("a");
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {}))));
        assertEquals(StepStatus.GREEN, step.getStatus());
    }

    @Test
    public void unknownActionId_statusRed_nextSkipped() throws Exception {
        Step step1 = new Step("missing");
        Step step2 = new Step("ok");
        run(List.of(step1, step2), new ActionRegistry(List.of()));
        assertEquals(StepStatus.RED,   step1.getStatus());
        assertEquals(StepStatus.WHITE, step2.getStatus());
    }

    @Test
    public void throwingAction_statusRed_nextSkipped() throws Exception {
        Step step1 = new Step("fail");
        Step step2 = new Step("ok");
        run(List.of(step1, step2), new ActionRegistry(List.of(
            stub("fail", ctx -> { throw new RuntimeException("boom"); }),
            stub("ok",   ctx -> {})
        )));
        assertEquals(StepStatus.RED,   step1.getStatus());
        assertEquals(StepStatus.WHITE, step2.getStatus());
    }

    @Test
    public void progressUpdatesStep() throws Exception {
        Step step = new Step("a");
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> ctx.setProgress(50)))));
        assertEquals(50, step.getProgress());
        assertEquals(StepStatus.GREEN, step.getStatus());
    }

    @Test
    public void multipleSteps_allGreen() throws Exception {
        Step step1 = new Step("a");
        Step step2 = new Step("b");
        run(List.of(step1, step2), new ActionRegistry(List.of(
            stub("a", ctx -> {}),
            stub("b", ctx -> {})
        )));
        assertEquals(StepStatus.GREEN, step1.getStatus());
        assertEquals(StepStatus.GREEN, step2.getStatus());
    }

    @Test
    public void configPassedToAction() throws Exception {
        AtomicReference<Map<String, String>> received = new AtomicReference<>();
        Step step = new Step("a");
        step.getConfig().put("myKey", "myValue");
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {
            received.set(new HashMap<>(ctx.getConfig()));
        }))));
        assertEquals("myValue", received.get().get("myKey"));
    }

    @Test
    public void retryOnError_secondAttemptSucceeds_statusGreen() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Step step = new Step("a");
        step.setRetryOnError(true);
        step.setRetryWaitSeconds(0); // no actual sleep in tests
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {
            if (calls.incrementAndGet() == 1) throw new RuntimeException("first attempt fails");
        }))));
        assertEquals(2, calls.get());
        assertEquals(StepStatus.GREEN, step.getStatus());
    }

    @Test
    public void retryOnError_bothAttemptsThrow_statusRed() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Step step = new Step("a");
        step.setRetryOnError(true);
        step.setRetryWaitSeconds(0);
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {
            calls.incrementAndGet();
            throw new RuntimeException("always fails");
        }))));
        assertEquals(2, calls.get());
        assertEquals(StepStatus.RED, step.getStatus());
    }

    @Test
    public void noRetry_singleAttempt_statusRed() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Step step = new Step("a");
        // retryOnError defaults to false
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {
            calls.incrementAndGet();
            throw new RuntimeException("fail");
        }))));
        assertEquals(1, calls.get());
        assertEquals(StepStatus.RED, step.getStatus());
    }
}
