package com.example.automation.actions;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.example.automation.EclipseVariables;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class SetXmlTagTextAction implements IAction {

    @Override public String getId()          { return "set-xml-tag-text"; }
    @Override public String getName()        { return "Set XML Tag Text"; }
    @Override public String getDescription() { return "Sets the text content of a tag in an XML file. The tag path uses slash-separated tag names (e.g. /root/settings/value). Fails if any tag is not found."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("filePath", "", "tagPath", "", "value", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("filePath", "").isBlank())
            errors.add("filePath must not be blank");
        if (config.getOrDefault("tagPath", "").isBlank())
            errors.add("tagPath must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String filePath = EclipseVariables.resolve(config.getOrDefault("filePath", ""));
        String tagPath  = config.getOrDefault("tagPath", "");
        String value    = EclipseVariables.resolve(config.getOrDefault("value", ""));
        context.setProgress(0);
        updateXml(filePath, tagPath, value);
        context.getStdout().println("Updated tag " + tagPath + " in " + filePath);
        context.setProgress(100);
    }

    /**
     * Parses filePath as XML, walks tagPath (slash-separated names),
     * replaces text content of all matching leaf nodes with value, writes back.
     * Public for testing.
     */
    public void updateXml(String filePath, String tagPath, String value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(filePath));

        String[] parts = tagPath.split("/");
        // Walk to the parent of the final tag
        org.w3c.dom.Node current = doc.getDocumentElement();
        String rootTagName = current.getNodeName();
        int startIndex = 0;
        // Skip empty parts from leading slash
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) { startIndex = i; break; }
        }
        // If the first non-empty part matches the document root, skip it
        if (parts[startIndex].equals(rootTagName)) startIndex++;

        // Walk intermediate tags
        for (int i = startIndex; i < parts.length - 1; i++) {
            String tag = parts[i];
            if (tag.isEmpty()) continue;
            NodeList nl = ((org.w3c.dom.Element) current).getElementsByTagName(tag);
            if (nl.getLength() == 0)
                throw new Exception("Tag '" + tag + "' not found in " + filePath);
            current = nl.item(0);
        }

        // Replace all leaf nodes at the final path component
        String leafTag = parts[parts.length - 1];
        NodeList leaves = ((org.w3c.dom.Element) current).getElementsByTagName(leafTag);
        if (leaves.getLength() == 0)
            throw new Exception("Tag '" + leafTag + "' not found in " + filePath);
        for (int i = 0; i < leaves.getLength(); i++) {
            org.w3c.dom.Node node = leaves.item(i);
            // Remove all child text nodes, then add new text
            while (node.hasChildNodes()) node.removeChild(node.getFirstChild());
            node.appendChild(doc.createTextNode(value));
        }

        // Serialize back without re-indenting (preserves whitespace and comments)
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        Files.writeString(new File(filePath).toPath(), sw.toString(), StandardCharsets.UTF_8);
    }
}
