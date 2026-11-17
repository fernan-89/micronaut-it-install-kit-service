package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.InstallReportResponse;
import com.thinklab.application.mapper.InstallKitMapper;
import com.thinklab.domain.exception.InstallReportNotFoundException;
import com.thinklab.domain.repository.InstallReportRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for reading one install report of the tenant (BIAN Behavior Qualifier: {@code install-report/retrieve}). Staff only; another tenant is a 404. */
@Singleton
public class RetrieveInstallReportUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveInstallReportUseCase.class);

    private final InstallReportRepository repository;

    public RetrieveInstallReportUseCase(InstallReportRepository repository) {
        this.repository = repository;
    }

    public Mono<InstallReportResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving InstallReport ID: {}", id);
        return Mono.fromRunnable(() -> InstallKitAccess.requireStaff(role, "read an install report"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new InstallReportNotFoundException("InstallReport " + id + " not found.")))
                .map(InstallKitMapper::toResponse);
    }
}
