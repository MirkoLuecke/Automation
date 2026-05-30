package com.example.automation.api;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import com.example.automation.model.Step;

public interface IActionContext {

    /** Returns the resolved configuration for the current step. */
    default Map<String, String> getConfig() { return Collections.emptyMap(); }

    /** Returns the resolved working directory for the current step. */
    default String getWorkingDirectory() { return System.getProperty("user.home"); }

    /** Returns the current step model object. */
    default Step getStep() { return null; }

    /** Returns the stdout stream as a PrintStream. */
    default PrintStream getStdout() {
        OutputStream out = getOutputStream();
        return out instanceof PrintStream ? (PrintStream) out : new PrintStream(out);
    }

    /** Returns the stderr stream as a PrintStream. */
    default PrintStream getStderr() {
        OutputStream err = getErrorStream();
        return err instanceof PrintStream ? (PrintStream) err : new PrintStream(err);
    }

    /** Returns the UI executor for running tasks on the UI thread. */
    default Consumer<Runnable> getUiExecutor() { return Runnable::run; }

    /** Returns stdout as an OutputStream. */
    OutputStream getOutputStream();

    /** Returns stderr as an OutputStream. */
    OutputStream getErrorStream();

    /**
     * Updates the progress of the currently executing step (0–100).
     */
    void setProgress(int percent);

    /**
     * Returns {@code true} if the runner has been asked to cancel execution.
     */
    boolean isCancelled();
}
