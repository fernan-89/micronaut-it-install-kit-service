package com.thinklab.application.dto.request;

import com.thinklab.domain.model.InstallReport.Outcome;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** DTO for {@code install-report/initiate}: which kit ran, with what result and when. No hostname, serial number or credential (ADR-030). */
@Serdeable
public record InitiateInstallReportRequest(
        @NotNull(message = "The kit is required") UUID kitId,
        @NotBlank(message = "The add-on version is required") String addOnVersion,
        @NotNull(message = "The outcome is required") Outcome outcome,
        @Min(value = 0, message = "Warnings cannot be negative") @Max(value = 10000, message = "Warnings cannot exceed 10000") int warnings,
        @Nullable UUID assetId,
        @NotNull(message = "The moment the install ran is required") Instant occurredAt
) {}
