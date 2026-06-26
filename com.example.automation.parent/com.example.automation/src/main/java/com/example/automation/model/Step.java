package com.example.automation.model;

import java.util.HashMap;
import java.util.Map;

/**
 * A single step in a {@link Workflow}, binding an action ID to its configuration
 * and transient runtime state (status and progress percentage). Status and progress
 * fields are {@code transient} and are not persisted to JSON.
 */
public class Step {

    private String actionId;
    private String name;
    private boolean bold;
    private Map<String, String> config = new HashMap<>();

    private transient StepStatus status = StepStatus.WHITE;
    private transient int progress = 0;

    public Step() {}

    public Step(String actionId) {
        this.actionId = actionId;
    }

    /** @return the ID of the action to execute (matches {@link com.example.automation.api.IAction#getId()}) */
    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    /** @return the optional display name, or {@code null} if not set */
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** @return whether this step should be displayed in bold */
    public boolean isBold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }

    /** @return the mutable configuration map passed to the action on execution */
    public Map<String, String> getConfig() { return config; }
    public void setConfig(Map<String, String> config) { this.config = config; }

    /** @return the transient execution status; defaults to {@link StepStatus#WHITE} */
    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }

    /** @return the transient progress percentage (0–100); 0 when not running */
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
}
