package com.thinklab.domain.repository;

import com.thinklab.domain.model.InstallReport;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound Port for InstallReport persistence. A report is only ever inserted; every lookup is tenant-scoped. */
public interface InstallReportRepository {

    Mono<InstallReport> create(InstallReport report);

    Mono<InstallReport> findById(UUID id, UUID organisationId);

    /** The tenant's reports, newest first. */
    Flux<InstallReport> findAll(UUID organisationId);
}
