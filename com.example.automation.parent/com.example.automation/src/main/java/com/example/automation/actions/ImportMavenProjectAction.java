package com.example.automation.actions;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that scans a directory for Maven modules,
 * removes all {@code target/} directories, and imports the projects into the Eclipse
 * workspace using M2E. Waits for M2E's background project-configuration jobs to finish.
 *
 * <p>Config keys: {@code pomPath} (required — path to a {@code pom.xml} or its parent directory).
 */
public class ImportMavenProjectAction implements IAction {

    @Override public String getId()          { return "import-maven-project"; }
    @Override public String getName()        { return "Import Maven Project"; }
    @Override public String getDescription() { return "Imports an existing Maven project and all its modules into the Eclipse workspace."; }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("pomPath", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        Bundle m2e = Platform.getBundle("org.eclipse.m2e.core");
        if (m2e == null || m2e.getState() != Bundle.ACTIVE)
            errors.add("M2E (Maven Integration for Eclipse) is not installed or not active.");
        if (config.getOrDefault("pomPath", "").isBlank())
            errors.add("pomPath must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String pomPath = config.get("pomPath");
        if (pomPath == null || pomPath.isBlank())
            throw new IllegalArgumentException("pomPath must not be blank");

        File pomFile = new File(pomPath);
        if (pomFile.isDirectory()) pomFile = new File(pomFile, "pom.xml");
        if (!pomFile.exists())
            throw new Exception("pom.xml not found at: " + pomFile.getAbsolutePath());

        context.setProgress(0);
        LocalProjectScanner scanner = new LocalProjectScanner(
            List.of(pomFile.getParentFile().getAbsolutePath()),
            false,
            MavenPlugin.getMavenModelManager());
        scanner.run(new NullProgressMonitor());

        // Flatten the module tree: scanner.getProjects() returns only root projects;
        // child modules are nested inside MavenProjectInfo.getProjects()
        List<MavenProjectInfo> allModules = new ArrayList<>();
        Queue<MavenProjectInfo> queue = new ArrayDeque<>(scanner.getProjects());
        while (!queue.isEmpty()) {
            MavenProjectInfo info = queue.poll();
            allModules.add(info);
            if (info.getProjects() != null)
                queue.addAll(info.getProjects());
        }
        context.getStdout().println("Discovered " + allModules.size() + " Maven project(s) to import.");
        deleteTargetDirs(pomFile.getParentFile(), context);
        MavenPlugin.getProjectConfigurationManager().importProjects(
            allModules,
            new ProjectImportConfiguration(),
            new ImportMonitor(context, allModules.size()));
        // Wait for M2E background project-configuration jobs (UpdateProjectJob)
        // These are scheduled inside importProjects() and run after it returns.
        IJobManager jm = Job.getJobManager();
        try {
            jm.join(MavenPlugin.getProjectConfigurationManager(), new BackgroundMonitor(context));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        context.setProgress(100);
    }

    private static void deleteTargetDirs(File dir, IActionContext context) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            if ("target".equals(child.getName())) {
                deleteRecursively(child);
                context.getStdout().println("Removed: " + child.getAbsolutePath());
            } else {
                deleteTargetDirs(child, context);
            }
        }
    }

    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null)
                for (File child : children) deleteRecursively(child);
        }
        f.delete();
    }

    private static final class ImportMonitor implements IProgressMonitor {
        private final IActionContext context;
        private final int total;
        private int done = 0;

        ImportMonitor(IActionContext context, int total) {
            this.context = context;
            this.total = total > 0 ? total : 1;
        }

        @Override
        public void beginTask(String name, int totalWork) { context.setProgress(5); }

        @Override
        public void worked(int work) {
            done += work;
            context.setProgress(Math.min(40, done * 40 / total));
        }

        @Override public void done()                      {}
        @Override public boolean isCanceled()             { return false; }
        @Override public void setCanceled(boolean value)  {}
        @Override public void setTaskName(String name)    {}
        @Override public void subTask(String name)        {}
        @Override public void internalWorked(double work) {}
    }

    private static final class BackgroundMonitor implements IProgressMonitor {
        private final IActionContext context;
        private int total = 1;
        private int done  = 0;

        BackgroundMonitor(IActionContext context) { this.context = context; }

        @Override
        public void beginTask(String name, int totalWork) {
            this.total = totalWork > 0 ? totalWork : 1;
            context.setProgress(40);
        }

        @Override
        public void worked(int work) {
            done += work;
            context.setProgress(Math.min(99, 40 + done * 59 / total));
        }

        @Override public void done()                      {}
        @Override public boolean isCanceled()             { return false; }
        @Override public void setCanceled(boolean value)  {}
        @Override public void setTaskName(String name)    {}
        @Override public void subTask(String name)        {}
        @Override public void internalWorked(double work) {}
    }
}
