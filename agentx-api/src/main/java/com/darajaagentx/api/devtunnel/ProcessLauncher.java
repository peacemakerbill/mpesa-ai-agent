package com.darajaagentx.api.devtunnel;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Starts real programs on Linux, macOS and Windows. */
public final class ProcessLauncher implements Launcher {

    private static ProcessBuilder builder(List<String> command, Map<String, String> extraEnvironment, Path log) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(extraEnvironment);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(log.toFile()));
        return pb;
    }

    @Override
    public Running start(List<String> command, Map<String, String> extraEnvironment, Path log) throws IOException {
        Process process = builder(command, extraEnvironment, log).start();
        return new Running() {
            @Override
            public boolean alive() {
                return process.isAlive();
            }

            @Override
            public void stop() {
                // the program may have started helpers of its own
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
            }
        };
    }

    @Override
    public int runToEnd(List<String> command, Map<String, String> extraEnvironment, Path log, Duration timeout) throws IOException, InterruptedException {
        Process process = builder(command, extraEnvironment, log).start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return -1;
        }
        return process.exitValue();
    }
}
