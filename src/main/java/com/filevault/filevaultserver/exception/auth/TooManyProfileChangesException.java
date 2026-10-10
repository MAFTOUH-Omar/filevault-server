package com.filevault.filevaultserver.exception.auth;

public class TooManyProfileChangesException extends RuntimeException {

    public TooManyProfileChangesException(String field) {
        super("Too many " + field + " changes today; try again tomorrow");
    }
}
