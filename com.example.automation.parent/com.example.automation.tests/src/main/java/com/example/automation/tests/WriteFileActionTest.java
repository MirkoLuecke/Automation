package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.WriteFileAction;
import com.example.automation.api.IActionContext;

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
    public void getId_returnsWriteFile() {
        assertEquals("write-file", new WriteFileAction().getId());
    }

    @Test
    public void getName_returnsWriteFile() {
        assertEquals("Write File", new WriteFileAction().getName());
    }

    @Test
    public void writeFile_createsNestedFileWithContent() throws Exception {
        File file = new File(tmp.getRoot(), "sub/dir/test.txt");
        WriteFileAction.writeFile(file.getAbsolutePath(), "hello world");
        assertEquals("hello world", Files.readString(file.toPath()));
    }

    @Test
    public void execute_writesFileAndProgressReaches100() throws Exception {
        File file = new File(tmp.getRoot(), "out.txt");
        List<Integer> progress = new ArrayList<>();
        new WriteFileAction().execute(
            Map.of("filePath", file.getAbsolutePath(), "content", "content"),
            trackingCtx(progress));
        assertEquals("content", Files.readString(file.toPath()));
        assertTrue(progress.contains(0));
        assertTrue(progress.contains(100));
    }

    @Test
    public void execute_contentWithVariableLiteral_writtenVerbatim() throws Exception {
        File file = new File(tmp.getRoot(), "out.txt");
        String rawContent = "value=${some_undefined_variable}";
        new WriteFileAction().execute(
            Map.of("filePath", file.getAbsolutePath(), "content", rawContent),
            trackingCtx(new ArrayList<>()));
        assertEquals(rawContent, Files.readString(file.toPath()));
    }

    private static IActionContext trackingCtx(List<Integer> progress) {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        { progress.add(p); }
            @Override public boolean isCancelled()          { return false; }
        };
    }
}
