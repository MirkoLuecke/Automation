package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.actions.SetXmlTagTextAction;

public class SetXmlTagTextActionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File writeXml(String content) throws Exception {
        File f = tmp.newFile("test.xml");
        Files.writeString(f.toPath(), content);
        return f;
    }

    @Test
    public void replaceTagText_updatesContent() throws Exception {
        File f = writeXml("<root><child>old</child></root>");
        new SetXmlTagTextAction().updateXml(f.getAbsolutePath(), "/root/child", "new");
        String result = Files.readString(f.toPath());
        assertTrue("Must contain new value", result.contains("<child>new</child>") || result.contains(">new<"));
        assertFalse("Must not contain old value", result.contains("old"));
    }

    @Test
    public void preservesXmlComment() throws Exception {
        File f = writeXml("<root><!-- keep this --><child>old</child></root>");
        new SetXmlTagTextAction().updateXml(f.getAbsolutePath(), "/root/child", "new");
        String result = Files.readString(f.toPath());
        assertTrue("XML comment must be preserved", result.contains("<!-- keep this -->"));
    }

    @Test(expected = Exception.class)
    public void missingTag_throws() throws Exception {
        File f = writeXml("<root><child>old</child></root>");
        new SetXmlTagTextAction().updateXml(f.getAbsolutePath(), "/root/missing", "new");
    }

    @Test
    public void replacesAllMatchingLeafNodes() throws Exception {
        File f = writeXml("<root><item>a</item><item>b</item></root>");
        new SetXmlTagTextAction().updateXml(f.getAbsolutePath(), "/root/item", "x");
        String result = Files.readString(f.toPath());
        assertFalse("Old value 'a' must be gone", result.contains(">a<"));
        assertFalse("Old value 'b' must be gone", result.contains(">b<"));
    }

    @Test
    public void validate_requiresFilePath() {
        List<String> errors = new SetXmlTagTextAction().validate(
            Map.of("filePath", "", "tagPath", "/root/tag", "value", "v"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void validate_requiresTagPath() {
        List<String> errors = new SetXmlTagTextAction().validate(
            Map.of("filePath", "f.xml", "tagPath", "", "value", "v"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void getId_returnsSetXmlTagText() {
        assertEquals("set-xml-tag-text", new SetXmlTagTextAction().getId());
    }
}
