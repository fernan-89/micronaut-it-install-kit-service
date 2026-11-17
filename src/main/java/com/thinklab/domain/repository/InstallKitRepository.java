package com.thinklab.domain.repository;

import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.model.InstallKit.InstallKitAuditEntry;
import com.thinklab.domain.model.InstallKit.InstallKitStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound Port for InstallKit persistence. {@link #save} is a guarded write on the status loaded (ADR-033); every lookup is tenant-scoped. */
public interface InstallKitRepository {

    /** Inserts the kit. A name already used in the organisation is {@link com.thinklab.domain.exception.DuplicateInstallKitException}: the unique index is the atomic backstop. */
    Mono<InstallKit> create(InstallKit kit);

    Mono<InstallKit> findById(UUID id, UUID organisationId);

    /** The tenant's kits, oldest first. */
    Flux<InstallKit> findAll(UUID organisationId);

    Mono<Void> save(InstallKit kit, InstallKitStatus expectedStatus, InstallKitAuditEntry auditEntry);
}
