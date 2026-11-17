package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateInstallKitRequest;
import com.thinklab.application.dto.request.InitiateInstallReportRequest;
import com.thinklab.application.dto.request.UpdateInstallKitRequest;
import com.thinklab.domain.exception.InstallKitAccessDeniedException;
import com.thinklab.domain.exception.InstallKitNotFoundException;
import com.thinklab.domain.exception.InstallReportNotFoundException;
import com.thinklab.domain.exception.InvalidInstallKitStatusException;
import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.model.InstallKit.Family;
import com.thinklab.domain.model.InstallKit.InstallKitStatus;
import com.thinklab.domain.model.InstallKit.Language;
import com.thinklab.domain.model.InstallKit.Role;
import com.thinklab.domain.model.InstallReport;
import com.thinklab.domain.model.InstallReport.Outcome;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.InstallKitRepository;
import com.thinklab.domain.repository.InstallReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstallKitUseCasesTest {

    private static final String STAFF = "ENGINEER";
    private static final String REQUESTER = "REQUESTER";

    private final UUID org = UUID.randomUUID();
    private final UUID asset = UUID.randomUUID();
    private final Instant when = Instant.parse("2026-11-17T12:00:00Z");

    private HashServicePort hash;
    private InstallKitRepository kits;
    private InstallReportRepository reports;

    @BeforeEach
    void setUp() {
        hash = mock(HashServicePort.class);
        kits = mock(InstallKitRepository.class);
        reports = mock(InstallReportRepository.class);
    }

    private InstallKit kit(String name, String systemId, Language language, String machineClass) {
        return InstallKit.createNew(UUID.randomUUID(), org, name, Family.WINDOWS, Role.WORKSTATION, language, systemId, machineClass, "op");
    }

    private InstallKit kit() {
        return kit("Windows 11 EN", "W11", Language.EN, "BASE");
    }

    private InitiateInstallKitRequest kitRequest() {
        return new InitiateInstallKitRequest("Windows 11 EN", Family.WINDOWS, Role.WORKSTATION, Language.EN, "W11", "BASE");
    }

    // ------------------------------------------------------------------ Kits

    @Test
    @DisplayName("initiate a kit takes a sovereign id and saves it; a requester is refused before an id is taken")
    void initiateInstallKit() {
        when(hash.generateSovereignId("kit-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(kits.create(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        InitiateInstallKitUseCase useCase = new InitiateInstallKitUseCase(hash, kits);

        StepVerifier.create(useCase.execute(org, kitRequest(), "op", STAFF)).assertNext(response -> {
            assertEquals("Windows 11 EN", response.name());
            assertEquals("WINDOWS", response.family());
            assertEquals("W11", response.systemId());
            assertEquals("EN", response.language());
            assertEquals(1, response.revision());
            assertEquals("ACTIVE", response.status());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(org, kitRequest(), "op", REQUESTER)).expectError(InstallKitAccessDeniedException.class).verify();
        verify(hash).generateSovereignId("kit-creation");
    }

    @Test
    @DisplayName("update, retire and reinstate go through the workflow with the status loaded; a requester and an unknown kit are refused")
    void updateAndControlInstallKit() {
        InstallKit kit = kit();
        when(kits.findById(kit.getId(), org)).thenReturn(Mono.just(kit));
        when(kits.save(any(), any(), any())).thenReturn(Mono.empty());
        InstallKitWorkflow workflow = new InstallKitWorkflow(kits);
        UpdateInstallKitUseCase update = new UpdateInstallKitUseCase(workflow);
        ControlInstallKitUseCase control = new ControlInstallKitUseCase(workflow);

        StepVerifier.create(update.execute(kit.getId(), org, new UpdateInstallKitRequest("Windows 11 PT", Family.WINDOWS, Role.WORKSTATION, Language.PT, "W11", "BASE"), "op", STAFF))
                .verifyComplete();
        assertEquals("Windows 11 PT", kit.getName());
        assertEquals(2, kit.getRevision());
        StepVerifier.create(control.execute(kit.getId(), org, ControlInstallKitUseCase.Action.RETIRE, "op", null)).verifyComplete();
        assertEquals(InstallKitStatus.RETIRED, kit.getStatus());
        StepVerifier.create(control.execute(kit.getId(), org, ControlInstallKitUseCase.Action.REINSTATE, "op", STAFF)).verifyComplete();
        assertEquals(InstallKitStatus.ACTIVE, kit.getStatus());
        verify(kits).save(any(), eq(InstallKitStatus.RETIRED), any());

        StepVerifier.create(control.execute(kit.getId(), org, ControlInstallKitUseCase.Action.REINSTATE, "op", STAFF)).expectError(InvalidInstallKitStatusException.class).verify();
        StepVerifier.create(control.execute(kit.getId(), org, ControlInstallKitUseCase.Action.RETIRE, "op", REQUESTER)).expectError(InstallKitAccessDeniedException.class).verify();
        UUID missing = UUID.randomUUID();
        when(kits.findById(missing, org)).thenReturn(Mono.empty());
        StepVerifier.create(control.execute(missing, org, ControlInstallKitUseCase.Action.RETIRE, "op", STAFF)).expectError(InstallKitNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieve a kit, list them filtered by status and family, and read the audit log: staff only, 404 for another tenant")
    void retrieveInstallKits() {
        InstallKit windows = kit();
        InstallKit rescue = InstallKit.createNew(UUID.randomUUID(), org, "Rescue", Family.RECOVERY, Role.TOOL, Language.EN, "rescue", "BASE", "op");
        rescue.retire("op");
        when(kits.findById(windows.getId(), org)).thenReturn(Mono.just(windows));
        UUID elsewhere = UUID.randomUUID();
        when(kits.findById(windows.getId(), elsewhere)).thenReturn(Mono.empty());
        when(kits.findAll(org)).thenReturn(Flux.just(windows, rescue));

        RetrieveInstallKitUseCase one = new RetrieveInstallKitUseCase(kits);
        StepVerifier.create(one.execute(windows.getId(), org, STAFF)).assertNext(response -> assertEquals("Windows 11 EN", response.name())).verifyComplete();
        StepVerifier.create(one.execute(windows.getId(), org, REQUESTER)).expectError(InstallKitAccessDeniedException.class).verify();
        StepVerifier.create(one.execute(windows.getId(), elsewhere, STAFF)).expectError(InstallKitNotFoundException.class).verify();

        RetrieveInstallKitsUseCase list = new RetrieveInstallKitsUseCase(kits);
        StepVerifier.create(list.execute(org, null, null, STAFF).map(r -> r.name())).expectNext("Windows 11 EN", "Rescue").verifyComplete();
        StepVerifier.create(list.execute(org, InstallKitStatus.RETIRED, null, null).map(r -> r.name())).expectNext("Rescue").verifyComplete();
        StepVerifier.create(list.execute(org, null, Family.WINDOWS, STAFF).map(r -> r.name())).expectNext("Windows 11 EN").verifyComplete();
        StepVerifier.create(list.execute(org, InstallKitStatus.ACTIVE, Family.RECOVERY, STAFF)).verifyComplete();
        StepVerifier.create(list.execute(org, null, null, REQUESTER)).expectError(InstallKitAccessDeniedException.class).verify();

        RetrieveInstallKitAuditLogUseCase audit = new RetrieveInstallKitAuditLogUseCase(kits);
        StepVerifier.create(audit.execute(windows.getId(), org, STAFF)).assertNext(trail -> assertEquals("INITIATED", trail.get(0).action())).verifyComplete();
        StepVerifier.create(audit.execute(windows.getId(), org, REQUESTER)).expectError(InstallKitAccessDeniedException.class).verify();
        StepVerifier.create(audit.execute(windows.getId(), elsewhere, STAFF)).expectError(InstallKitNotFoundException.class).verify();
    }

    // ------------------------------------------------------------------ Stick definition

    @Test
    @DisplayName("the stick definition lists only ACTIVE kits, grouped by system and sorted by language then machine class; a requester is refused")
    void stickDefinition() {
        InstallKit pt = kit("W11 PT P520", "W11", Language.PT, "P520");
        InstallKit enP520 = kit("W11 EN P520", "W11", Language.EN, "P520");
        InstallKit enBase = kit("W11 EN BASE", "W11", Language.EN, "BASE");
        InstallKit server = InstallKit.createNew(UUID.randomUUID(), org, "S19 EN", Family.WINDOWS, Role.SERVER, Language.EN, "S19", "BASE", "op");
        InstallKit retired = kit("Old", "L21", Language.EN, "BASE");
        retired.retire("op");
        when(kits.findAll(org)).thenReturn(Flux.just(pt, enP520, server, retired, enBase));
        RetrieveStickDefinitionUseCase useCase = new RetrieveStickDefinitionUseCase(kits);

        StepVerifier.create(useCase.execute(org, STAFF)).assertNext(definition -> {
            assertEquals(4, definition.kitCount());
            assertEquals(2, definition.systems().size());
            assertEquals("S19", definition.systems().get(0).systemId());
            assertEquals("SERVER", definition.systems().get(0).role());
            var w11 = definition.systems().get(1);
            assertEquals("W11", w11.systemId());
            assertEquals("WINDOWS", w11.family());
            assertEquals("W11 EN BASE", w11.kits().get(0).name());
            assertEquals("W11 EN P520", w11.kits().get(1).name());
            assertEquals("W11 PT P520", w11.kits().get(2).name());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(org, REQUESTER)).expectError(InstallKitAccessDeniedException.class).verify();
        when(kits.findAll(org)).thenReturn(Flux.empty());
        StepVerifier.create(useCase.execute(org, STAFF)).assertNext(definition -> assertEquals(0, definition.kitCount())).verifyComplete();
    }

    // ------------------------------------------------------------------ Reports

    private InitiateInstallReportRequest reportRequest(UUID kitId, UUID assetId) {
        return new InitiateInstallReportRequest(kitId, "1.2.2", Outcome.PARTIAL, 1, assetId, when);
    }

    private InstallReport report(UUID kitId, Outcome outcome, UUID assetId) {
        return InstallReport.createNew(UUID.randomUUID(), org, kitId, 1, "1.2.2", outcome, 0, assetId, when, "op");
    }

    @Test
    @DisplayName("initiate a report needs a kit of the tenant, takes a sovereign id and writes the kit's revision; a retired kit can still be reported on; a requester is refused first")
    void initiateReport() {
        InstallKit kit = kit();
        kit.update("Windows 11 EN v2", Family.WINDOWS, Role.WORKSTATION, Language.EN, "W11", "BASE", "op");
        InstallKit retired = kit("Old", "L21", Language.EN, "BASE");
        retired.retire("op");
        when(kits.findById(kit.getId(), org)).thenReturn(Mono.just(kit));
        when(kits.findById(retired.getId(), org)).thenReturn(Mono.just(retired));
        UUID elsewhere = UUID.randomUUID();
        when(kits.findById(elsewhere, org)).thenReturn(Mono.empty());
        when(hash.generateSovereignId("install-report-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(reports.create(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        InitiateInstallReportUseCase useCase = new InitiateInstallReportUseCase(hash, kits, reports);

        StepVerifier.create(useCase.execute(org, reportRequest(kit.getId(), asset), "op", STAFF)).assertNext(response -> {
            assertEquals(kit.getId(), response.kitId());
            assertEquals(2, response.kitRevision());
            assertEquals("PARTIAL", response.outcome());
            assertEquals(asset, response.assetId());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(org, reportRequest(retired.getId(), null), "op", STAFF)).assertNext(response -> assertEquals(1, response.kitRevision())).verifyComplete();
        StepVerifier.create(useCase.execute(org, reportRequest(elsewhere, null), "op", STAFF)).expectError(InstallKitNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(org, reportRequest(kit.getId(), null), "op", REQUESTER)).expectError(InstallKitAccessDeniedException.class).verify();
        verify(reports, never()).findById(any(), any());
    }

    @Test
    @DisplayName("retrieve a report and list them filtered by kit, outcome and asset: staff only, 404 for another tenant")
    void retrieveReports() {
        UUID kitA = UUID.randomUUID();
        UUID kitB = UUID.randomUUID();
        InstallReport ok = report(kitA, Outcome.SUCCEEDED, asset);
        InstallReport bad = report(kitB, Outcome.FAILED, null);
        when(reports.findById(ok.getId(), org)).thenReturn(Mono.just(ok));
        UUID elsewhere = UUID.randomUUID();
        when(reports.findById(ok.getId(), elsewhere)).thenReturn(Mono.empty());
        when(reports.findAll(org)).thenReturn(Flux.just(ok, bad));

        RetrieveInstallReportUseCase one = new RetrieveInstallReportUseCase(reports);
        StepVerifier.create(one.execute(ok.getId(), org, STAFF)).assertNext(response -> assertEquals("SUCCEEDED", response.outcome())).verifyComplete();
        StepVerifier.create(one.execute(ok.getId(), org, REQUESTER)).expectError(InstallKitAccessDeniedException.class).verify();
        StepVerifier.create(one.execute(ok.getId(), elsewhere, STAFF)).expectError(InstallReportNotFoundException.class).verify();

        RetrieveInstallReportsUseCase list = new RetrieveInstallReportsUseCase(reports);
        StepVerifier.create(list.execute(org, null, null, null, STAFF).map(r -> r.outcome())).expectNext("SUCCEEDED", "FAILED").verifyComplete();
        StepVerifier.create(list.execute(org, kitB, null, null, null).map(r -> r.outcome())).expectNext("FAILED").verifyComplete();
        StepVerifier.create(list.execute(org, null, Outcome.SUCCEEDED, null, STAFF).map(r -> r.outcome())).expectNext("SUCCEEDED").verifyComplete();
        StepVerifier.create(list.execute(org, null, null, asset, STAFF).map(r -> r.outcome())).expectNext("SUCCEEDED").verifyComplete();
        StepVerifier.create(list.execute(org, kitA, Outcome.FAILED, null, STAFF)).verifyComplete();
        StepVerifier.create(list.execute(org, null, null, null, REQUESTER)).expectError(InstallKitAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the access check is a utility class that cannot be instantiated")
    void utilityClass() throws Exception {
        Constructor<InstallKitAccess> constructor = InstallKitAccess.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertEquals(UnsupportedOperationException.class, failure.getCause().getClass());
        InstallKitAccess.requireStaff(null, "do anything");
        assertThrows(InstallKitAccessDeniedException.class, () -> InstallKitAccess.requireStaff(REQUESTER, "do anything"));
    }
}
