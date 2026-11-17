package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.InstallKitResponse;
import com.thinklab.application.mapper.InstallKitMapper;
import com.thinklab.domain.model.InstallKit.Family;
import com.thinklab.domain.model.InstallKit.InstallKitStatus;
import com.thinklab.domain.repository.InstallKitRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for listing the tenant's kits, oldest first, filterable by status and family (BIAN Behavior Qualifier: {@code retrieve}, collection). */
@Singleton
public class RetrieveInstallKitsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveInstallKitsUseCase.class);

    private final InstallKitRepository repository;

    public RetrieveInstallKitsUseCase(InstallKitRepository repository) {
        this.repository = repository;
    }

    public Flux<InstallKitResponse> execute(UUID organisationId, InstallKitStatus status, Family family, String role) {
        log.info("[USE CASE] Retrieving the InstallKits of organisation: {}", organisationId);
        return Mono.fromRunnable(() -> InstallKitAccess.requireStaff(role, "list kits"))
                .thenMany(Flux.defer(() -> repository.findAll(organisationId)
                        .filter(kit -> status == null || kit.getStatus() == status)
                        .filter(kit -> family == null || kit.getFamily() == family)
                        .map(InstallKitMapper::toResponse)));
    }
}
