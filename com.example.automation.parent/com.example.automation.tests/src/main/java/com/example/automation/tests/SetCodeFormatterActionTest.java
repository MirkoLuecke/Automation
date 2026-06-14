package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.SetCodeFormatterAction;
import com.example.automation.api.IActionContext;

public class SetCodeFormatterActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String PREF_NODE = "org.eclipse.jdt.core";
    private static final String TEST_KEY  = "org.eclipse.jdt.core.formatter.tabulation.char";

    private String originalValue;

    @Before
    public void captureOriginal() throws Exception {
        originalValue = InstanceScope.INSTANCE.getNode(PREF_NODE).get(TEST_KEY, null);
    }

    @After
    public void restorePreference() throws Exception {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        if (originalValue == null)
            prefs.remove(TEST_KEY);
        else
            prefs.put(TEST_KEY, originalValue);
        prefs.flush();
    }

    private static IActionContext nullCtx() {
        return new IActionContext() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public OutputStream getErrorStream()  { return OutputStream.nullOutputStream(); }
            @Override public void setProgress(int p)        {}
            @Override public boolean isCancelled()           { return false; }
        };
    }

    @Test
    public void apply_writesSettingsToPreferences() throws Exception {
        File xml = tmp.newFile("formatter.xml");
        Files.writeString(xml.toPath(),
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
            "<profiles version=\"21\">\n" +
            "  <profile kind=\"CodeFormatterProfile\" name=\"TestProfile\" version=\"21\">\n" +
            "    <setting id=\"" + TEST_KEY + "\" value=\"space\"/>\n" +
            "  </profile>\n" +
            "</profiles>\n");

        new SetCodeFormatterAction().execute(
            Map.of("filePath", xml.getAbsolutePath()), nullCtx());

        assertEquals("space",
            InstanceScope.INSTANCE.getNode(PREF_NODE).get(TEST_KEY, null));
    }

    @Test
    public void apply_missingFile_throws() {
        try {
            new SetCodeFormatterAction().execute(
                Map.of("filePath", "/nonexistent/path/formatter.xml"), nullCtx());
            fail("Expected exception for missing file");
        } catch (Exception e) {
            assertTrue("Message should mention 'not found'",
                e.getMessage().contains("not found"));
        }
    }

    @Test
    public void validate_blankFilePath_returnsError() {
        List<String> errors = new SetCodeFormatterAction().validate(Map.of("filePath", ""));
        assertFalse("validate() must reject blank filePath", errors.isEmpty());
    }

    @Test
    public void getId_returnsSetCodeFormatter() {
        assertEquals("set-code-formatter", new SetCodeFormatterAction().getId());
    }

    @Test
    public void getName_returnsSetCodeFormatter() {
        assertEquals("Set Code Formatter", new SetCodeFormatterAction().getName());
    }
}
