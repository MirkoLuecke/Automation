package com.example.automation.tests;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.WriteFileAction;

public class WriteFileActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

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
    public void writeFile_createsFileWithContent() throws Exception {
        Path file = tmp.newFolder().toPath().resolve("out.txt");
        WriteFileAction.writeFile(file.toString(), "hello world");
        assertEquals("hello world", Files.readString(file));
    }

    @Test
    public void writeFile_createsParentDirectories() throws Exception {
        Path file = tmp.newFolder().toPath().resolve("a/b/c/out.txt");
        WriteFileAction.writeFile(file.toString(), "nested");
        assertTrue(Files.exists(file));
        assertEquals("nested", Files.readString(file));
    }

    @Test
    public void writeFile_overwritesExistingFile() throws Exception {
        Path file = tmp.newFile("existing.txt").toPath();
        Files.writeString(file, "old");
        WriteFileAction.writeFile(file.toString(), "new");
        assertEquals("new", Files.readString(file));
    }
}
