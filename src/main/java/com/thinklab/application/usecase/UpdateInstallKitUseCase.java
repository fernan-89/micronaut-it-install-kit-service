package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateInstallKitRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for changing a kit (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateInstallKitUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateInstallKitUseCase.class);

    private final InstallKitWorkflow workflow;

    public UpdateInstallKitUseCase(InstallKitWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateInstallKitRequest request, String executor, String role) {
        log.info("[USE CASE] Updating InstallKit ID: {}", id);
        return workflow.apply(id, organisationId, role, "change a kit", kit -> kit.update(request.name(), request.family(), request.role(), request.language(), request.systemId(), request.machineClass(), executor));
    }
}
