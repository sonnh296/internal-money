package com.mockbank.auth.service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mockbank.auth.config.AuthJwtProperties;
import com.mockbank.auth.model.AuthUser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Service
public class JwtTokenService {

    private final AuthJwtProperties jwtProperties;
    private final JwtKeyProvider keyProvider;

    public JwtTokenService(AuthJwtProperties jwtProperties, JwtKeyProvider keyProvider) {
        this.jwtProperties = jwtProperties;
        this.keyProvider = keyProvider;
    }

    public String createAccessToken(AuthUser user, List<String> permissions) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtProperties.getAccessTokenTtl());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(jwtProperties.getIssuer())
                .audience(jwtProperties.getAudience())
                .subject(user.getId().toString())
                .claim("customer_id", user.getCustomerId())
                .claim("permissions", permissions)
                .claim("scope", String.join(" ", permissions))
                .claim("role", user.getRole() != null ? user.getRole() : "CUSTOMER")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(keyProvider.signingKey().getKeyID())
                        .build(),
                claims
        );
        try {
            jwt.sign(new RSASSASigner(keyProvider.signingKey().toPrivateKey()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign access token", e);
        }
    }
}
