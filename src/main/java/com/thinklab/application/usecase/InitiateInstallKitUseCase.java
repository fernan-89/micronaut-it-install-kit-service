package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateInstallKitRequest;
import com.thinklab.application.dto.response.InstallKitResponse;
import com.thinklab.application.mapper.InstallKitMapper;
import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.InstallKitRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for registering a kit (BIAN Behavior Qualifier: {@code initiate}). Staff only. */
@Singleton
public class InitiateInstallKitUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateInstallKitUseCase.class);

    private final HashServicePort hashServicePort;
    private final InstallKitRepository repository;

    public InitiateInstallKitUseCase(HashServicePort hashServicePort, InstallKitRepository repository) {
        this.hashServicePort = hashServicePort;
        this.repository = repository;
    }

    public Mono<InstallKitResponse> execute(UUID organisationId, InitiateInstallKitRequest request, String executor, String role) {
        log.info("[USE CASE] Creating the InstallKit [{}] for organisation: {}", request.name(), organisationId);
        return Mono.fromRunnable(() -> InstallKitAccess.requireStaff(role, "register a kit"))
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("kit-creation")))
                .map(id -> InstallKit.createNew(id, organisationId, request.name(), request.family(), request.role(), request.language(), request.systemId(), request.machineClass(), executor))
                .flatMap(repository::create)
                .map(InstallKitMapper::toResponse);
    }
}
