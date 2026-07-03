package com.example.automation;

import com.example.automation.model.Workflow;

/** A workflow entry retrieved from Artifactory, including its raw JSON for later save. */
public class RemoteWorkflow {

    public final String filename;   // e.g. "my-workflow.json"
    public final Workflow workflow;
    public final String rawJson;

    public RemoteWorkflow(String filename, Workflow workflow, String rawJson) {
        this.filename = filename;
        this.workflow = workflow;
        this.rawJson = rawJson;
    }
}
