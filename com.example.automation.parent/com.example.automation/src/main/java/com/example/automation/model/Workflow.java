package com.example.automation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A named, ordered sequence of {@link Step} objects persisted as a single JSON file
 * and executed by {@link com.example.automation.WorkflowRunner}.
 */
public class Workflow {

    private String workflowId;
    private String displayName;
    private String description;
    private List<Step> steps = new ArrayList<>();

    public Workflow() {}

    public Workflow(String workflowId, String displayName, String description) {
        this.workflowId = workflowId;
        this.displayName = displayName;
        this.description = description;
    }

    /** @return the unique workflow identifier used as the JSON filename stem */
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    /** @return the human-readable display name shown in the UI */
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /** @return the optional description shown below the title in the Automation view */
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** @return the mutable ordered list of steps; never null */
    public List<Step> getSteps() { return steps; }
    public void setSteps(List<Step> steps) { this.steps = steps; }
}
