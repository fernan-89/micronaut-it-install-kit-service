package com.thinklab.domain.exception;

/** Domain Exception: no kit with that id in the tenant (another tenant's answers the same). RFC 7807 mapping: HTTP 404 Not Found. */
public class InstallKitNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "ERR-IKT-00404";

    public InstallKitNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
