package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateInstallKitRequest;
import com.thinklab.application.dto.request.InitiateInstallReportRequest;
import com.thinklab.application.dto.request.UpdateInstallKitRequest;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.InstallKitResponse;
import com.thinklab.application.dto.response.InstallReportResponse;
import com.thinklab.application.dto.response.StickDefinitionResponse;
import com.thinklab.application.usecase.ControlInstallKitUseCase;
import com.thinklab.application.usecase.InitiateInstallKitUseCase;
import com.thinklab.application.usecase.InitiateInstallReportUseCase;
import com.thinklab.application.usecase.RetrieveInstallKitAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveInstallKitUseCase;
import com.thinklab.application.usecase.RetrieveInstallKitsUseCase;
import com.thinklab.application.usecase.RetrieveInstallReportUseCase;
import com.thinklab.application.usecase.RetrieveInstallReportsUseCase;
import com.thinklab.application.usecase.RetrieveStickDefinitionUseCase;
import com.thinklab.application.usecase.UpdateInstallKitUseCase;
import com.thinklab.domain.model.InstallKit.Family;
import com.thinklab.domain.model.InstallKit.InstallKitStatus;
import com.thinklab.domain.model.InstallReport.Outcome;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-install-kit} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.InstallKit} is the Control Record, at the root;
 * {@link com.thinklab.domain.model.InstallReport} is the secondary aggregate, under {@code /install-report}: what happened when a kit ran. The stick
 * definition is a read over the active kits. There is no {@code DELETE}: a kit is retired; a report is a fact and is never changed.
 *
 * <p><b>Tenant on every route, staff only (ADR-032):</b> {@code X-Tenant-Id} scopes every lookup (another tenant's kit is a 404) and
 * {@code X-Role} makes every route refuse a {@code REQUESTER} with 403 {@code ERR-IKT-00403}.
 */
@Controller("/it-install-kit/v1")
public class InstallKitController {

    private static final Logger log = LoggerFactory.getLogger(InstallKitController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateInstallKitUseCase initiateKit;
    private final RetrieveInstallKitUseCase retrieveKit;
    private final RetrieveInstallKitsUseCase retrieveKits;
    private final UpdateInstallKitUseCase updateKit;
    private final ControlInstallKitUseCase controlKit;
    private final RetrieveInstallKitAuditLogUseCase kitAuditLog;
    private final InitiateInstallReportUseCase initiateReport;
    private final RetrieveInstallReportUseCase retrieveReport;
    private final RetrieveInstallReportsUseCase retrieveReports;
    private final RetrieveStickDefinitionUseCase stickDefinition;

    public InstallKitController(InitiateInstallKitUseCase initiateKit, RetrieveInstallKitUseCase retrieveKit, RetrieveInstallKitsUseCase retrieveKits,
                                UpdateInstallKitUseCase updateKit, ControlInstallKitUseCase controlKit, RetrieveInstallKitAuditLogUseCase kitAuditLog,
                                InitiateInstallReportUseCase initiateReport, RetrieveInstallReportUseCase retrieveReport, RetrieveInstallReportsUseCase retrieveReports,
                                RetrieveStickDefinitionUseCase stickDefinition) {
        this.initiateKit = initiateKit;
        this.retrieveKit = retrieveKit;
        this.retrieveKits = retrieveKits;
        this.updateKit = updateKit;
        this.controlKit = controlKit;
        this.kitAuditLog = kitAuditLog;
        this.initiateReport = initiateReport;
        this.retrieveReport = retrieveReport;
        this.retrieveReports = retrieveReports;
        this.stickDefinition = stickDefinition;
    }

    // ------------------------------------------------------------------ Kits

    /** Behavior Qualifier: {@code initiate}. Registers a kit. */
    @Post("/initiate")
    public Mono<HttpResponse<InstallKitResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid InitiateInstallKitRequest request
    ) {
        log.info("[ACTION: INITIATE_INSTALL_KIT] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);
        return Mono.defer(() -> initiateKit.execute(UUID.fromString(tenantId), request, executor, role)).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. One kit of the tenant. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<InstallKitResponse>> retrieve(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveKit.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection), oldest first, filterable by status and family. */
    @Get("/retrieve")
    public Mono<List<InstallKitResponse>> retrieveAll(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                      @QueryValue @Nullable InstallKitStatus status, @QueryValue @Nullable Family family) {
        return Mono.defer(() -> retrieveKits.execute(UUID.fromString(tenantId), status, family, role).collectList());
    }

    /** Behavior Qualifier: {@code update}. The whole definition again; the revision goes up. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateInstallKitRequest request
    ) {
        return Mono.defer(() -> updateKit.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/retire}. ACTIVE -&gt; RETIRED: the kit leaves the stick definition. */
    @Put("/{id}/control/retire")
    public Mono<HttpResponse<Void>> retire(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                           @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlInstallKitUseCase.Action.RETIRE, executor, role);
    }

    /** Behavior Qualifier: {@code control/reinstate}. RETIRED -&gt; ACTIVE. */
    @Put("/{id}/control/reinstate")
    public Mono<HttpResponse<Void>> reinstate(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                              @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlInstallKitUseCase.Action.REINSTATE, executor, role);
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> auditLog(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> kitAuditLog.execute(id, UUID.fromString(tenantId), role));
    }

    // ------------------------------------------------------------------ Stick definition

    /** Behavior Qualifier: {@code stick-definition/retrieve}. The active kits grouped by system, for a stick builder. */
    @Get("/stick-definition/retrieve")
    public Mono<StickDefinitionResponse> stickDefinition(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> stickDefinition.execute(UUID.fromString(tenantId), role));
    }

    // ------------------------------------------------------------------ Install reports

    /** Behavior Qualifier: {@code install-report/initiate}. What happened when a kit ran. */
    @Post("/install-report/initiate")
    public Mono<HttpResponse<InstallReportResponse>> initiateReport(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid InitiateInstallReportRequest request
    ) {
        log.info("[ACTION: INITIATE_INSTALL_REPORT] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);
        return Mono.defer(() -> initiateReport.execute(UUID.fromString(tenantId), request, executor, role)).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code install-report/retrieve}. One report of the tenant. */
    @Get("/install-report/{id}/retrieve")
    public Mono<HttpResponse<InstallReportResponse>> retrieveReport(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveReport.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code install-report/retrieve} (collection), newest first, filterable by kit, outcome and asset. */
    @Get("/install-report/retrieve")
    public Mono<List<InstallReportResponse>> retrieveReports(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                             @QueryValue @Nullable UUID kitId, @QueryValue @Nullable Outcome outcome, @QueryValue @Nullable UUID assetId) {
        return Mono.defer(() -> retrieveReports.execute(UUID.fromString(tenantId), kitId, outcome, assetId, role).collectList());
    }

    private Mono<HttpResponse<Void>> control(UUID id, String tenantId, ControlInstallKitUseCase.Action action, String executor, String role) {
        log.info("[ACTION: {}_INSTALL_KIT] [EXECUTOR: {}] for ID: {}", action, executor, id);
        return Mono.defer(() -> controlKit.execute(id, UUID.fromString(tenantId), action, executor, role)).thenReturn(HttpResponse.noContent());
    }
}
