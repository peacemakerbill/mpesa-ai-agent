package com.darajaagentx.api.devtunnel;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Finds a program on the PATH or in the folder the tunnel tools are installed to. */
public final class ToolLocator {

    private final Platform platform;
    private final List<Path> folders;

    public ToolLocator(Platform platform, List<Path> folders) {
        this.platform = platform;
        this.folders = List.copyOf(folders);
    }

    /** The PATH of this process, then the install folder. */
    public static ToolLocator system(Platform platform, Path installFolder) {
        List<Path> all = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String part : path.split(File.pathSeparator)) {
                if (!part.isBlank()) {
                    try {
                        all.add(Path.of(part.strip()));
                    } catch (RuntimeException ignored) {
                        // a PATH entry this system cannot read as a folder
                    }
                }
            }
        }
        all.add(installFolder);
        return new ToolLocator(platform, all);
    }

    public Optional<Path> find(String program) {
        String file = platform.exe(program);
        for (Path folder : folders) {
            Path candidate = folder.resolve(file);
            if (Files.isRegularFile(candidate) && (platform.windows() || Files.isExecutable(candidate))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
