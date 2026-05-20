package com.mockbank.commons.dto.exception;

import java.util.UUID;

public class OwnerAccessDeniedException extends RuntimeException {
    public OwnerAccessDeniedException() {
        super("Invalid Owner " );
    }
}
