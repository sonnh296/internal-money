package com.mockbank.customer.util;

import com.mockbank.commons.dto.util.Sha256Fingerprints;

public final class Fingerprints {

    private Fingerprints() {}

    public static String customerCreate(String firstName, String lastName, String email, String phone,
                                        String address) {
        return Sha256Fingerprints.fingerprint(firstName, lastName, email, phone, address);
    }
}
