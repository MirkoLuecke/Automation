package com.example.automation.api;

import java.util.List;
import java.util.Map;

/**
 * Contract for a single automation action contributed via the
 * {@code com.example.automation.actions} extension point.
 * Each action declares a unique ID, a human-readable name, its default
 * configuration, and execution logic.
 */
public interface IAction {
    /** @return the unique identifier for this action (e.g., {@code "shell-command"}); must be unique across all registered actions */
    String getId();

    /** @return the human-readable name shown in the UI action list */
    String getName();

    /** @return a one-line description of what this action does, or {@code null} */
    String getDescription();

    /**
     * Returns the default configuration with all keys this action recognises.
     * Used to pre-populate a step's config when the user adds it to a workflow.
     *
     * @return key-value map of config key to default value; never null
     */
    Map<String, String> getDefaultConfig();

    /**
     * Validates the given configuration before execution.
     *
     * @param config the configuration to validate
     * @return list of human-readable error messages; empty if the configuration is valid
     */
    List<String> validate(Map<String, String> config);

    /**
     * Executes this action with the given configuration and context.
     *
     * @param config  resolved key-value pairs from the workflow step
     * @param context provides I/O streams, working directory, progress, and cancellation
     * @throws Exception if the action fails; the message is displayed to the user
     */
    void execute(Map<String, String> config, IActionContext context) throws Exception;
}
