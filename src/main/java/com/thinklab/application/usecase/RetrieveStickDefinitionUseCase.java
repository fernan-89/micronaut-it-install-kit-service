package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.StickDefinitionResponse;
import com.thinklab.application.mapper.InstallKitMapper;
import com.thinklab.domain.repository.InstallKitRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for the definition of the install stick: the organisation's active kits grouped by system (BIAN Behavior Qualifier: {@code stick-definition/retrieve}). Staff only. */
@Singleton
public class RetrieveStickDefinitionUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveStickDefinitionUseCase.class);

    private final InstallKitRepository repository;

    public RetrieveStickDefinitionUseCase(InstallKitRepository repository) {
        this.repository = repository;
    }

    public Mono<StickDefinitionResponse> execute(UUID organisationId, String role) {
        log.info("[USE CASE] Building the stick definition of organisation: {}", organisationId);
        return Mono.fromRunnable(() -> InstallKitAccess.requireStaff(role, "read the stick definition"))
                .then(Mono.defer(() -> repository.findAll(organisationId).collectList()))
                .map(kits -> InstallKitMapper.toStickDefinition(organisationId, kits));
    }
}
