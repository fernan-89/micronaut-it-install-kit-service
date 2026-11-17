package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.DuplicateInstallKitException;
import com.thinklab.domain.exception.InvalidInstallKitStatusException;
import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.model.InstallKit.Family;
import com.thinklab.domain.model.InstallKit.InstallKitStatus;
import com.thinklab.domain.model.InstallKit.Language;
import com.thinklab.domain.model.InstallKit.Role;
import com.thinklab.domain.model.InstallReport;
import com.thinklab.domain.model.InstallReport.Outcome;
import com.thinklab.domain.repository.InstallKitRepository;
import com.thinklab.domain.repository.InstallReportRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The install kit persistence against a real MongoDB: the unique kit name, the status-guarded saves that keep the revision, the reports
 * read back newest first and only for their own organisation, and the indexes created at startup.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InstallKitPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "install_kit_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject InstallKitRepository kits;
    @Inject InstallReportRepository reports;
    @Inject MongoClient mongoClient;

    private InstallKit newKit(UUID organisation, String name) {
        return InstallKit.createNew(UUID.randomUUID(), organisation, name, Family.WINDOWS, Role.WORKSTATION, Language.PT, "W11", "BASE", "op-1");
    }

    @Test
    @DisplayName("a kit is read back whole, only for its own organisation, oldest first; the name is unique per organisation")
    void kitRoundTrip() {
        UUID organisation = UUID.randomUUID();
        InstallKit first = kits.create(newKit(organisation, "First")).block();
        kits.create(newKit(organisation, "Second")).block();

        InstallKit found = kits.findById(first.getId(), organisation).block();

        assertEquals("First", found.getName());
        assertEquals(Family.WINDOWS, found.getFamily());
        assertEquals(Role.WORKSTATION, found.getRole());
        assertEquals(Language.PT, found.getLanguage());
        assertEquals("W11", found.getSystemId());
        assertEquals("BASE", found.getMachineClass());
        assertEquals(1, found.getRevision());
        assertEquals(InstallKitStatus.ACTIVE, found.getStatus());
        assertEquals(1, found.getAuditTrail().size());
        assertNull(kits.findById(first.getId(), UUID.randomUUID()).block());
        assertEquals(List.of("First", "Second"), kits.findAll(organisation).map(InstallKit::getName).collectList().block());
        assertThrows(DuplicateInstallKitException.class, () -> kits.create(newKit(organisation, "First")).block());
        kits.create(newKit(UUID.randomUUID(), "First")).block();
    }

    @Test
    @DisplayName("save is guarded by the status loaded and keeps the revision and the audit trail; a lost race is a conflict")
    void guardedSave() {
        UUID organisation = UUID.randomUUID();
        InstallKit kit = kits.create(newKit(organisation, "Guarded")).block();
        InstallKit loaded = kits.findById(kit.getId(), organisation).block();
        var statusBefore = loaded.getStatus();

        var updated = loaded.update("Guarded EN", Family.WINDOWS, Role.WORKSTATION, Language.EN, "W11", "P520", "op-2");
        kits.save(loaded, statusBefore, updated).block();
        var retired = loaded.retire("op-2");
        kits.save(loaded, statusBefore, retired).block();

        InstallKit back = kits.findById(kit.getId(), organisation).block();
        assertEquals("Guarded EN", back.getName());
        assertEquals(Language.EN, back.getLanguage());
        assertEquals("P520", back.getMachineClass());
        assertEquals(2, back.getRevision());
        assertEquals(InstallKitStatus.RETIRED, back.getStatus());
        assertEquals(3, back.getAuditTrail().size());

        InstallKit stale = newKit(organisation, "Stale");
        kits.create(stale).block();
        InstallKit other = kits.findById(stale.getId(), organisation).block();
        var entry = other.retire("op-3");
        kits.save(other, InstallKitStatus.ACTIVE, entry).block();
        assertThrows(InvalidInstallKitStatusException.class, () -> kits.save(other, InstallKitStatus.ACTIVE, entry).block());
    }

    @Test
    @DisplayName("reports are read back whole, newest first and only for their own organisation")
    void reports() {
        UUID organisation = UUID.randomUUID();
        UUID kitId = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        InstallReport older = InstallReport.createNew(UUID.randomUUID(), organisation, kitId, 1, "1.2.2", Outcome.PARTIAL, 2, asset, Instant.parse("2026-11-16T10:00:00Z"), "op-1");
        InstallReport newer = InstallReport.createNew(UUID.randomUUID(), organisation, kitId, 2, "1.2.3", Outcome.SUCCEEDED, 0, null, Instant.parse("2026-11-17T10:00:00Z"), "op-1");
        reports.create(older).block();
        reports.create(newer).block();

        InstallReport found = reports.findById(older.getId(), organisation).block();

        assertEquals(Outcome.PARTIAL, found.getOutcome());
        assertEquals(asset, found.getAssetId());
        assertEquals(Instant.parse("2026-11-16T10:00:00Z"), found.getOccurredAt());
        assertNull(reports.findById(older.getId(), UUID.randomUUID()).block());
        assertEquals(List.of(newer.getId(), older.getId()), reports.findAll(organisation).map(InstallReport::getId).collectList().block());
    }

    @Test
    @DisplayName("the indexes exist after startup: the unique kit name, the status list and the two report indexes")
    void indexes() {
        List<String> kitIndexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("install_kits").listIndexes()).map(index -> index.getString("name")).collectList().block();
        List<String> reportIndexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("install_reports").listIndexes()).map(index -> index.getString("name")).collectList().block();

        assertTrue(kitIndexes.contains("organisationId_1_name_1"));
        assertTrue(kitIndexes.contains("organisationId_1_status_1"));
        assertTrue(reportIndexes.contains("organisationId_1_kitId_1_occurredAt_-1"));
        assertTrue(reportIndexes.contains("organisationId_1_occurredAt_-1"));
        Document unique = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("install_kits").listIndexes()).filter(index -> "organisationId_1_name_1".equals(index.getString("name"))).blockFirst();
        assertEquals(Boolean.TRUE, unique.getBoolean("unique"));
    }
}
