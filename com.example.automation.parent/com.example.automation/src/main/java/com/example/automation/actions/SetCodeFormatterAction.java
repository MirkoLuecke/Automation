package com.example.automation.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that reads an Eclipse formatter XML
 * profile and applies its settings to the workspace-level JDT preferences.
 *
 * <p>Config keys: {@code filePath} (required — path to an Eclipse formatter XML export file).
 */
public class SetCodeFormatterAction implements IAction {

    @Override public String getId()          { return "set-code-formatter"; }
    @Override public String getName()        { return "Set Code Formatter"; }
    @Override public String getDescription() {
        return "Applies an Eclipse formatter XML profile to the workspace JDT settings.";
    }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("filePath", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("filePath", "").isBlank())
            errors.add("filePath must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String filePath = config.getOrDefault("filePath", "");
        if (filePath.isBlank()) throw new IllegalArgumentException("filePath must not be blank");

        context.setProgress(0);

        File file = new File(filePath);
        if (!file.exists())
            throw new Exception("Formatter file not found: " + file.getAbsolutePath());

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        Document doc = dbf.newDocumentBuilder().parse(file);

        NodeList profiles = doc.getElementsByTagName("profile");
        Element profile = null;
        for (int i = 0; i < profiles.getLength(); i++) {
            Element candidate = (Element) profiles.item(i);
            if ("CodeFormatterProfile".equals(candidate.getAttribute("kind"))) {
                profile = candidate;
                break;
            }
        }
        if (profile == null)
            throw new Exception("No CodeFormatterProfile found in: " + file.getName());

        String profileName = profile.getAttribute("name");
        NodeList settings = profile.getElementsByTagName("setting");

        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode("org.eclipse.jdt.core");
        for (int i = 0; i < settings.getLength(); i++) {
            Element setting = (Element) settings.item(i);
            prefs.put(setting.getAttribute("id"), setting.getAttribute("value"));
        }
        prefs.flush();

        context.getStdout().println("Applied formatter profile '" + profileName
            + "' (" + settings.getLength() + " settings) to workspace JDT preferences.");
        context.setProgress(100);
    }
}
