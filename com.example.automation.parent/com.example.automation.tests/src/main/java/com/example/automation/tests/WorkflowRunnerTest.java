package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.example.automation.WorkflowRunner;
import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;

public class WorkflowRunnerTest {

    @FunctionalInterface
    interface ThrowingConsumer {
        void accept(IActionContext ctx) throws Exception;
    }

    private static IAction stub(String id, ThrowingConsumer body) {
        return new IAction() {
            @Override public String getId() { return id; }
            @Override public String getName() { return id; }
            @Override public String getDescription() { return ""; }
            @Override public Map<String, String> getDefaultConfig() { return Map.of(); }
            @Override public List<String> validate(Map<String, String> config) { return List.of(); }
            @Override public void execute(Map<String, String> config, IActionContext ctx) throws Exception {
                body.accept(ctx);
            }
        };
    }

    private void run(List<Step> steps, ActionRegistry registry) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        new WorkflowRunner(steps, registry, r -> r.run(), () -> {}, done::countDown,
            OutputStream.nullOutputStream(), OutputStream.nullOutputStream()).start();
        assertTrue("Runner did not finish in 5 s", done.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void successfulStep_statusGreen() throws Exception {
        Step step = new Step("a");
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> {}))));
        assertEquals(StepStatus.GREEN, step.getStatus());
    }

    @Test
    public void unknownActionId_statusRed() throws Exception {
        Step step1 = new Step("missing");
        Step step2 = new Step("ok");
        run(List.of(step1, step2), new ActionRegistry(List.of()));
        assertEquals(StepStatus.RED,   step1.getStatus());
        assertEquals(StepStatus.WHITE, step2.getStatus());
    }

    @Test
    public void throwingAction_statusRed_nextStepSkipped() throws Exception {
        Step step1 = new Step("fail");
        Step step2 = new Step("ok");
        ActionRegistry reg = new ActionRegistry(List.of(
            stub("fail", ctx -> { throw new RuntimeException("boom"); }),
            stub("ok",   ctx -> {})
        ));
        run(List.of(step1, step2), reg);
        assertEquals(StepStatus.RED,   step1.getStatus());
        assertEquals(StepStatus.WHITE, step2.getStatus());
    }

    @Test
    public void cancelBeforeStart_noStepsRun() throws Exception {
        Step step = new Step("a");
        CountDownLatch done = new CountDownLatch(1);
        WorkflowRunner runner = new WorkflowRunner(
            List.of(step),
            new ActionRegistry(List.of(stub("a", ctx -> {}))),
            r -> r.run(), () -> {}, done::countDown,
            OutputStream.nullOutputStream(), OutputStream.nullOutputStream());
        runner.cancel();
        runner.start();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(StepStatus.WHITE, step.getStatus());
    }

    @Test
    public void progressUpdatesStep() throws Exception {
        Step step = new Step("a");
        run(List.of(step), new ActionRegistry(List.of(stub("a", ctx -> ctx.setProgress(50)))));
        assertEquals(50, step.getProgress());
        assertEquals(StepStatus.GREEN, step.getStatus());
    }
}
