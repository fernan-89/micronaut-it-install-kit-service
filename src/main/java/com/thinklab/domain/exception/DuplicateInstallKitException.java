package com.thinklab.domain.exception;

/** Domain Exception: a kit name already used in the organisation. RFC 7807 mapping: HTTP 409 Conflict. */
public class DuplicateInstallKitException extends BusinessException {

    private static final String ERROR_CODE = "ERR-IKT-00409";

    public DuplicateInstallKitException(String message) {
        super(ERROR_CODE, message);
    }
}
