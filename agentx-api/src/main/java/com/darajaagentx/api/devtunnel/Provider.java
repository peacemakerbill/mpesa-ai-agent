package com.darajaagentx.api.devtunnel;

public enum Provider {
    ZROK("zrok2"),
    NGROK("ngrok");

    private final String program;

    Provider(String program) {
        this.program = program;
    }

    /** The program's name without .exe */
    public String program() {
        return program;
    }
}
