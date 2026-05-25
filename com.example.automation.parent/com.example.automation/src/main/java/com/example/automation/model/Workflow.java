package com.example.automation.model;

import java.util.ArrayList;
import java.util.List;

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

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<Step> getSteps() { return steps; }
    public void setSteps(List<Step> steps) { this.steps = steps; }
}
