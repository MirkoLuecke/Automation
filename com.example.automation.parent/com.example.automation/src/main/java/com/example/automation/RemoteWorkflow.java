package com.example.automation;

import com.example.automation.model.Workflow;

/** A workflow entry retrieved from Artifactory, including its raw JSON for later save. */
class RemoteWorkflow {

    final String filename;   // e.g. "my-workflow.json"
    final Workflow workflow;
    final String rawJson;

    RemoteWorkflow(String filename, Workflow workflow, String rawJson) {
        this.filename = filename;
        this.workflow = workflow;
        this.rawJson = rawJson;
    }
}
