package com.example.automation.actions;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WriteFileActionHelperTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

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
        Path file = tmp.newFolder().toPath().resolve("existing.txt");
        Files.writeString(file.getParent().resolve("existing.txt"), "old");
        WriteFileAction.writeFile(file.toString(), "new");
        assertEquals("new", Files.readString(file));
    }

    @Test
    public void writeFile_utf8RoundTrip() throws Exception {
        Path file = tmp.newFolder().toPath().resolve("utf8.txt");
        String content = "héllo wörld — 日本語";
        WriteFileAction.writeFile(file.toString(), content);
        assertEquals(content, Files.readString(file, StandardCharsets.UTF_8));
    }
}
