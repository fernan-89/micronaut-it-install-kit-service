package com.thinklab.application.usecase;

import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.model.InstallKit.InstallKitAuditEntry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for deactivating and activating a kit (BIAN Behavior Qualifier: {@code control/*}). */
@Singleton
public class ControlInstallKitUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlInstallKitUseCase.class);

    /** What a person can ask for; each constant says how the aggregate performs it. */
    public enum Action {
        RETIRE {
            @Override InstallKitAuditEntry apply(InstallKit kit, String executor) { return kit.retire(executor); }
        },
        REINSTATE {
            @Override InstallKitAuditEntry apply(InstallKit kit, String executor) { return kit.reinstate(executor); }
        };

        abstract InstallKitAuditEntry apply(InstallKit kit, String executor);
    }

    private final InstallKitWorkflow workflow;

    public ControlInstallKitUseCase(InstallKitWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, Action action, String executor, String role) {
        log.info("[USE CASE] {} on InstallKit ID: {}", action, id);
        return workflow.apply(id, organisationId, role, "retire or reinstate a kit", kit -> action.apply(kit, executor));
    }
}
