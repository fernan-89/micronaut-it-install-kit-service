package com.thinklab.application.usecase;

import com.thinklab.domain.exception.InstallKitNotFoundException;
import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.model.InstallKit.InstallKitAuditEntry;
import com.thinklab.domain.repository.InstallKitRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/** The shape every staff change to a kit shares: staff only, load (a 404 if it is not the tenant's), apply, save with the status loaded. */
@Singleton
public class InstallKitWorkflow {

    private final InstallKitRepository repository;

    public InstallKitWorkflow(InstallKitRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> apply(UUID id, UUID organisationId, String role, String operation, Function<InstallKit, InstallKitAuditEntry> action) {
        return Mono.fromRunnable(() -> InstallKitAccess.requireStaff(role, operation))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new InstallKitNotFoundException("InstallKit " + id + " not found.")))
                .flatMap(kit -> {
                    var statusBefore = kit.getStatus();
                    InstallKitAuditEntry entry = action.apply(kit);
                    return repository.save(kit, statusBefore, entry);
                });
    }
}
