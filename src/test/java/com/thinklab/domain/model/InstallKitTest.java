package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidInstallKitStatusException;
import com.thinklab.domain.model.InstallKit.Family;
import com.thinklab.domain.model.InstallKit.InstallKitAuditEntry;
import com.thinklab.domain.model.InstallKit.InstallKitStatus;
import com.thinklab.domain.model.InstallKit.Language;
import com.thinklab.domain.model.InstallKit.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallKitTest {

    private final UUID org = UUID.randomUUID();

    private InstallKit created() {
        return InstallKit.createNew(UUID.randomUUID(), org, "Windows 11 EN", Family.WINDOWS, Role.WORKSTATION, Language.EN, "W11", "BASE", "op");
    }

    private static void rejects(Executable action) {
        assertThrows(IllegalArgumentException.class, action);
    }

    @Test
    @DisplayName("a new kit is ACTIVE at revision 1, names its system, language and machine class, and starts its trail")
    void createNew() {
        InstallKit kit = created();

        assertEquals(InstallKitStatus.ACTIVE, kit.getStatus());
        assertEquals("Windows 11 EN", kit.getName());
        assertEquals(Family.WINDOWS, kit.getFamily());
        assertEquals(Role.WORKSTATION, kit.getRole());
        assertEquals(Language.EN, kit.getLanguage());
        assertEquals("W11", kit.getSystemId());
        assertEquals("BASE", kit.getMachineClass());
        assertEquals(1, kit.getRevision());
        assertEquals(1, kit.getAuditTrail().size());
        assertEquals("INITIATED", kit.getAuditTrail().get(0).action());
        assertNull(kit.getAuditTrail().get(0).fromStatus());
        assertEquals(kit.getCreatedAt(), kit.getUpdatedAt());
    }

    @Test
    @DisplayName("a kit needs an identity, a name of up to 80 characters, family, role, language, short codes and an executor; a server kit is English only")
    void validation() {
        UUID id = UUID.randomUUID();
        Family f = Family.WINDOWS;
        Role r = Role.WORKSTATION;
        Language l = Language.PT;
        rejects(() -> InstallKit.createNew(null, org, "k", f, r, l, "W11", "BASE", "op"));
        rejects(() -> InstallKit.createNew(id, null, "k", f, r, l, "W11", "BASE", "op"));
        rejects(() -> InstallKit.createNew(id, org, null, f, r, l, "W11", "BASE", "op"));
        rejects(() -> InstallKit.createNew(id, org, " ", f, r, l, "W11", "BASE", "op"));
        rejects(() -> InstallKit.createNew(id, org, "x".repeat(81), f, r, l, "W11", "BASE", "op"));
        rejects(() -> InstallKit.createNew(id, org, "k", null, r, l, "W11", "BASE", "op"));
        rejects(() -> InstallKit.createNew(id, org, "k", f, null, l, "W11", "BASE", "op"));
        rejects(() -> InstallKit.createNew(id, org, "k", f, r, null, "W11", "BASE", "op"));
        rejects(() -> InstallKit.createNew(id, org, "k", f, r, l, null, "BASE", "op"));
        rejects(() -> InstallKit.createNew(id, org, "k", f, r, l, "not a code", "BASE", "op"));
        rejects(() -> InstallKit.createNew(id, org, "k", f, r, l, "W11", null, "op"));
        rejects(() -> InstallKit.createNew(id, org, "k", f, r, l, "W11", "x".repeat(41), "op"));
        rejects(() -> InstallKit.createNew(id, org, "k", f, r, l, "W11", "BASE", null));
        rejects(() -> InstallKit.createNew(id, org, "k", f, r, l, "W11", "BASE", " "));
        rejects(() -> InstallKit.createNew(id, org, "k", f, Role.SERVER, Language.PT, "S19", "BASE", "op"));
        assertEquals("x".repeat(80), InstallKit.createNew(id, org, "x".repeat(80), Family.LINUX, Role.SERVER, Language.EN, "ubuntu", "BASE", "op").getName());
        assertEquals(Language.PT, InstallKit.createNew(id, org, "k", f, Role.TOOL, Language.PT, "pe", "BASE", "op").getLanguage());
    }

    @Test
    @DisplayName("update changes the whole definition in any status, raises the revision and records it")
    void update() {
        InstallKit kit = created();

        InstallKitAuditEntry entry = kit.update("Windows 11 PT", Family.WINDOWS, Role.WORKSTATION, Language.PT, "W11", "P520", "op-2");

        assertEquals("Windows 11 PT", kit.getName());
        assertEquals(Language.PT, kit.getLanguage());
        assertEquals("P520", kit.getMachineClass());
        assertEquals(2, kit.getRevision());
        assertEquals("UPDATED", entry.action());
        assertEquals(InstallKitStatus.ACTIVE, entry.fromStatus());
        kit.retire("op");
        kit.update("k", Family.LINUX, Role.SERVER, Language.EN, "alma", "BASE", "op");
        assertEquals(InstallKitStatus.RETIRED, kit.getStatus());
        assertEquals(3, kit.getRevision());
        rejects(() -> kit.update(" ", Family.WINDOWS, Role.WORKSTATION, Language.EN, "W11", "BASE", "op"));
        rejects(() -> kit.update("k", Family.WINDOWS, Role.SERVER, Language.PT, "W11", "BASE", "op"));
        rejects(() -> kit.update("k", Family.WINDOWS, Role.WORKSTATION, Language.EN, "W11", "BASE", null));
        assertEquals(3, kit.getRevision());
    }

    @Test
    @DisplayName("retire and reinstate go one way each; the wrong state is a conflict")
    void lifecycle() {
        InstallKit kit = created();

        assertThrows(InvalidInstallKitStatusException.class, () -> kit.reinstate("op"));
        InstallKitAuditEntry off = kit.retire("op");
        assertEquals(InstallKitStatus.RETIRED, kit.getStatus());
        assertEquals("RETIRED", off.action());
        assertThrows(InvalidInstallKitStatusException.class, () -> kit.retire("op"));
        InstallKitAuditEntry on = kit.reinstate("op");
        assertEquals(InstallKitStatus.ACTIVE, kit.getStatus());
        assertEquals(InstallKitStatus.RETIRED, on.fromStatus());
        rejects(() -> kit.retire(null));
        kit.retire("op");
        rejects(() -> kit.reinstate(" "));
        assertEquals(4, kit.getAuditTrail().size());
        assertTrue(kit.getUpdatedAt().compareTo(kit.getCreatedAt()) >= 0);
    }

    @Test
    @DisplayName("reconstitute keeps what was stored, defaults what is missing, and refuses a missing defining field")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-11-17T10:00:00Z");
        InstallKit full = InstallKit.reconstitute(id, org, "k", Family.RECOVERY, Role.TOOL, Language.EN, "pe", "BASE", 4, InstallKitStatus.RETIRED, created,
                created.plusSeconds(5), List.of(new InstallKitAuditEntry(created, "INITIATED", "op", null, InstallKitStatus.ACTIVE, "d")));
        assertEquals(InstallKitStatus.RETIRED, full.getStatus());
        assertEquals(4, full.getRevision());
        assertEquals(created.plusSeconds(5), full.getUpdatedAt());
        assertEquals(1, full.getAuditTrail().size());

        InstallKit bare = InstallKit.reconstitute(id, org, "k", Family.RECOVERY, Role.TOOL, Language.EN, "pe", "BASE", 0, null, null, null, null);
        assertEquals(InstallKitStatus.ACTIVE, bare.getStatus());
        assertEquals(1, bare.getRevision());
        assertTrue(bare.getAuditTrail().isEmpty());
        assertEquals(bare.getCreatedAt(), bare.getUpdatedAt());
        Family f = Family.RECOVERY;
        Role r = Role.TOOL;
        Language l = Language.EN;
        rejects(() -> InstallKit.reconstitute(null, org, "k", f, r, l, "pe", "BASE", 1, null, null, null, null));
        rejects(() -> InstallKit.reconstitute(id, null, "k", f, r, l, "pe", "BASE", 1, null, null, null, null));
        rejects(() -> InstallKit.reconstitute(id, org, null, f, r, l, "pe", "BASE", 1, null, null, null, null));
        rejects(() -> InstallKit.reconstitute(id, org, "k", null, r, l, "pe", "BASE", 1, null, null, null, null));
        rejects(() -> InstallKit.reconstitute(id, org, "k", f, null, l, "pe", "BASE", 1, null, null, null, null));
        rejects(() -> InstallKit.reconstitute(id, org, "k", f, r, null, "pe", "BASE", 1, null, null, null, null));
        rejects(() -> InstallKit.reconstitute(id, org, "k", f, r, l, null, "BASE", 1, null, null, null, null));
        rejects(() -> InstallKit.reconstitute(id, org, "k", f, r, l, "pe", null, 1, null, null, null, null));
        assertThrows(UnsupportedOperationException.class, () -> full.getAuditTrail().clear());
    }
}
