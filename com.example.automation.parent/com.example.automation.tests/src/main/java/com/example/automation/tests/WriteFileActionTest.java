package com.example.automation.tests;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.example.automation.actions.WriteFileAction;

public class WriteFileActionTest {

    @Test
    public void validate_rejectsBlankFilePath() {
        List<String> errors = new WriteFileAction().validate(Map.of("filePath", "", "content", ""));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_acceptsNonBlankFilePath() {
        List<String> errors = new WriteFileAction().validate(Map.of("filePath", "settings.xml", "content", ""));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void defaultConfig_containsFilePathAndContentKeys() {
        Map<String, String> cfg = new WriteFileAction().getDefaultConfig();
        assertTrue(cfg.containsKey("filePath"));
        assertTrue(cfg.containsKey("content"));
    }

    @Test
    public void getId_returnsWriteFile() {
        assertEquals("write-file", new WriteFileAction().getId());
    }

    @Test
    public void getName_returnsWriteFile() {
        assertEquals("Write File", new WriteFileAction().getName());
    }
}
