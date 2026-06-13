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

/**
 * Copies workflow JSON files bundled inside the plugin (under {@code workflows/}) into the
 * user-configured workflow storage directory. Runs at most once per installation unless
 * explicitly triggered from the preference page.
 */
public class BundledWorkflowInstaller {

    /**
     * Copies bundled workflows if they have not already been deployed to this workspace.
     *
     * @param resolvedStoragePath the absolute path to the workflow storage directory
     *                            (Eclipse variables already resolved)
     */
    public static void installIfNeeded(String resolvedStoragePath) {
        if (!AutomationPreferences.isWorkflowsDeployed()) {
            install(resolvedStoragePath);
        }
    }

    /**
     * Unconditionally copies all bundled workflow JSON files to the storage directory,
     * overwriting existing files with the same name.
     *
     * @param resolvedStoragePath the absolute path to the workflow storage directory
     *                            (Eclipse variables already resolved)
     */
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
