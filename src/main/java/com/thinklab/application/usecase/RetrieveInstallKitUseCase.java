package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.InstallKitResponse;
import com.thinklab.application.mapper.InstallKitMapper;
import com.thinklab.domain.exception.InstallKitNotFoundException;
import com.thinklab.domain.repository.InstallKitRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for reading one kit of the tenant (BIAN Behavior Qualifier: {@code retrieve}). Staff only; another tenant is a 404. */
@Singleton
public class RetrieveInstallKitUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveInstallKitUseCase.class);

    private final InstallKitRepository repository;

    public RetrieveInstallKitUseCase(InstallKitRepository repository) {
        this.repository = repository;
    }

    public Mono<InstallKitResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving InstallKit ID: {}", id);
        return Mono.fromRunnable(() -> InstallKitAccess.requireStaff(role, "read a kit"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new InstallKitNotFoundException("InstallKit " + id + " not found.")))
                .map(InstallKitMapper::toResponse);
    }
}
