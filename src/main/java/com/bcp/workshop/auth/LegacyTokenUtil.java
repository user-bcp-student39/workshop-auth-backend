package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Instant;
import java.util.Date;

public final class LegacyTokenUtil {
    public static final int TOKEN_TTL_SECONDS = 3600;

    private static final String SECRET_CONFIG_KEY = "auth.token.secret";
    private static final String ROLE_CLAIM = "role";

    private LegacyTokenUtil() {}

    public static String create(String username, String role) {
        Instant issuedAt = Instant.now();
        return JWT.create()
                .withSubject(username)
                .withClaim(ROLE_CLAIM, role)
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(issuedAt.plusSeconds(TOKEN_TTL_SECONDS)))
                .sign(signingAlgorithm());
    }

    public static DecodedJWT verify(String token) {
        return JWT.require(signingAlgorithm()).build().verify(token);
    }

    private static Algorithm signingAlgorithm() {
        String secret = ConfigProvider.getConfig().getValue(SECRET_CONFIG_KEY, String.class);
        return Algorithm.HMAC256(secret);
    }
}
