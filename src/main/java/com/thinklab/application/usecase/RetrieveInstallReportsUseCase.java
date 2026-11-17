package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.InstallReportResponse;
import com.thinklab.application.mapper.InstallKitMapper;
import com.thinklab.domain.model.InstallReport.Outcome;
import com.thinklab.domain.repository.InstallReportRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for listing the tenant's install reports, newest first, filterable by kit, outcome and asset (BIAN Behavior Qualifier: {@code install-report/retrieve}, collection). */
@Singleton
public class RetrieveInstallReportsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveInstallReportsUseCase.class);

    private final InstallReportRepository repository;

    public RetrieveInstallReportsUseCase(InstallReportRepository repository) {
        this.repository = repository;
    }

    public Flux<InstallReportResponse> execute(UUID organisationId, UUID kitId, Outcome outcome, UUID assetId, String role) {
        log.info("[USE CASE] Retrieving the InstallReports of organisation: {}", organisationId);
        return Mono.fromRunnable(() -> InstallKitAccess.requireStaff(role, "list install reports"))
                .thenMany(Flux.defer(() -> repository.findAll(organisationId)
                        .filter(report -> kitId == null || report.getKitId().equals(kitId))
                        .filter(report -> outcome == null || report.getOutcome() == outcome)
                        .filter(report -> assetId == null || assetId.equals(report.getAssetId()))
                        .map(InstallKitMapper::toResponse)));
    }
}
