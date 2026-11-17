package com.thinklab.application.dto.response;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record InstallReportResponse(UUID id, UUID organisationId, UUID kitId, int kitRevision, String addOnVersion, String outcome, int warnings, @Nullable UUID assetId,
                                    Instant occurredAt, Instant createdAt, String reportedBy) {}
