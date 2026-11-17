package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.InstallReport;
import com.thinklab.domain.model.InstallReport.Outcome;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.UUID;

/** Infrastructure-specific representation of the InstallReport Aggregate for MongoDB. */
@Introspected
public class InstallReportDocument {

    @BsonId
    private UUID id;
    private UUID organisationId;
    private UUID kitId;
    private int kitRevision;
    private String addOnVersion;
    private String outcome;
    private int warnings;
    private UUID assetId;
    private Instant occurredAt;
    private Instant createdAt;
    private String reportedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public UUID getKitId() { return kitId; }
    public void setKitId(UUID kitId) { this.kitId = kitId; }
    public int getKitRevision() { return kitRevision; }
    public void setKitRevision(int kitRevision) { this.kitRevision = kitRevision; }
    public String getAddOnVersion() { return addOnVersion; }
    public void setAddOnVersion(String addOnVersion) { this.addOnVersion = addOnVersion; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public int getWarnings() { return warnings; }
    public void setWarnings(int warnings) { this.warnings = warnings; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }

    public static final class InstallReportPersistenceMapper {

        private InstallReportPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static InstallReportDocument toDocument(InstallReport report) {
            InstallReportDocument doc = new InstallReportDocument();
            doc.setId(report.getId());
            doc.setOrganisationId(report.getOrganisationId());
            doc.setKitId(report.getKitId());
            doc.setKitRevision(report.getKitRevision());
            doc.setAddOnVersion(report.getAddOnVersion());
            doc.setOutcome(report.getOutcome().name());
            doc.setWarnings(report.getWarnings());
            doc.setAssetId(report.getAssetId());
            doc.setOccurredAt(report.getOccurredAt());
            doc.setCreatedAt(report.getCreatedAt());
            doc.setReportedBy(report.getReportedBy());
            return doc;
        }

        public static InstallReport toDomain(InstallReportDocument doc) {
            return InstallReport.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getKitId(), doc.getKitRevision(), doc.getAddOnVersion(), Outcome.valueOf(doc.getOutcome()),
                    doc.getWarnings(), doc.getAssetId(), doc.getOccurredAt(), doc.getCreatedAt(), doc.getReportedBy());
        }
    }
}
