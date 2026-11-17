package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.InstallKitResponse;
import com.thinklab.application.dto.response.InstallReportResponse;
import com.thinklab.application.dto.response.StickDefinitionResponse;
import com.thinklab.application.dto.response.StickDefinitionResponse.KitEntry;
import com.thinklab.application.dto.response.StickDefinitionResponse.SystemEntry;
import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.model.InstallKit.InstallKitAuditEntry;
import com.thinklab.domain.model.InstallKit.InstallKitStatus;
import com.thinklab.domain.model.InstallReport;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class InstallKitMapper {

    private InstallKitMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static InstallKitResponse toResponse(InstallKit kit) {
        return new InstallKitResponse(kit.getId(), kit.getOrganisationId(), kit.getName(), kit.getFamily().name(), kit.getRole().name(), kit.getLanguage().name(),
                kit.getSystemId(), kit.getMachineClass(), kit.getRevision(), kit.getStatus().name(), kit.getCreatedAt(), kit.getUpdatedAt());
    }

    public static InstallReportResponse toResponse(InstallReport report) {
        return new InstallReportResponse(report.getId(), report.getOrganisationId(), report.getKitId(), report.getKitRevision(), report.getAddOnVersion(),
                report.getOutcome().name(), report.getWarnings(), report.getAssetId(), report.getOccurredAt(), report.getCreatedAt(), report.getReportedBy());
    }

    public static AuditEntryResponse toResponse(InstallKitAuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                entry.toStatus().name(), entry.detail());
    }

    /** The active kits grouped by system id (in the order the systems first appear), each system's kits sorted by language then machine class. */
    public static StickDefinitionResponse toStickDefinition(UUID organisationId, List<InstallKit> kits) {
        List<InstallKit> active = kits.stream().filter(kit -> kit.getStatus() == InstallKitStatus.ACTIVE).toList();
        Map<String, List<InstallKit>> bySystem = active.stream()
                .sorted(Comparator.comparing(InstallKit::getSystemId))
                .collect(Collectors.groupingBy(InstallKit::getSystemId, LinkedHashMap::new, Collectors.toList()));
        List<SystemEntry> systems = bySystem.entrySet().stream().map(entry -> {
            InstallKit first = entry.getValue().get(0);
            List<KitEntry> entries = entry.getValue().stream()
                    .sorted(Comparator.comparing((InstallKit kit) -> kit.getLanguage().name()).thenComparing(InstallKit::getMachineClass))
                    .map(kit -> new KitEntry(kit.getId(), kit.getName(), kit.getLanguage().name(), kit.getMachineClass(), kit.getRevision()))
                    .toList();
            return new SystemEntry(entry.getKey(), first.getFamily().name(), first.getRole().name(), entries);
        }).toList();
        return new StickDefinitionResponse(organisationId, active.size(), systems);
    }
}
