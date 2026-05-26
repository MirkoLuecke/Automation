package com.example.automation.actions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.example.automation.api.IActionContext;

class ProcessRunner {

    static void run(List<String> cmd, File dir, IActionContext context) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (dir != null) pb.directory(dir);
        context.setProgress(0);
        Process process = pb.start();
        Thread t1 = pipe(process.getInputStream(), context.getOutputStream());
        Thread t2 = pipe(process.getErrorStream(), context.getErrorStream());
        while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
            if (context.isCancelled()) {
                process.destroyForcibly();
                t1.join();
                t2.join();
                return;
            }
        }
        t1.join();
        t2.join();
        int exit = process.exitValue();
        if (exit != 0) throw new Exception("Process exited with code " + exit);
        context.setProgress(100);
    }

    private static Thread pipe(InputStream in, OutputStream out) {
        Thread t = new Thread(() -> {
            try { in.transferTo(out); } catch (IOException ignored) {}
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
