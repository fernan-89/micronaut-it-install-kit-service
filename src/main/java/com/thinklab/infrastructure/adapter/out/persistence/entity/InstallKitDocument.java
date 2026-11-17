package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.model.InstallKit.Family;
import com.thinklab.domain.model.InstallKit.Language;
import com.thinklab.domain.model.InstallKit.Role;
import com.thinklab.domain.model.InstallKit.InstallKitAuditEntry;
import com.thinklab.domain.model.InstallKit.InstallKitStatus;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific representation of the InstallKit Aggregate for MongoDB. */
@Introspected
public class InstallKitDocument {

    @BsonId
    private UUID id;
    private UUID organisationId;
    private String name;
    private String family;
    private String role;
    private String language;
    private String systemId;
    private String machineClass;
    private int revision;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFamily() { return family; }
    public void setFamily(String family) { this.family = family; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getSystemId() { return systemId; }
    public void setSystemId(String systemId) { this.systemId = systemId; }
    public String getMachineClass() { return machineClass; }
    public void setMachineClass(String machineClass) { this.machineClass = machineClass; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor, String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(InstallKitAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                    entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch: fromDomain always writes entry.toStatus().name().
        InstallKitAuditEntry toDomain() {
            return new InstallKitAuditEntry(occurredAt, action, executor, fromStatus != null ? InstallKitStatus.valueOf(fromStatus) : null, InstallKitStatus.valueOf(toStatus), detail);
        }
    }

    public static final class InstallKitPersistenceMapper {

        private InstallKitPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static InstallKitDocument toDocument(InstallKit kit) {
            InstallKitDocument doc = new InstallKitDocument();
            doc.setId(kit.getId());
            doc.setOrganisationId(kit.getOrganisationId());
            doc.setName(kit.getName());
            doc.setFamily(kit.getFamily().name());
            doc.setRole(kit.getRole().name());
            doc.setLanguage(kit.getLanguage().name());
            doc.setSystemId(kit.getSystemId());
            doc.setMachineClass(kit.getMachineClass());
            doc.setRevision(kit.getRevision());
            doc.setStatus(kit.getStatus().name());
            doc.setCreatedAt(kit.getCreatedAt());
            doc.setUpdatedAt(kit.getUpdatedAt());
            doc.setAuditTrail(kit.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static InstallKit toDomain(InstallKitDocument doc) {
            return InstallKit.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getName(), Family.valueOf(doc.getFamily()), Role.valueOf(doc.getRole()), Language.valueOf(doc.getLanguage()), doc.getSystemId(),
                    doc.getMachineClass(), doc.getRevision(), InstallKitStatus.valueOf(doc.getStatus()),
                    doc.getCreatedAt(), doc.getUpdatedAt(), doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()));
        }
    }
}
