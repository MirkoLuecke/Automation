package com.example.automation;

import java.net.InetAddress;
import java.util.Locale;

public final class ArtifactoryConfig {

    static final String ENV_FOLDER_URL = "ARTIFACTORY_FOLDER_URL";

    public static String folderUrl() {
        String direct = System.getenv(ENV_FOLDER_URL);
        if (direct != null && !direct.isBlank()) return direct;
        String companyName = extractCompanyName();
        return companyName != null ? buildFolderUrl(companyName) : null;
    }

    public static String listingUrl() {
        String folder = folderUrl();
        return folder != null ? buildListingUrl(folder) : null;
    }

    public static String buildFolderUrl(String companyName) {
        return "https://artifactory.surv-xc-de.cap." + companyName + ".se"
             + "/artifactory/abew-smr-sync/tools/eclipse/automation-workflows";
    }

    public static String buildListingUrl(String folderUrl) {
        int idx = folderUrl.indexOf("/artifactory/") + "/artifactory".length();
        return folderUrl.substring(0, idx) + "/api/storage" + folderUrl.substring(idx);
    }

    public static String extractCompanyName() {
        String companyName = companyNameFrom(System.getenv("USERDNSDOMAIN"));
        if (companyName != null) return companyName;
        try {
            companyName = companyNameFrom(InetAddress.getLocalHost().getCanonicalHostName());
            if (companyName != null) return companyName;
        } catch (Exception ignored) {}
        try {
            Process proc = Runtime.getRuntime().exec("hostname");
            String output = new String(proc.getInputStream().readAllBytes()).trim();
            companyName = companyNameFrom(output);
            if (companyName != null) return companyName;
        } catch (Exception ignored) {}
        return null;
    }

    public static String companyNameFrom(String fqdn) {
        if (fqdn == null || fqdn.isBlank()) return null;
        String[] parts = fqdn.toLowerCase(Locale.ROOT).split("\\.");
        return parts.length >= 2 ? parts[parts.length - 2] : null;
    }

    private ArtifactoryConfig() {}
}
