package com.example.automation.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

/**
 * Registry for actions contributed via the {@code com.example.automation.actions}
 * extension point. Loaded lazily on first access; use {@link #getInstance()} in
 * production code and the list constructor only in unit tests.
 */
public class ActionRegistry {

    private static final String EXTENSION_POINT = "com.example.automation.actions";
    private static ActionRegistry instance;

    private final Map<String, IAction> actionsById;
    private final List<IAction> allActions;

    /**
     * Constructs a registry from an explicit action list (for unit testing only).
     * Production code must use {@link #getInstance()}.
     *
     * @param actions the actions to register; duplicate IDs are not supported
     */
    public ActionRegistry(List<IAction> actions) {
        Map<String, IAction> map = new LinkedHashMap<>();
        for (IAction action : actions) {
            map.put(action.getId(), action);
        }
        this.actionsById = Collections.unmodifiableMap(map);
        this.allActions = Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    /**
     * Returns the singleton registry, loading extension-point contributions on first call.
     *
     * @return the shared registry instance; never null
     */
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

    /**
     * Returns the action registered under the given ID.
     *
     * @param id the action ID as declared in the extension point; must not be null
     * @return the action, or {@code null} if no action is registered under that ID
     */
    public IAction getAction(String id) {
        return actionsById.get(id);
    }

    /**
     * Returns all registered actions in registration order.
     *
     * @return unmodifiable list of actions; never null
     */
    public List<IAction> getAllActions() {
        return allActions;
    }
}
