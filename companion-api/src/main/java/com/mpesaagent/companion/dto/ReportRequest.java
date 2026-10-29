package com.mpesaagent.companion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(@NotNull Outcome outcome, @Size(max = 30) String receipt) {

    public enum Outcome {
        DONE, NOT_DONE
    }
}
