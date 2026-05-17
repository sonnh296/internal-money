package com.commons.exception;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(UUID accountId) {
        super("No account with ID: " + accountId);
    }

    public AccountNotFoundException(String detail) {
        super(detail);
    }
}
