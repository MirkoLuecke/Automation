package com.example.automation.model;

import java.util.HashMap;
import java.util.Map;

public class Step {

    private String actionId;
    private Map<String, String> config = new HashMap<>();

    private transient StepStatus status = StepStatus.WHITE;
    private transient int progress = 0;

    public Step() {}

    public Step(String actionId) {
        this.actionId = actionId;
    }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public Map<String, String> getConfig() { return config; }
    public void setConfig(Map<String, String> config) { this.config = config; }

    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
}
