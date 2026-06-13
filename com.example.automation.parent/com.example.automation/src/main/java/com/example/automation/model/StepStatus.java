package com.example.automation.model;

/**
 * Runtime execution status of a {@link Step}, visualised as a coloured square
 * in the Automation view.
 */
public enum StepStatus {
    /** Not yet run or explicitly reset. */
    WHITE,
    /** Completed successfully. */
    GREEN,
    /** Currently executing. */
    YELLOW,
    /** Failed — action threw an exception or action ID was not found. */
    RED
}
