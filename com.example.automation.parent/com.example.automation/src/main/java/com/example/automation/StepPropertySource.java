package com.example.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.PropertyDescriptor;
import org.eclipse.ui.views.properties.TextPropertyDescriptor;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.model.Step;

public class StepPropertySource implements IPropertySource {

    private static final String PROP_ACTION = "action";

    private final Step step;
    private final ActionRegistry registry;
    private final Runnable save;

    public StepPropertySource(Step step, ActionRegistry registry, Runnable save) {
        this.step     = step;
        this.registry = registry;
        this.save     = save;
    }

    @Override
    public IPropertyDescriptor[] getPropertyDescriptors() {
        List<IPropertyDescriptor> list = new ArrayList<>();
        PropertyDescriptor actionDesc = new PropertyDescriptor(PROP_ACTION, "Action");
        actionDesc.setCategory("Step");
        list.add(actionDesc);
        for (String key : configKeys()) {
            TextPropertyDescriptor d = new TextPropertyDescriptor(key, key);
            d.setCategory("Config");
            list.add(d);
        }
        return list.toArray(new IPropertyDescriptor[0]);
    }

    @Override
    public Object getPropertyValue(Object id) {
        if (PROP_ACTION.equals(id)) {
            String aid = step.getActionId();
            return aid != null ? aid : "";
        }
        return (id instanceof String key) ? step.getConfig().getOrDefault(key, "") : "";
    }

    @Override
    public void setPropertyValue(Object id, Object value) {
        if (PROP_ACTION.equals(id)) return;
        if (!(id instanceof String key)) return;
        step.getConfig().put(key, (String) value);
        save.run();
    }

    @Override
    public void resetPropertyValue(Object id) {
        if (PROP_ACTION.equals(id)) return;
        if (!(id instanceof String key)) return;
        IAction action = registry.getAction(step.getActionId());
        if (action == null) return; // action unknown: no canonical default, leave value unchanged
        String def = action.getDefaultConfig().get(key);
        if (def != null) {
            step.getConfig().put(key, def);
            save.run();
        }
    }

    @Override
    public boolean isPropertySet(Object id) {
        if (PROP_ACTION.equals(id)) return false;
        return (id instanceof String key) && step.getConfig().containsKey(key);
    }

    @Override
    public Object getEditableValue() {
        return null;
    }

    private List<String> configKeys() {
        IAction action = registry.getAction(step.getActionId());
        Map<String, String> source = (action != null) ? action.getDefaultConfig() : step.getConfig();
        return new ArrayList<>(source.keySet());
    }
}
