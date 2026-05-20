package com.mockbank.commons.dto.exception;
public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(String message) { super(message); }
}
