package com.example.automation.api;

import java.io.OutputStream;

public interface IActionContext {
    void setProgress(int percent);
    boolean isCancelled();
    OutputStream getOutputStream();
    OutputStream getErrorStream();
}
