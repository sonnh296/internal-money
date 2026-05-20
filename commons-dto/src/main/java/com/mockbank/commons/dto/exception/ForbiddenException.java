package com.mockbank.commons.dto.exception;

public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) { super(message); }
}
