package com.example.automation.api;

import java.io.PrintStream;
import java.util.Map;
import java.util.function.Consumer;

import com.example.automation.model.Step;

public interface IActionContext {
    Map<String, String> getConfig();
    String getWorkingDirectory();
    Step getStep();
    PrintStream getStdout();
    PrintStream getStderr();
    Consumer<Runnable> getUiExecutor();
}
