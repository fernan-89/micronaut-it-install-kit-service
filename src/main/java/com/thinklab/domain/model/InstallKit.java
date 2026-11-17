package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidInstallKitStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Aggregate Root (BIAN Control Record) of the IT Install Kit Service Domain: one unattended-install recipe of the Quick Install add-on, that is, which
 * system ({@code systemId}) is installed, in which {@link Language}, for which class of machine ({@code machineClass}, {@code BASE} for the generic kit).
 * It holds no machine name, serial number, product key or credential (ADR-030): those stay on the owner's own stick. A kit is never deleted; one that
 * is no longer wanted is retired and no longer appears in the stick definition.
 *
 * <p>Platform rule carried from the add-on: a {@link Role#SERVER} kit is English only; every other kit may be English or Portuguese.
 */
public class InstallKit {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_CODE_LENGTH = 40;
    /** A system id or a machine class is a short code, never a sentence. */
    public static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_CODE_LENGTH + "}");

    private final UUID id;
    private final UUID organisationId;
    private String name;
    private Family family;
    private Role role;
    private Language language;
    private String systemId;
    private String machineClass;
    private int revision;
    private InstallKitStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<InstallKitAuditEntry> auditTrail;

    private InstallKit(UUID id, UUID organisationId, String name, Family family, Role role, Language language, String systemId, String machineClass, String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.family = family;
        this.role = role;
        this.language = language;
        this.systemId = systemId;
        this.machineClass = machineClass;
        this.revision = 1;
        this.status = InstallKitStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new InstallKitAuditEntry(this.createdAt, "INITIATED", executor, null, InstallKitStatus.ACTIVE,
                "Kit " + systemId + " / " + language + " / " + machineClass + "."));
    }

    private InstallKit(UUID id, UUID organisationId, String name, Family family, Role role, Language language, String systemId, String machineClass, int revision,
                       InstallKitStatus status, Instant createdAt, Instant updatedAt, List<InstallKitAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.family = family;
        this.role = role;
        this.language = language;
        this.systemId = systemId;
        this.machineClass = machineClass;
        this.revision = Math.max(revision, 1);
        this.status = status != null ? status : InstallKitStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static InstallKit createNew(UUID id, UUID organisationId, String name, Family family, Role role, Language language, String systemId, String machineClass,
                                       String executor) {
        if (id == null || organisationId == null) {
            throw new IllegalArgumentException("ID and Organisation ID are mandatory for InstallKit creation.");
        }
        validate(name, family, role, language, systemId, machineClass);
        requireExecutor(executor);
        return new InstallKit(id, organisationId, name, family, role, language, systemId, machineClass, executor);
    }

    public static InstallKit reconstitute(UUID id, UUID organisationId, String name, Family family, Role role, Language language, String systemId, String machineClass,
                                          int revision, InstallKitStatus status, Instant createdAt, Instant updatedAt, List<InstallKitAuditEntry> auditTrail) {
        if (id == null || organisationId == null || name == null || family == null || role == null || language == null || systemId == null || machineClass == null) {
            throw new IllegalArgumentException("Every defining field is mandatory to reconstitute an InstallKit.");
        }
        return new InstallKit(id, organisationId, name, family, role, language, systemId, machineClass, revision, status, createdAt, updatedAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /** Behavior Qualifier: {@code update}. The whole definition again, in any status; the revision goes up so a report says which version of the kit ran. */
    public InstallKitAuditEntry update(String newName, Family newFamily, Role newRole, Language newLanguage, String newSystemId, String newMachineClass, String executor) {
        validate(newName, newFamily, newRole, newLanguage, newSystemId, newMachineClass);
        requireExecutor(executor);
        this.name = newName;
        this.family = newFamily;
        this.role = newRole;
        this.language = newLanguage;
        this.systemId = newSystemId;
        this.machineClass = newMachineClass;
        this.revision++;
        return record("UPDATED", executor, this.status, this.status, "Kit updated to revision " + this.revision + ".");
    }

    /** Behavior Qualifier: {@code control/retire}. ACTIVE -&gt; RETIRED: the kit leaves the stick definition. */
    public InstallKitAuditEntry retire(String executor) {
        requireStatus(InstallKitStatus.ACTIVE);
        requireExecutor(executor);
        this.status = InstallKitStatus.RETIRED;
        return record("RETIRED", executor, InstallKitStatus.ACTIVE, InstallKitStatus.RETIRED, "Retired: the kit is no longer part of the stick definition.");
    }

    /** Behavior Qualifier: {@code control/reinstate}. RETIRED -&gt; ACTIVE. */
    public InstallKitAuditEntry reinstate(String executor) {
        requireStatus(InstallKitStatus.RETIRED);
        requireExecutor(executor);
        this.status = InstallKitStatus.ACTIVE;
        return record("REINSTATED", executor, InstallKitStatus.RETIRED, InstallKitStatus.ACTIVE, "Reinstated.");
    }

    // --- Internal helpers ---

    private InstallKitAuditEntry record(String action, String executor, InstallKitStatus from, InstallKitStatus to, String detail) {
        this.updatedAt = Instant.now();
        InstallKitAuditEntry entry = new InstallKitAuditEntry(this.updatedAt, action, executor, from, to, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(InstallKitStatus expected) {
        if (this.status != expected) {
            throw new InvalidInstallKitStatusException(String.format("Illegal transition: InstallKit is [%s], expected [%s].", this.status, expected));
        }
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable InstallKit mutations.");
        }
    }

    private static void validate(String name, Family family, Role role, Language language, String systemId, String machineClass) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Name is mandatory for an InstallKit (up to " + MAX_NAME_LENGTH + " characters).");
        }
        if (family == null || role == null || language == null) {
            throw new IllegalArgumentException("The family, the role and the language of the kit are mandatory.");
        }
        if (systemId == null || !CODE_PATTERN.matcher(systemId).matches()) {
            throw new IllegalArgumentException("The system id is a short code (letters, digits, dot, dash, underscore; up to " + MAX_CODE_LENGTH + ").");
        }
        if (machineClass == null || !CODE_PATTERN.matcher(machineClass).matches()) {
            throw new IllegalArgumentException("The machine class is a short code (letters, digits, dot, dash, underscore; up to " + MAX_CODE_LENGTH + "); BASE is the generic kit.");
        }
        if (role == Role.SERVER && language != Language.EN) {
            throw new IllegalArgumentException("Server kits are English only.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getName() { return name; }
    public Family getFamily() { return family; }
    public Role getRole() { return role; }
    public Language getLanguage() { return language; }
    public String getSystemId() { return systemId; }
    public String getMachineClass() { return machineClass; }
    public int getRevision() { return revision; }
    public InstallKitStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<InstallKitAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    public enum InstallKitStatus { ACTIVE, RETIRED }

    /** What the kit installs. */
    public enum Family { WINDOWS, LINUX, RECOVERY }

    /** Who the install is for; a server kit is English only. */
    public enum Role { WORKSTATION, SERVER, TOOL }

    /** The installed language: English (EN-US) or Brazilian Portuguese (PT-BR). */
    public enum Language { EN, PT }

    public record InstallKitAuditEntry(Instant occurredAt, String action, String executor, InstallKitStatus fromStatus, InstallKitStatus toStatus, String detail) {}
}
