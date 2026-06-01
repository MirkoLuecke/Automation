package com.example.automation.actions;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
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
            MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(
                new MavenUpdateRequest(toUpdate, false, false), new NullProgressMonitor());
            context.getStdout().println("Updated configuration for " + toUpdate.size() + " project(s).");

            // Fallback: add Java nature directly for projects whose M2E lifecycle
            // mapping did not apply it (e.g., eclipse-plugin packaging without the
            // Tycho M2E connector, or when m2e-jdt is not installed)
            for (IProject project : toUpdate) {
                try {
                    if (project.isOpen() && !project.hasNature("org.eclipse.jdt.core.javanature")) {
                        addJavaNature(project);
                        context.getStdout().println("Added Java nature to: " + project.getName());
                    }
                } catch (CoreException e) {
                    context.getStdout().println("Warning: could not add Java nature to "
                        + project.getName() + ": " + e.getMessage());
                }
            }
        }
        context.setProgress(100);
    }

    private static void addJavaNature(IProject project) throws CoreException {
        IProjectDescription desc = project.getDescription();

        // Add Java nature (prepend so Eclipse UI shows it as a Java project)
        String[] natures = desc.getNatureIds();
        for (String n : natures)
            if ("org.eclipse.jdt.core.javanature".equals(n)) return; // added concurrently
        String[] newNatures = new String[natures.length + 1];
        newNatures[0] = "org.eclipse.jdt.core.javanature";
        System.arraycopy(natures, 0, newNatures, 1, natures.length);
        desc.setNatureIds(newNatures);

        // Add Java builder if not already listed
        ICommand[] cmds = desc.getBuildSpec();
        for (ICommand c : cmds)
            if ("org.eclipse.jdt.core.javabuilder".equals(c.getBuilderName())) {
                project.setDescription(desc, new NullProgressMonitor());
                return;
            }
        ICommand javaCmd = desc.newCommand();
        javaCmd.setBuilderName("org.eclipse.jdt.core.javabuilder");
        ICommand[] newCmds = new ICommand[cmds.length + 1];
        newCmds[0] = javaCmd;
        System.arraycopy(cmds, 0, newCmds, 1, cmds.length);
        desc.setBuildSpec(newCmds);

        project.setDescription(desc, new NullProgressMonitor());
    }
}
