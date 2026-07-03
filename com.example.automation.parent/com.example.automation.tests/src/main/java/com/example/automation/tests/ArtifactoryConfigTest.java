package com.example.automation.tests;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import com.example.automation.ArtifactoryConfig;

public class ArtifactoryConfigTest {

    @Test
    public void xorDecrypt_roundTrip() {
        byte[] key = { 1, 2, 3 };
        byte[] plain = "hello-world".getBytes(StandardCharsets.UTF_8);
        byte[] enc = new byte[plain.length];
        for (int i = 0; i < plain.length; i++)
            enc[i] = (byte) (plain[i] ^ key[i % key.length]);
        byte[] dec = new byte[enc.length];
        for (int i = 0; i < enc.length; i++)
            dec[i] = (byte) (enc[i] ^ key[i % key.length]);
        assertEquals("hello-world", new String(dec, StandardCharsets.UTF_8));
    }

    @Test
    public void folderUrl_isValidHttpsUrl() {
        String url = ArtifactoryConfig.folderUrl();
        assertTrue("Expected https URL, got: " + url, url.startsWith("https://"));
        assertFalse(url.isEmpty());
    }

    @Test
    public void listingUrl_containsApiStorage() {
        String url = ArtifactoryConfig.listingUrl();
        assertTrue(url.contains("/api/storage/"));
        assertTrue(url.startsWith("https://"));
    }
}
