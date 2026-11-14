package com.darajaagentx.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** The requests a customer chose to clear from their list. */
public record ClearRequest(@NotEmpty @Size(max = 100) List<UUID> ids) {
}
