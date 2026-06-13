package com.example.automation;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.ui.views.properties.IPropertySource;

import com.example.automation.api.ActionRegistry;
import com.example.automation.model.Step;

/**
 * Eclipse {@link IAdapterFactory} that adapts {@link com.example.automation.model.Step}
 * objects to {@link org.eclipse.ui.views.properties.IPropertySource}, enabling the
 * Eclipse Properties view to display and edit step configuration.
 */
public class StepAdapterFactory implements IAdapterFactory {

    private final Runnable save;

    public StepAdapterFactory(Runnable save) {
        this.save = save;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
        if (adapterType == IPropertySource.class && adaptableObject instanceof Step step) {
            return (T) new StepPropertySource(step, ActionRegistry.getInstance(), save);
        }
        return null;
    }

    @Override
    public Class<?>[] getAdapterList() {
        return new Class<?>[] { IPropertySource.class };
    }
}
