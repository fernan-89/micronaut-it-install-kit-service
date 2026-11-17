package com.thinklab.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Secondary aggregate of the IT Install Kit Service Domain: what happened when a kit was used on a machine. A report is a fact and is never edited or
 * deleted: it names the kit and its revision, the version of the add-on that ran, the {@link Outcome}, how many warnings the install logged and,
 * optionally, the asset the machine became. It carries no hostname, serial number or credential (ADR-030): the asset id is the only link to a machine.
 */
public class InstallReport {

    public static final int MAX_WARNINGS = 10_000;
    public static final int MAX_VERSION_LENGTH = 20;
    public static final Pattern VERSION_PATTERN = Pattern.compile("[0-9]{1,4}(\\.[0-9]{1,4}){0,3}(-[A-Za-z0-9.]{1,10})?");

    private final UUID id;
    private final UUID organisationId;
    private final UUID kitId;
    private final int kitRevision;
    private final String addOnVersion;
    private final Outcome outcome;
    private final int warnings;
    private final UUID assetId;
    private final Instant occurredAt;
    private final Instant createdAt;
    private final String reportedBy;

    private InstallReport(UUID id, UUID organisationId, UUID kitId, int kitRevision, String addOnVersion, Outcome outcome, int warnings, UUID assetId, Instant occurredAt,
                          Instant createdAt, String reportedBy) {
        this.id = id;
        this.organisationId = organisationId;
        this.kitId = kitId;
        this.kitRevision = kitRevision;
        this.addOnVersion = addOnVersion;
        this.outcome = outcome;
        this.warnings = warnings;
        this.assetId = assetId;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.reportedBy = reportedBy;
    }

    public static InstallReport createNew(UUID id, UUID organisationId, UUID kitId, int kitRevision, String addOnVersion, Outcome outcome, int warnings, UUID assetId,
                                          Instant occurredAt, String executor) {
        if (id == null || organisationId == null || kitId == null) {
            throw new IllegalArgumentException("ID, Organisation ID and InstallKit ID are mandatory for InstallReport creation.");
        }
        if (kitRevision < 1) {
            throw new IllegalArgumentException("The revision of the kit that ran starts at 1.");
        }
        if (addOnVersion == null || !VERSION_PATTERN.matcher(addOnVersion).matches()) {
            throw new IllegalArgumentException("The add-on version is a version number such as 1.2.2.");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("The outcome of the install is mandatory.");
        }
        if (warnings < 0 || warnings > MAX_WARNINGS) {
            throw new IllegalArgumentException("Warnings is a count from 0 to " + MAX_WARNINGS + ".");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("The moment the install ran is mandatory.");
        }
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable InstallReport mutations.");
        }
        return new InstallReport(id, organisationId, kitId, kitRevision, addOnVersion, outcome, warnings, assetId, occurredAt, Instant.now(), executor);
    }

    public static InstallReport reconstitute(UUID id, UUID organisationId, UUID kitId, int kitRevision, String addOnVersion, Outcome outcome, int warnings, UUID assetId,
                                             Instant occurredAt, Instant createdAt, String reportedBy) {
        if (id == null || organisationId == null || kitId == null || addOnVersion == null || outcome == null || occurredAt == null || createdAt == null || reportedBy == null) {
            throw new IllegalArgumentException("Every mandatory field is needed to reconstitute an InstallReport.");
        }
        return new InstallReport(id, organisationId, kitId, kitRevision, addOnVersion, outcome, warnings, assetId, occurredAt, createdAt, reportedBy);
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getKitId() { return kitId; }
    public int getKitRevision() { return kitRevision; }
    public String getAddOnVersion() { return addOnVersion; }
    public Outcome getOutcome() { return outcome; }
    public int getWarnings() { return warnings; }
    public UUID getAssetId() { return assetId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getReportedBy() { return reportedBy; }

    /** How the install ended: everything done, done with a step that needs a person, or stopped. */
    public enum Outcome { SUCCEEDED, PARTIAL, FAILED }
}
