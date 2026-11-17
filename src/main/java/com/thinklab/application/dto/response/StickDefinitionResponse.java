package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

/**
 * What a stick builder needs: the organisation's active kits grouped by system, each system with its kits sorted by language then machine class. Pure data derived
 * from the kits; it holds no machine name, key or credential (ADR-030).
 */
@Serdeable
public record StickDefinitionResponse(UUID organisationId, int kitCount, List<SystemEntry> systems) {

    @Serdeable
    public record SystemEntry(String systemId, String family, String role, List<KitEntry> kits) {}

    @Serdeable
    public record KitEntry(UUID kitId, String name, String language, String machineClass, int revision) {}
}
