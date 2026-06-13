package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.pde.core.target.ITargetDefinition;
import org.eclipse.pde.core.target.ITargetHandle;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.core.target.LoadTargetDefinitionJob;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that loads, resolves, and activates a
 * {@code .target} file as the Eclipse workspace target platform using PDE's
 * {@link org.eclipse.pde.core.target.ITargetPlatformService}.
 *
 * <p>Config keys: {@code targetFile} (required — absolute path to a {@code .target} file).
 */
public class SetActiveTargetPlatformAction implements IAction {

    @Override public String getId()          { return "set-active-target-platform"; }
    @Override public String getName()        { return "Set Active Target Platform"; }
    @Override public String getDescription() {
        return "Loads, resolves, and activates a .target file as the Eclipse workspace target platform.";
    }

    @Override
    public Map<String, String> getDefaultConfig() { return Map.of("targetFile", ""); }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        Bundle pdeCore = Platform.getBundle("org.eclipse.pde.core");
        if (pdeCore == null || pdeCore.getState() != Bundle.ACTIVE)
            errors.add("PDE Core (org.eclipse.pde.core) is not installed or not active.");
        if (config.getOrDefault("targetFile", "").isBlank())
            errors.add("targetFile must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String targetPath = config.get("targetFile");
        if (targetPath == null || targetPath.isBlank())
            throw new IllegalArgumentException("targetFile must not be blank");

        File targetFile = new File(targetPath);
        if (!targetFile.exists())
            throw new Exception(".target file not found at: " + targetFile.getAbsolutePath());

        Bundle pdeCore = Platform.getBundle("org.eclipse.pde.core");
        BundleContext bc = pdeCore.getBundleContext();
        if (bc == null)
            throw new Exception("Cannot get BundleContext from org.eclipse.pde.core");

        ServiceReference<ITargetPlatformService> ref =
            bc.getServiceReference(ITargetPlatformService.class);
        if (ref == null)
            throw new Exception("ITargetPlatformService is not available");
        ITargetPlatformService service = bc.getService(ref);
        try {
            ITargetHandle handle = service.getTarget(targetFile.toURI());
            ITargetDefinition definition = handle.getTargetDefinition();

            context.getStdout().println("Resolving target platform: " + targetFile.getName());
            IStatus status = definition.resolve(new TargetMonitor(context));
            if (!status.isOK())
                context.getStderr().println("Target resolution status: " + status.getMessage());
            if (status.getSeverity() == IStatus.ERROR)
                throw new Exception("Target platform resolution failed: " + status.getMessage());

            context.getStdout().println("Activating target platform...");
            LoadTargetDefinitionJob job = new LoadTargetDefinitionJob(definition);
            job.schedule();
            job.join();
            IStatus jobResult = job.getResult();
            if (jobResult != null && jobResult.getSeverity() == IStatus.ERROR)
                throw new Exception("Target platform activation failed: " + jobResult.getMessage());
        } finally {
            bc.ungetService(ref);
        }

        context.setProgress(100);
    }

    private static final class TargetMonitor implements IProgressMonitor {
        private final IActionContext context;
        private int total = 1;
        private int done  = 0;

        TargetMonitor(IActionContext context) { this.context = context; }

        @Override
        public void beginTask(String name, int totalWork) {
            this.total = totalWork > 0 ? totalWork : 1;
            context.setProgress(0);
        }

        @Override
        public void worked(int work) {
            done += work;
            context.setProgress(Math.min(90, done * 90 / total));
        }

        @Override public void done()                      {}
        @Override public boolean isCanceled()             { return false; }
        @Override public void setCanceled(boolean value)  {}
        @Override public void setTaskName(String name)    {}
        @Override public void subTask(String name)        {}
        @Override public void internalWorked(double work) {}
    }
}
