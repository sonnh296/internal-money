package com.mockbank.account.util;

import com.mockbank.commons.dto.util.Sha256Fingerprints;

public final class Fingerprints {

    private Fingerprints() {}

    public static String createAccount(String customerId, String type, String subType, String currency,
                                       String nickname, String displayName) {
        return Sha256Fingerprints.fingerprint(customerId, type, subType, currency, nickname, displayName);
    }

    public static String hold(String accountId, String amount, String currency, String type, String reason) {
        return Sha256Fingerprints.fingerprint(accountId, amount, currency, type, reason);
    }

    public static String posting(String accountId, String side, String amount, String currency, String reason) {
        return Sha256Fingerprints.fingerprint(accountId, side, amount, currency, reason);
    }
}
