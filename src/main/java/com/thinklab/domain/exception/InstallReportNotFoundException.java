package com.thinklab.domain.exception;

/** Domain Exception: an install report that does not exist in the organisation. RFC 7807 mapping: HTTP 404 Not Found. */
public class InstallReportNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "ERR-IKT-00404";

    public InstallReportNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
