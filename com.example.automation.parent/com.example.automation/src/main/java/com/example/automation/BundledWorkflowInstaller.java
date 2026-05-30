package com.example.automation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import com.example.automation.preferences.AutomationPreferences;

public class BundledWorkflowInstaller {

    public static void installIfNeeded(String resolvedStoragePath) {
        if (!AutomationPreferences.isWorkflowsDeployed()) {
            install(resolvedStoragePath);
        }
    }

    public static void install(String resolvedStoragePath) {
        Bundle bundle = Platform.getBundle("com.example.automation");
        File targetDir = new File(resolvedStoragePath);
        targetDir.mkdirs();

        Enumeration<URL> entries = bundle.findEntries("workflows", "*.json", false);
        if (entries == null) return;

        boolean allSucceeded = true;
        while (entries.hasMoreElements()) {
            URL url = entries.nextElement();
            String filename = url.getFile().substring(url.getFile().lastIndexOf('/') + 1);
            File target = new File(targetDir, filename);
            try (InputStream in = url.openStream()) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                allSucceeded = false;
                Platform.getLog(BundledWorkflowInstaller.class)
                        .warn("Could not deploy bundled workflow: " + filename, e);
            }
        }
        if (allSucceeded) {
            AutomationPreferences.setWorkflowsDeployed(true);
        }
    }
}
