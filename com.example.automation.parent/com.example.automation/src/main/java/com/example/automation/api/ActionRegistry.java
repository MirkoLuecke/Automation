package com.example.automation.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

public class ActionRegistry {

    private static final String EXTENSION_POINT = "com.example.automation.actions";
    private static ActionRegistry instance;

    private final Map<String, IAction> actionsById;
    private final List<IAction> allActions;

    /** For unit testing only. Production code must use {@link #getInstance()}. */
    public ActionRegistry(List<IAction> actions) {
        Map<String, IAction> map = new LinkedHashMap<>();
        for (IAction action : actions) {
            map.put(action.getId(), action);
        }
        this.actionsById = Collections.unmodifiableMap(map);
        this.allActions = Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    public static synchronized ActionRegistry getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ActionRegistry load() {
        ILog log = Platform.getLog(ActionRegistry.class);
        List<IAction> actions = new ArrayList<>();
        IConfigurationElement[] elements = Platform.getExtensionRegistry()
                .getConfigurationElementsFor(EXTENSION_POINT);
        for (IConfigurationElement element : elements) {
            try {
                IAction action = (IAction) element.createExecutableExtension("class");
                actions.add(action);
            } catch (Exception e) {
                log.error("Failed to instantiate action: " + element.getAttribute("class"), e);
            }
        }
        return new ActionRegistry(actions);
    }

    public IAction getAction(String id) {
        return actionsById.get(id);
    }

    public List<IAction> getAllActions() {
        return allActions;
    }
}
