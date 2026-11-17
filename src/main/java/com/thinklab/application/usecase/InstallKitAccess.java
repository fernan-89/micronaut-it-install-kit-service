package com.thinklab.application.usecase;

import com.thinklab.domain.exception.InstallKitAccessDeniedException;

/** The kit registry is for IT staff (ADR-032): a REQUESTER is refused everywhere. */
final class InstallKitAccess {

    static final String REQUESTER_ROLE = "REQUESTER";

    private InstallKitAccess() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static void requireStaff(String role, String operation) {
        if (REQUESTER_ROLE.equals(role)) {
            throw new InstallKitAccessDeniedException("A requester cannot " + operation + ": install kits and reports are handled by IT staff.");
        }
    }
}
