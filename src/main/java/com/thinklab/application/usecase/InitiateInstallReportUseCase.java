package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateInstallReportRequest;
import com.thinklab.application.dto.response.InstallReportResponse;
import com.thinklab.application.mapper.InstallKitMapper;
import com.thinklab.domain.exception.InstallKitNotFoundException;
import com.thinklab.domain.model.InstallReport;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.InstallKitRepository;
import com.thinklab.domain.repository.InstallReportRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for recording what happened when a kit ran (BIAN Behavior Qualifier: {@code install-report/initiate}). Staff only. The kit must exist in the
 * organisation (a 404 otherwise); its current revision is written into the report, so the report says which version of the kit ran. A retired kit can
 * still be reported on: a machine may have been installed with it before it was retired.
 */
@Singleton
public class InitiateInstallReportUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateInstallReportUseCase.class);

    private final HashServicePort hashServicePort;
    private final InstallKitRepository kits;
    private final InstallReportRepository reports;

    public InitiateInstallReportUseCase(HashServicePort hashServicePort, InstallKitRepository kits, InstallReportRepository reports) {
        this.hashServicePort = hashServicePort;
        this.kits = kits;
        this.reports = reports;
    }

    public Mono<InstallReportResponse> execute(UUID organisationId, InitiateInstallReportRequest request, String executor, String role) {
        log.info("[USE CASE] Recording an install report of kit {} for organisation: {}", request.kitId(), organisationId);
        return Mono.fromRunnable(() -> InstallKitAccess.requireStaff(role, "record an install report"))
                .then(Mono.defer(() -> kits.findById(request.kitId(), organisationId)))
                .switchIfEmpty(Mono.error(new InstallKitNotFoundException("InstallKit " + request.kitId() + " not found.")))
                .flatMap(kit -> hashServicePort.generateSovereignId("install-report-creation")
                        .map(id -> InstallReport.createNew(id, organisationId, kit.getId(), kit.getRevision(), request.addOnVersion(), request.outcome(), request.warnings(),
                                request.assetId(), request.occurredAt(), executor)))
                .flatMap(reports::create)
                .map(InstallKitMapper::toResponse);
    }
}
