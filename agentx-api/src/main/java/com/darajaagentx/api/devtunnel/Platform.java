package com.darajaagentx.api.devtunnel;

import java.util.Locale;

/** The operating system and processor, in the words the zrok and ngrok download names use. */
public record Platform(String os, String arch) {

    public static Platform current() {
        return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    public static Platform of(String osName, String archName) {
        String o = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        // darwin contains "win", so macOS is tested first
        String os = o.contains("mac") || o.contains("darwin") ? "darwin" : o.contains("win") ? "windows" : "linux";
        String a = archName == null ? "" : archName.toLowerCase(Locale.ROOT);
        String arch;
        if (a.equals("aarch64") || a.equals("arm64")) {
            arch = "arm64";
        } else if (a.startsWith("arm")) {
            arch = "arm";
        } else if (a.equals("x86") || a.equals("i386") || a.equals("i686")) {
            arch = "386";
        } else {
            arch = "amd64";
        }
        return new Platform(os, arch);
    }

    public boolean windows() {
        return os.equals("windows");
    }

    /** The file name of a program on this system: zrok2 or zrok2.exe. */
    public String exe(String name) {
        return windows() ? name + ".exe" : name;
    }
}
