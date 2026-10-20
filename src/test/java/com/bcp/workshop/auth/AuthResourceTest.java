package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(AuthResourceTest.AuthConfigurationProfile.class)
class AuthResourceTest {
    private static final String TEST_TOKEN_SECRET = "test-only-secret";

    @Inject
    LegacyDatabase database;

    @Inject
    PasswordHasher passwordHasher;

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("token", notNullValue());
    }

    @Test
    void logsInWithWorkshopTrainer() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"trainer\",\"password\":\"Trainer123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("trainer"))
                .body("user.role", is("TRAINER"));
    }

    @Test
    void rejectsIncorrectPasswordWithDocumentedResponse() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrect\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void rejectsUsernameContainingSqlSyntax() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"missing' OR username='student' -- \",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user missing' OR username='student' --  or password"));
    }

    @Test
    void storesWorkshopPasswordAsBcryptHash() throws SQLException {
        String storedPassword = findStoredPassword("student");

        assertNotEquals("Workshop123!", storedPassword);
        assertTrue(storedPassword.startsWith("$2"));
        assertTrue(passwordHasher.matches("Workshop123!", storedPassword));
    }

    @Test
    void hashesSamePasswordWithDistinctSalts() {
        String firstHash = passwordHasher.hash("Workshop123!");
        String secondHash = passwordHasher.hash("Workshop123!");

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.matches("Workshop123!", firstHash));
        assertTrue(passwordHasher.matches("Workshop123!", secondHash));
    }

    @Test
    void migratesExistingPasswordOnlyOnce() throws SQLException {
        String legacyPassword = "Workshop123!";
        database.initialize();
        updateStoredPassword("student", legacyPassword);

        database.initialize();
        String migratedPassword = findStoredPassword("student");

        assertNotEquals(legacyPassword, migratedPassword);
        assertTrue(migratedPassword.startsWith("$2"));

        database.initialize();

        assertEquals(migratedPassword, findStoredPassword("student"));
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200);
    }

    @Test
    void issuesSignedTokenWithClaimsAndExpiration() {
        String token = loginAndGetToken("student", "Workshop123!");
        DecodedJWT decodedToken = LegacyTokenUtil.verify(token);
        String payload = new String(Base64.getUrlDecoder().decode(decodedToken.getPayload()), StandardCharsets.UTF_8);

        assertEquals("student", decodedToken.getSubject());
        assertEquals("STUDENT", decodedToken.getClaim("role").asString());
        assertTrue(decodedToken.getExpiresAt().after(new Date()));
        assertFalse(payload.contains(TEST_TOKEN_SECRET));
    }

    @Test
    void rejectsTokenWithAlteredSignature() {
        String token = loginAndGetToken("student", "Workshop123!");
        String[] tokenParts = token.split("\\.");
        assertEquals(3, tokenParts.length);
        String alteredFirstCharacter = tokenParts[2].charAt(0) == 'A' ? "B" : "A";
        String alteredToken = tokenParts[0] + "." + tokenParts[1] + "."
                + alteredFirstCharacter + tokenParts[2].substring(1);

        assertThrows(JWTVerificationException.class, () -> LegacyTokenUtil.verify(alteredToken));
    }

    @Test
    void rejectsExpiredSignedToken() {
        String expiredToken = JWT.create()
                .withSubject("student")
                .withClaim("role", "STUDENT")
                .withExpiresAt(Date.from(Instant.now().minusSeconds(1)))
                .sign(Algorithm.HMAC256(TEST_TOKEN_SECRET));

        assertThrows(JWTVerificationException.class, () -> LegacyTokenUtil.verify(expiredToken));
    }

    @Test
    void rejectsCorsOriginOutsideConfiguredAllowlist() {
        given()
                .header("Origin", "https://unapproved.example")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .statusCode(403);
    }

    @Test
    void allowsConfiguredCorsOrigin() {
        given()
                .header("Origin", "https://approved.example")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", is("https://approved.example"));
    }

    @Test
    void reportsHealthAsUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void reportsNoHealthChecksWhenNoneAreRegistered() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("checks", hasSize(0));
    }

    @Test
    void rejectsHealthPreflightFromUnapprovedOrigin() {
        given()
                .header("Origin", "https://unapproved.example")
                .header("Access-Control-Request-Method", "GET")
                .when().options("/q/health")
                .then()
                .statusCode(403)
                .body(isEmptyOrNullString());
    }

    @Test
    void returnsBadRequestForNullLoginRequest() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void returnsBadRequestForEmptyUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"\",\"password\":\"x\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void returnsBadRequestForMissingUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"x\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void returnsBadRequestForNullUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":null,\"password\":\"x\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void returnsBadRequestForWhitespaceUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"  \",\"password\":\"x\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void returnsBadRequestForEmptyPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void returnsBadRequestForMissingPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void returnsBadRequestForNullPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":null}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void returnsBadRequestForWhitespacePassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"  \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsMalformedLoginJsonWithEmptyFrameworkResponse() {
        given()
                .contentType("application/json")
                .body("{\"username\":")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body(isEmptyOrNullString());
    }

    @Test
    void rejectsUnknownUserWithDocumentedResponse() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"missing\",\"password\":\"bad\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user missing or password"));
    }

    @Test
    void rejectsLoginPreflightFromUnapprovedOriginWithEmptyResponse() {
        given()
                .header("Origin", "https://unapproved.example")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .statusCode(403)
                .body(isEmptyOrNullString());
    }

    @Test
    void rejectsWrongLoginMethodWithEmptyFrameworkResponse() {
        given()
                .when().get("/auth/login")
                .then()
                .statusCode(405)
                .body(isEmptyOrNullString());
    }

    @Test
    void rejectsUnacceptableLoginResponseTypeWithEmptyFrameworkResponse() {
        given()
                .accept("text/plain")
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(406)
                .body(isEmptyOrNullString());
    }

    @Test
    void rejectsUnsupportedLoginRequestTypeWithEmptyFrameworkResponse() {
        given()
                .contentType("text/plain")
                .body("not-json")
                .when().post("/auth/login")
                .then()
                .statusCode(415)
                .body(isEmptyOrNullString());
    }

    @Test
    void returnsDocumentedServerErrorWhenCredentialLookupThrows() {
        AuthResource resource = new AuthResource();
        resource.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw new IllegalStateException("simulated failure");
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";

        Response response = resource.login(request);
        Map<?, ?> body = (Map<?, ?>) response.getEntity();

        assertEquals(500, response.getStatus());
        assertEquals("Internal server error", body.get("message"));
        assertFalse(body.containsKey("exception"));
    }

    @Test
    void returnsDocumentedServerErrorOverHttpWhenCredentialLookupThrows() {
        QuarkusMock.installMockForType(new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw new IllegalStateException("simulated failure");
            }
        }, LegacyDatabase.class);

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(500)
                .body("message", is("Internal server error"))
                .body("exception", isEmptyOrNullString());
    }

    private static void updateStoredPassword(String username, String password) throws SQLException {
        String databaseUrl = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
        String sql = "UPDATE legacy_users SET password = ? WHERE username = ?";
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, password);
            statement.setString(2, username);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Workshop user was not initialized");
            }
        }
    }

    public static class AuthConfigurationProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "AUTH_TOKEN_SECRET", TEST_TOKEN_SECRET,
                    "AUTH_CORS_ORIGINS", "https://approved.example"
            );
        }
    }

    private static String loginAndGetToken(String username, String password) {
        return given()
                .contentType("application/json")
                .body(Map.of("username", username, "password", password))
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract().path("token");
    }

    private static String findStoredPassword(String username) throws SQLException {
        String databaseUrl = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
        String sql = "SELECT password FROM legacy_users WHERE username = ?";
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Workshop user was not initialized");
                }
                return result.getString("password");
            }
        }
    }
}
