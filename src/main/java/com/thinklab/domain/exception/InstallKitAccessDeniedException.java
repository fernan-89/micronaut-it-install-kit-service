package com.thinklab.domain.exception;

/** Domain Exception: a REQUESTER tried to use the kit registry (staff only, ADR-032). RFC 7807 mapping: HTTP 403 Forbidden. */
public class InstallKitAccessDeniedException extends BusinessException {

    private static final String ERROR_CODE = "ERR-IKT-00403";

    public InstallKitAccessDeniedException(String message) {
        super(ERROR_CODE, message);
    }
}
