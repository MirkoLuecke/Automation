package com.example.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.PropertyDescriptor;
import org.eclipse.ui.views.properties.TextPropertyDescriptor;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.model.Step;

/**
 * {@link org.eclipse.ui.views.properties.IPropertySource} implementation for
 * {@link com.example.automation.model.Step}. Exposes the action ID as a read-only
 * property and each config key as an editable text (or multi-line dialog) property.
 * Saving is delegated to a {@link Runnable} injected at construction time.
 */
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
            PropertyDescriptor d = createConfigDescriptor(key);
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
        if (!(id instanceof String key) || !(value instanceof String strVal)) return;
        step.getConfig().put(key, strVal);
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
        if (!(id instanceof String key)) return false;
        IAction action = registry.getAction(step.getActionId());
        if (action == null) return step.getConfig().containsKey(key);
        String currentVal = step.getConfig().get(key);
        if (currentVal == null) return false;
        return !currentVal.equals(action.getDefaultConfig().get(key));
    }

    @Override
    public Object getEditableValue() {
        return null;
    }

    private PropertyDescriptor createConfigDescriptor(String key) {
        if ("projectName".equals(key)) {
            return new PropertyDescriptor(key, key) {
                @Override
                public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                    return new ProjectComboBoxCellEditor(parent);
                }
            };
        }
        if (StepOperations.isDirField(key)) {
            return new PropertyDescriptor(key, key) {
                @Override
                public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                    return new PathCellEditor(parent, step, PathCellEditor.PathType.DIRECTORY);
                }
            };
        }
        if (StepOperations.isFileField(key)) {
            return new PropertyDescriptor(key, key) {
                @Override
                public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                    return new PathCellEditor(parent, step, PathCellEditor.PathType.FILE);
                }
            };
        }
        if (isMultiLineField(step.getActionId(), key)) {
            return new PropertyDescriptor(key, key) {
                @Override
                public org.eclipse.jface.viewers.CellEditor createPropertyEditor(Composite parent) {
                    return new MultiLineTextCellEditor(parent);
                }
            };
        }
        return new TextPropertyDescriptor(key, key);
    }

    private static boolean isMultiLineField(String actionId, String key) {
        return ("shell-command".equals(actionId) && "command".equals(key))
            || ("write-file".equals(actionId) && "content".equals(key));
    }

    private List<String> configKeys() {
        IAction action = registry.getAction(step.getActionId());
        Map<String, String> source = (action != null) ? action.getDefaultConfig() : step.getConfig();
        return new ArrayList<>(source.keySet());
    }
}
