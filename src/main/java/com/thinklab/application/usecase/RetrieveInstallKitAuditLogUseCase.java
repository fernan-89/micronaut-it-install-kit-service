package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.InstallKitMapper;
import com.thinklab.domain.exception.InstallKitNotFoundException;
import com.thinklab.domain.repository.InstallKitRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use Case for the forensic ledger of a kit (BIAN Behavior Qualifier: {@code audit-log/retrieve}). Staff only. */
@Singleton
public class RetrieveInstallKitAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveInstallKitAuditLogUseCase.class);

    private final InstallKitRepository repository;

    public RetrieveInstallKitAuditLogUseCase(InstallKitRepository repository) {
        this.repository = repository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the audit log of InstallKit ID: {}", id);
        return Mono.fromRunnable(() -> InstallKitAccess.requireStaff(role, "read the audit trail of a kit"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new InstallKitNotFoundException("InstallKit " + id + " not found.")))
                .map(kit -> kit.getAuditTrail().stream().map(InstallKitMapper::toResponse).collect(Collectors.toList()));
    }
}
