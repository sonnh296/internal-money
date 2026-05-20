package com.mockbank.commons.dto.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Sha256Fingerprints {

    private Sha256Fingerprints() {}

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String fingerprint(String... parts) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                joined.append('|');
            }
            joined.append(normalize(parts[i]));
        }
        return sha256(joined.toString());
    }
}
