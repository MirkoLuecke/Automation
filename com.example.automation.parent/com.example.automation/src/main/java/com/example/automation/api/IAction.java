package com.example.automation.api;

import java.util.List;
import java.util.Map;

public interface IAction {
    String getId();
    String getName();
    String getDescription();
    Map<String, String> getDefaultConfig();
    List<String> validate(Map<String, String> config);
    void execute(Map<String, String> config, IActionContext context) throws Exception;
}
