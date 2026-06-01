package com.example.automation.actions;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.MavenUpdateRequest;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.osgi.framework.Bundle;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

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
        MavenPlugin.getProjectConfigurationManager().importProjects(
            allModules,
            new ProjectImportConfiguration(),
            new NullProgressMonitor());

        // Find workspace projects by filesystem location — reliable even when
        // importProjects() skips pre-existing projects (returning null IProject results)
        Set<File> importedDirs = new HashSet<>();
        for (MavenProjectInfo info : allModules)
            importedDirs.add(info.getPomFile().getParentFile().getAbsoluteFile());

        List<IProject> toUpdate = new ArrayList<>();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            try {
                if (!project.isOpen()) continue;
                IPath loc = project.getLocation();
                if (loc != null && importedDirs.contains(loc.toFile().getAbsoluteFile()))
                    toUpdate.add(project);
            } catch (Exception ignored) {}
        }

        if (!toUpdate.isEmpty()) {
            context.getStdout().println("Running M2E update for " + toUpdate.size() + " project(s)...");
            MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(
                new MavenUpdateRequest(toUpdate, false, false), new NullProgressMonitor());
            context.getStdout().println("Done.");
        }
        context.setProgress(100);
    }
}
