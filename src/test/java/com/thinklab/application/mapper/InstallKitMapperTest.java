package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.InstallKitResponse;
import com.thinklab.application.dto.response.InstallReportResponse;
import com.thinklab.application.dto.response.StickDefinitionResponse;
import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.model.InstallKit.Family;
import com.thinklab.domain.model.InstallKit.Language;
import com.thinklab.domain.model.InstallKit.Role;
import com.thinklab.domain.model.InstallReport;
import com.thinklab.domain.model.InstallReport.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstallKitMapperTest {

    private final UUID org = UUID.randomUUID();

    private InstallKit kit(String name, String systemId, Language language, String machineClass) {
        return InstallKit.createNew(UUID.randomUUID(), org, name, Family.WINDOWS, Role.WORKSTATION, language, systemId, machineClass, "op");
    }

    @Test
    @DisplayName("a kit is mapped with its family, role, language, system, machine class, revision and status")
    void kit() {
        InstallKit kit = kit("Windows 11 EN", "W11", Language.EN, "BASE");

        InstallKitResponse response = InstallKitMapper.toResponse(kit);

        assertEquals(kit.getId(), response.id());
        assertEquals("Windows 11 EN", response.name());
        assertEquals("WINDOWS", response.family());
        assertEquals("WORKSTATION", response.role());
        assertEquals("EN", response.language());
        assertEquals("W11", response.systemId());
        assertEquals("BASE", response.machineClass());
        assertEquals(1, response.revision());
        assertEquals("ACTIVE", response.status());
        assertEquals(org, response.organisationId());
    }

    @Test
    @DisplayName("a report is mapped with its outcome as a name and its optional asset")
    void report() {
        UUID kitId = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        Instant when = Instant.parse("2026-11-17T12:00:00Z");
        InstallReport report = InstallReport.createNew(UUID.randomUUID(), org, kitId, 2, "1.2.2", Outcome.PARTIAL, 3, asset, when, "op");
        InstallReport bare = InstallReport.createNew(UUID.randomUUID(), org, kitId, 1, "1.2.2", Outcome.SUCCEEDED, 0, null, when, "op");

        InstallReportResponse response = InstallKitMapper.toResponse(report);

        assertEquals(kitId, response.kitId());
        assertEquals(2, response.kitRevision());
        assertEquals("1.2.2", response.addOnVersion());
        assertEquals("PARTIAL", response.outcome());
        assertEquals(3, response.warnings());
        assertEquals(asset, response.assetId());
        assertEquals(when, response.occurredAt());
        assertEquals("op", response.reportedBy());
        assertNull(InstallKitMapper.toResponse(bare).assetId());
    }

    @Test
    @DisplayName("the audit entries of a kit are mapped, with no earlier status for the first one")
    void auditEntries() {
        InstallKit kit = kit("k", "W11", Language.EN, "BASE");
        kit.retire("op-2");

        AuditEntryResponse first = InstallKitMapper.toResponse(kit.getAuditTrail().get(0));
        AuditEntryResponse second = InstallKitMapper.toResponse(kit.getAuditTrail().get(1));

        assertNull(first.fromStatus());
        assertEquals("ACTIVE", first.toStatus());
        assertEquals("ACTIVE", second.fromStatus());
        assertEquals("RETIRED", second.toStatus());
        assertEquals("op-2", second.executor());
    }

    @Test
    @DisplayName("the stick definition groups the active kits by system and leaves the retired ones out")
    void stickDefinition() {
        InstallKit enBase = kit("W11 EN", "W11", Language.EN, "BASE");
        InstallKit ptBase = kit("W11 PT", "W11", Language.PT, "BASE");
        InstallKit retired = kit("W10", "W10", Language.EN, "BASE");
        retired.retire("op");

        StickDefinitionResponse definition = InstallKitMapper.toStickDefinition(org, List.of(ptBase, retired, enBase));

        assertEquals(org, definition.organisationId());
        assertEquals(2, definition.kitCount());
        assertEquals(1, definition.systems().size());
        assertEquals(List.of("EN", "PT"), definition.systems().get(0).kits().stream().map(StickDefinitionResponse.KitEntry::language).toList());
        assertEquals(0, InstallKitMapper.toStickDefinition(org, List.of()).systems().size());
    }

    @Test
    @DisplayName("the mapper is a utility class that cannot be instantiated")
    void utilityClass() throws Exception {
        Constructor<InstallKitMapper> constructor = InstallKitMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertEquals(UnsupportedOperationException.class, failure.getCause().getClass());
    }
}
