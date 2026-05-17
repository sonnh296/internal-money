package com.authuser.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

@Component
public class JwtKeyProvider {

    private final RSAKey rsaJwk;
    private final JWKSet jwkSet;

    public JwtKeyProvider() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair pair = kpg.generateKeyPair();

            this.rsaJwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .build();
            this.jwkSet = new JWKSet(rsaJwk.toPublicJWK());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize RSA key pair for JWT", e);
        }
    }

    public RSAKey signingKey() {
        return rsaJwk;
    }

    public JWKSet publicJwkSet() {
        return jwkSet;
    }
}
