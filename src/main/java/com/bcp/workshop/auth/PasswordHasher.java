package com.bcp.workshop.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Pattern;

@ApplicationScoped
public class PasswordHasher {
    private static final int BCRYPT_ITERATIONS = 12;
    private static final int BCRYPT_HASH_BODY_LENGTH = 53;
    private static final Pattern BCRYPT_HASH_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{"
            + BCRYPT_HASH_BODY_LENGTH + "}$");

    public String hash(String password) {
        return BcryptUtil.bcryptHash(password, BCRYPT_ITERATIONS);
    }

    public boolean matches(String password, String passwordHash) {
        return BcryptUtil.matches(password, passwordHash);
    }

    public boolean isBcryptHash(String value) {
        return BCRYPT_HASH_PATTERN.matcher(value).matches();
    }
}