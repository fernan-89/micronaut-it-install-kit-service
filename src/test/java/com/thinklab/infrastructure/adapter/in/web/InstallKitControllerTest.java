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
import com.thinklab.domain.model.InstallKit.Language;
import com.thinklab.domain.model.InstallKit.Role;
import com.thinklab.domain.model.InstallReport.Outcome;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** The controller only reads headers and delegates: tenant, executor and role always travel to the use case. */
@ExtendWith(MockitoExtension.class)
class InstallKitControllerTest {

    private static final String EXECUTOR = "op-1";
    private static final String ROLE = "ENGINEER";
    private final UUID tenant = UUID.randomUUID();
    private final String tenantHeader = tenant.toString();
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-17T12:00:00Z");

    @Mock private InitiateInstallKitUseCase initiateKit;
    @Mock private RetrieveInstallKitUseCase retrieveKit;
    @Mock private RetrieveInstallKitsUseCase retrieveKits;
    @Mock private UpdateInstallKitUseCase updateKit;
    @Mock private ControlInstallKitUseCase controlKit;
    @Mock private RetrieveInstallKitAuditLogUseCase kitAuditLog;
    @Mock private InitiateInstallReportUseCase initiateReport;
    @Mock private RetrieveInstallReportUseCase retrieveReport;
    @Mock private RetrieveInstallReportsUseCase retrieveReports;
    @Mock private RetrieveStickDefinitionUseCase stickDefinition;

    private InstallKitController controller() {
        return new InstallKitController(initiateKit, retrieveKit, retrieveKits, updateKit, controlKit, kitAuditLog, initiateReport, retrieveReport, retrieveReports, stickDefinition);
    }

    private InstallKitResponse kitResponse() {
        return new InstallKitResponse(id, tenant, "Windows 11 EN", "WINDOWS", "WORKSTATION", "EN", "W11", "BASE", 1, "ACTIVE", now, now);
    }

    private InstallReportResponse reportResponse() {
        return new InstallReportResponse(id, tenant, UUID.randomUUID(), 1, "1.2.2", "SUCCEEDED", 0, null, now, now, EXECUTOR);
    }

    @Test
    @DisplayName("initiate a kit answers 201 with the kit")
    void initiateKitRoute() {
        InitiateInstallKitRequest request = new InitiateInstallKitRequest("Windows 11 EN", Family.WINDOWS, Role.WORKSTATION, Language.EN, "W11", "BASE");
        when(initiateKit.execute(tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.just(kitResponse()));

        StepVerifier.create(controller().initiate(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("retrieve a kit and the kit list answer the use cases' result with the filters")
    void retrieveKitRoutes() {
        when(retrieveKit.execute(id, tenant, ROLE)).thenReturn(Mono.just(kitResponse()));
        when(retrieveKits.execute(tenant, InstallKitStatus.ACTIVE, Family.WINDOWS, ROLE)).thenReturn(Flux.just(kitResponse()));

        StepVerifier.create(controller().retrieve(id, tenantHeader, ROLE)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retrieveAll(tenantHeader, ROLE, InstallKitStatus.ACTIVE, Family.WINDOWS)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("update, retire and reinstate a kit answer 204 and pass the right action")
    void kitChanges() {
        UpdateInstallKitRequest request = new UpdateInstallKitRequest("Windows 11 PT", Family.WINDOWS, Role.WORKSTATION, Language.PT, "W11", "BASE");
        when(updateKit.execute(id, tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlKit.execute(id, tenant, ControlInstallKitUseCase.Action.RETIRE, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlKit.execute(id, tenant, ControlInstallKitUseCase.Action.REINSTATE, EXECUTOR, ROLE)).thenReturn(Mono.empty());

        StepVerifier.create(controller().update(id, tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retire(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().reinstate(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("the audit log answers the trail")
    void auditLog() {
        List<AuditEntryResponse> trail = List.of(new AuditEntryResponse(now, "INITIATED", EXECUTOR, null, "ACTIVE", null));
        when(kitAuditLog.execute(id, tenant, ROLE)).thenReturn(Mono.just(trail));

        StepVerifier.create(controller().auditLog(id, tenantHeader, ROLE)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("the stick definition route passes the tenant and the role")
    void stickDefinitionRoute() {
        when(stickDefinition.execute(tenant, ROLE)).thenReturn(Mono.just(new StickDefinitionResponse(tenant, 0, List.of())));

        StepVerifier.create(controller().stickDefinition(tenantHeader, ROLE)).assertNext(definition -> assertEquals(tenant, definition.organisationId())).verifyComplete();
    }

    @Test
    @DisplayName("initiate a report answers 201 with the report")
    void initiateReportRoute() {
        InitiateInstallReportRequest request = new InitiateInstallReportRequest(UUID.randomUUID(), "1.2.2", Outcome.SUCCEEDED, 0, null, now);
        when(initiateReport.execute(tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.just(reportResponse()));

        StepVerifier.create(controller().initiateReport(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("retrieve a report and the report list answer the use cases' result with the filters")
    void retrieveReportRoutes() {
        UUID kitId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(retrieveReport.execute(id, tenant, ROLE)).thenReturn(Mono.just(reportResponse()));
        when(retrieveReports.execute(tenant, kitId, Outcome.FAILED, assetId, ROLE)).thenReturn(Flux.just(reportResponse()));

        StepVerifier.create(controller().retrieveReport(id, tenantHeader, ROLE)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retrieveReports(tenantHeader, ROLE, kitId, Outcome.FAILED, assetId)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }
}
