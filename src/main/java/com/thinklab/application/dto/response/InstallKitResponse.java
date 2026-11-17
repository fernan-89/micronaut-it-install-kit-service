package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record InstallKitResponse(UUID id, UUID organisationId, String name, String family, String role, String language, String systemId, String machineClass, int revision,
                                 String status, Instant createdAt, Instant updatedAt) {}
