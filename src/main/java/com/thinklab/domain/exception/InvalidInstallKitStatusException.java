package com.thinklab.domain.exception;

/** Domain Exception: an illegal InstallKit lifecycle transition, the kit changed while the write was applied. RFC 7807 mapping: HTTP 409 Conflict. */
public class InvalidInstallKitStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-IKT-00409";

    public InvalidInstallKitStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
