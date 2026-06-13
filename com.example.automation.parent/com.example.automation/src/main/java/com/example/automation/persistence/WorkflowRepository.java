package com.example.automation.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

import com.example.automation.model.Workflow;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Reads and writes {@link com.example.automation.model.Workflow} objects as JSON files
 * in a local directory. Each workflow is stored as {@code <workflowId>.json}. Saves are
 * atomic: the JSON is written to a {@code .json.tmp} file first, then renamed, so a
 * crash during save never leaves a corrupt file.
 */
public class WorkflowRepository {

    private static final ILog LOG = Platform.getLog(WorkflowRepository.class);

    private final File storageDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public WorkflowRepository() {
        this(new File(System.getProperty("user.home"), "automation"));
    }

    /**
     * Constructs a repository backed by the given directory.
     *
     * @param storageDir the directory to read from and write to; created on demand
     */
    public WorkflowRepository(File storageDir) {
        this.storageDir = storageDir;
    }

    /**
     * Returns all workflows found in the storage directory.
     * Files that cannot be parsed are logged and skipped.
     *
     * @return mutable list of workflows; empty if the directory is empty or does not exist
     * @throws IOException if the directory cannot be accessed
     */
    public List<Workflow> list() throws IOException {
        ensureDirectoryExists();
        List<Workflow> result = new ArrayList<>();
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return result;
        for (File file : files) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                Workflow wf = gson.fromJson(reader, Workflow.class);
                if (wf != null) result.add(wf);
            } catch (Exception e) {
                LOG.error("Failed to read workflow file: " + file.getName(), e);
            }
        }
        return result;
    }

    /**
     * Persists a workflow to disk as {@code <workflowId>.json} using an atomic write.
     *
     * @param workflow the workflow to save; its {@code workflowId} must not be blank
     * @throws IOException if the file cannot be written
     */
    public void save(Workflow workflow) throws IOException {
        requireValidId(workflow.getWorkflowId());
        ensureDirectoryExists();
        File temp = new File(storageDir, workflow.getWorkflowId() + ".json.tmp");
        File target = new File(storageDir, workflow.getWorkflowId() + ".json");
        boolean moved = false;
        try (FileWriter writer = new FileWriter(temp, StandardCharsets.UTF_8)) {
            gson.toJson(workflow, writer);
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) {
                temp.delete();
            }
        }
    }

    /**
     * Loads a single workflow by ID.
     *
     * @param workflowId the ID of the workflow to load; must not be blank
     * @return the deserialized workflow, or {@code null} if the file contains a null JSON literal
     * @throws IOException if the file does not exist or cannot be read
     */
    public Workflow load(String workflowId) throws IOException {
        requireValidId(workflowId);
        File file = new File(storageDir, workflowId + ".json");
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, Workflow.class);
        }
    }

    /**
     * Deletes the JSON file for the given workflow ID.
     *
     * @param workflowId the ID of the workflow to delete; must not be blank
     * @return {@code true} if the file was deleted, {@code false} if it did not exist
     */
    public boolean delete(String workflowId) {
        requireValidId(workflowId);
        return new File(storageDir, workflowId + ".json").delete();
    }

    private void ensureDirectoryExists() throws IOException {
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            throw new IOException("Cannot create storage directory: " + storageDir);
        }
    }

    private static void requireValidId(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("workflowId must not be blank");
        if (id.contains("/") || id.contains("\\") || id.contains(".."))
            throw new IllegalArgumentException("workflowId contains illegal characters: " + id);
    }
}
