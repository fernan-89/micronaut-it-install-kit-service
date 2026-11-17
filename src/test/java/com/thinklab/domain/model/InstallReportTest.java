package com.thinklab.domain.model;

import com.thinklab.domain.model.InstallReport.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstallReportTest {

    private final UUID org = UUID.randomUUID();
    private final UUID kit = UUID.randomUUID();
    private final Instant when = Instant.parse("2026-11-17T12:00:00Z");

    private static void rejects(Executable action) {
        assertThrows(IllegalArgumentException.class, action);
    }

    @Test
    @DisplayName("a new report keeps the kit, its revision, the add-on version, the outcome, the warnings, the optional asset and who reported it")
    void createNew() {
        UUID asset = UUID.randomUUID();
        InstallReport report = InstallReport.createNew(UUID.randomUUID(), org, kit, 3, "1.2.2", Outcome.PARTIAL, 2, asset, when, "op");

        assertEquals(kit, report.getKitId());
        assertEquals(3, report.getKitRevision());
        assertEquals("1.2.2", report.getAddOnVersion());
        assertEquals(Outcome.PARTIAL, report.getOutcome());
        assertEquals(2, report.getWarnings());
        assertEquals(asset, report.getAssetId());
        assertEquals(when, report.getOccurredAt());
        assertEquals("op", report.getReportedBy());
        assertEquals(org, report.getOrganisationId());
        assertNull(InstallReport.createNew(UUID.randomUUID(), org, kit, 1, "1.2.2-rc1", Outcome.SUCCEEDED, 0, null, when, "op").getAssetId());
    }

    @Test
    @DisplayName("a report needs identities, a revision from 1, a version number, an outcome, 0 to 10000 warnings, a moment and an executor")
    void validation() {
        UUID id = UUID.randomUUID();
        rejects(() -> InstallReport.createNew(null, org, kit, 1, "1.2.2", Outcome.FAILED, 0, null, when, "op"));
        rejects(() -> InstallReport.createNew(id, null, kit, 1, "1.2.2", Outcome.FAILED, 0, null, when, "op"));
        rejects(() -> InstallReport.createNew(id, org, null, 1, "1.2.2", Outcome.FAILED, 0, null, when, "op"));
        rejects(() -> InstallReport.createNew(id, org, kit, 0, "1.2.2", Outcome.FAILED, 0, null, when, "op"));
        rejects(() -> InstallReport.createNew(id, org, kit, 1, null, Outcome.FAILED, 0, null, when, "op"));
        rejects(() -> InstallReport.createNew(id, org, kit, 1, "v1", Outcome.FAILED, 0, null, when, "op"));
        rejects(() -> InstallReport.createNew(id, org, kit, 1, "1.2.2", null, 0, null, when, "op"));
        rejects(() -> InstallReport.createNew(id, org, kit, 1, "1.2.2", Outcome.FAILED, -1, null, when, "op"));
        rejects(() -> InstallReport.createNew(id, org, kit, 1, "1.2.2", Outcome.FAILED, 10_001, null, when, "op"));
        rejects(() -> InstallReport.createNew(id, org, kit, 1, "1.2.2", Outcome.FAILED, 0, null, null, "op"));
        rejects(() -> InstallReport.createNew(id, org, kit, 1, "1.2.2", Outcome.FAILED, 0, null, when, null));
        rejects(() -> InstallReport.createNew(id, org, kit, 1, "1.2.2", Outcome.FAILED, 0, null, when, " "));
        assertEquals(10_000, InstallReport.createNew(id, org, kit, 1, "1.2.2", Outcome.FAILED, 10_000, null, when, "op").getWarnings());
    }

    @Test
    @DisplayName("reconstitute keeps what was stored and refuses a missing mandatory field")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        Instant created = when.plusSeconds(9);
        InstallReport report = InstallReport.reconstitute(id, org, kit, 2, "1.2.2", Outcome.SUCCEEDED, 0, null, when, created, "op");
        assertEquals(created, report.getCreatedAt());
        assertEquals(id, report.getId());
        rejects(() -> InstallReport.reconstitute(null, org, kit, 2, "1.2.2", Outcome.SUCCEEDED, 0, null, when, created, "op"));
        rejects(() -> InstallReport.reconstitute(id, null, kit, 2, "1.2.2", Outcome.SUCCEEDED, 0, null, when, created, "op"));
        rejects(() -> InstallReport.reconstitute(id, org, null, 2, "1.2.2", Outcome.SUCCEEDED, 0, null, when, created, "op"));
        rejects(() -> InstallReport.reconstitute(id, org, kit, 2, null, Outcome.SUCCEEDED, 0, null, when, created, "op"));
        rejects(() -> InstallReport.reconstitute(id, org, kit, 2, "1.2.2", null, 0, null, when, created, "op"));
        rejects(() -> InstallReport.reconstitute(id, org, kit, 2, "1.2.2", Outcome.SUCCEEDED, 0, null, null, created, "op"));
        rejects(() -> InstallReport.reconstitute(id, org, kit, 2, "1.2.2", Outcome.SUCCEEDED, 0, null, when, null, "op"));
        rejects(() -> InstallReport.reconstitute(id, org, kit, 2, "1.2.2", Outcome.SUCCEEDED, 0, null, when, created, null));
    }
}
