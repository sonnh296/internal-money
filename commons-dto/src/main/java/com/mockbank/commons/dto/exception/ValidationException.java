package com.mockbank.commons.dto.exception;
public class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}
