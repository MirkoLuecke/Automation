package com.example.automation.tests;

import static org.junit.Assert.*;
import org.junit.Test;
import com.example.automation.ArtifactoryConfig;

public class ArtifactoryConfigTest {

    @Test
    public void buildFolderUrl_returnsHttpsUrl() {
        String url = ArtifactoryConfig.buildFolderUrl("testcompany");
        assertTrue("Expected https URL", url.startsWith("https://"));
    }

    @Test
    public void buildFolderUrl_containsCompanyName() {
        String url = ArtifactoryConfig.buildFolderUrl("testcompany");
        assertTrue("Expected companyName in URL", url.contains("testcompany"));
    }

    @Test
    public void buildListingUrl_containsApiStorage() {
        String folder = ArtifactoryConfig.buildFolderUrl("testcompany");
        String listing = ArtifactoryConfig.buildListingUrl(folder);
        assertTrue("Expected /api/storage/ in listing URL", listing.contains("/api/storage/"));
    }

    @Test
    public void buildListingUrl_startsWithHttps() {
        String folder = ArtifactoryConfig.buildFolderUrl("testcompany");
        String listing = ArtifactoryConfig.buildListingUrl(folder);
        assertTrue("Expected https", listing.startsWith("https://"));
    }

    @Test
    public void companyNameFrom_extractsSecondToLast() {
        assertEquals("mycompany", ArtifactoryConfig.companyNameFrom("host.mycompany.se"));
    }

    @Test
    public void companyNameFrom_lowercasesResult() {
        assertEquals("mycompany", ArtifactoryConfig.companyNameFrom("SOMETHING.MYCOMPANY.SE"));
    }

    @Test
    public void companyNameFrom_returnsNullForSinglePart() {
        assertNull(ArtifactoryConfig.companyNameFrom("hostname"));
    }

    @Test
    public void companyNameFrom_returnsNullForNull() {
        assertNull(ArtifactoryConfig.companyNameFrom(null));
    }

    @Test
    public void companyNameFrom_handlesSubdomains() {
        assertEquals("mycompany", ArtifactoryConfig.companyNameFrom("sub.domain.mycompany.se"));
    }
}
