package com.mockbank.commons.dto.exception;
public class PreconditionRequiredException extends RuntimeException {
    public PreconditionRequiredException(String msg) { super(msg); }
}