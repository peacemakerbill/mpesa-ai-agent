package com.darajaagentx.api.devtunnel;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Starts programs. The real one uses the operating system, the tests use a pretend one. */
public interface Launcher {

    interface Running {
        boolean alive();

        void stop();
    }

    /** Starts a program that keeps running. Everything it prints goes to the log file. */
    Running start(List<String> command, Map<String, String> extraEnvironment, Path log) throws IOException;

    /** Runs a program until it ends. Returns its exit code, or -1 when it did not end in time. */
    int runToEnd(List<String> command, Map<String, String> extraEnvironment, Path log, Duration timeout) throws IOException, InterruptedException;
}
