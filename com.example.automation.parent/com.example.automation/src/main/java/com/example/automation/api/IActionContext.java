package com.example.automation.api;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import com.example.automation.model.Step;

/**
 * Execution context passed to an {@link IAction} during workflow execution.
 * Provides I/O streams, the resolved working directory, progress reporting, and
 * cancellation support. Default implementations return safe no-op or standard-stream
 * values so actions can be tested without a full Eclipse runtime.
 */
public interface IActionContext {

    /**
     * Returns the resolved configuration for the current step.
     *
     * @return key-value config map; never null
     */
    default Map<String, String> getConfig() { return Collections.emptyMap(); }

    /**
     * Returns the resolved working directory for the current step.
     *
     * @return absolute path string; defaults to {@code user.home}
     */
    default String getWorkingDirectory() { return System.getProperty("user.home"); }

    /**
     * Returns the current step model object.
     *
     * @return the step being executed, or {@code null} in test contexts
     */
    default Step getStep() { return null; }

    /**
     * Returns stdout as a PrintStream.
     *
     * @return a non-null PrintStream backed by {@link #getOutputStream()}
     */
    default PrintStream getStdout() {
        OutputStream out = getOutputStream();
        return out instanceof PrintStream ? (PrintStream) out : new PrintStream(out);
    }

    /**
     * Returns stderr as a PrintStream.
     *
     * @return a non-null PrintStream backed by {@link #getErrorStream()}
     */
    default PrintStream getStderr() {
        OutputStream err = getErrorStream();
        return err instanceof PrintStream ? (PrintStream) err : new PrintStream(err);
    }

    /**
     * Returns the executor for scheduling work on the UI thread.
     *
     * @return a consumer that runs runnables on the display thread; defaults to synchronous execution
     */
    default Consumer<Runnable> getUiExecutor() { return Runnable::run; }

    /**
     * Returns stdout as an OutputStream.
     *
     * @return the raw output stream; never null
     */
    OutputStream getOutputStream();

    /**
     * Returns stderr as an OutputStream.
     *
     * @return the raw error stream; never null
     */
    OutputStream getErrorStream();

    /**
     * Updates the progress of the currently executing step.
     *
     * @param percent completion percentage, 0 to 100 inclusive
     */
    void setProgress(int percent);

    /**
     * Returns {@code true} if the workflow runner has been asked to cancel execution.
     *
     * @return true if cancellation was requested
     */
    boolean isCancelled();
}
