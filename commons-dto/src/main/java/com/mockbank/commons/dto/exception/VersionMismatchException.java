package com.mockbank.commons.dto.exception;
public class VersionMismatchException extends RuntimeException {
    public VersionMismatchException(String message) { super(message); }
}
