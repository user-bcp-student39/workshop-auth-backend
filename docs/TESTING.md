# Pruebas de endpoints

## Matriz

La matriz corresponde al contrato actual de [ENDPOINTS.md](ENDPOINTS.md). Los resultados esperados se basan en respuestas documentadas; los errores del framework se esperan sin cuerpo, como se observo en HTTP.

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /q/health` | `reportsHealthAsUp` | Camino feliz | HTTP 200, `status=UP`. |
| `GET /q/health` | `reportsNoHealthChecksWhenNoneAreRegistered` | Borde | HTTP 200, `checks=[]`. |
| `GET /q/health` | `rejectsHealthPreflightFromUnapprovedOrigin` | Error | Preflight CORS no permitido: HTTP 403, cuerpo vacio. |
| `POST /auth/login` | `logsInWithWorkshopUser` | Camino feliz | HTTP 200; perfil STUDENT, token, `expiresIn=3600`, `debug` documentado. |
| `POST /auth/login` | `logsInWithWorkshopTrainer` | Camino feliz / borde | HTTP 200; perfil TRAINER; `client` omitido. |
| `POST /auth/login` | `storesWorkshopPasswordAsBcryptHash` | Borde / regresion | La contrasena persistida no es texto plano y tiene formato BCrypt. |
| `POST /auth/login` | `hashesSamePasswordWithDistinctSalts` | Borde / regresion | Dos hashes de la misma contrasena son distintos y ambos verifican la contrasena original. |
| `POST /auth/login` | `migratesExistingPasswordOnlyOnce` | Borde / regresion | Password legacy migra una vez a BCrypt y login conserva HTTP 200. |
| `POST /auth/login` | `returnsBadRequestForNullLoginRequest` | Error | HTTP 400, `{"message":"Request is null"}`. |
| `POST /auth/login` | `returnsBadRequestForEmptyUsername` | Error / borde | HTTP 400, `{"message":"Username is required"}`. |
| `POST /auth/login` | `returnsBadRequestForMissingUsername` | Error / borde | HTTP 400, `{"message":"Username is required"}`. |
| `POST /auth/login` | `returnsBadRequestForNullUsername` | Error / borde | HTTP 400, `{"message":"Username is required"}`. |
| `POST /auth/login` | `returnsBadRequestForWhitespaceUsername` | Error / borde | HTTP 400, `{"message":"Username is required"}`. |
| `POST /auth/login` | `returnsBadRequestForEmptyPassword` | Error / borde | HTTP 400, `{"message":"Password is required"}`. |
| `POST /auth/login` | `returnsBadRequestForMissingPassword` | Error / borde | HTTP 400, `{"message":"Password is required"}`. |
| `POST /auth/login` | `returnsBadRequestForNullPassword` | Error / borde | HTTP 400, `{"message":"Password is required"}`. |
| `POST /auth/login` | `returnsBadRequestForWhitespacePassword` | Error / borde | HTTP 400, `{"message":"Password is required"}`. |
| `POST /auth/login` | `rejectsMalformedLoginJsonWithEmptyFrameworkResponse` | Error | HTTP 400 antes del Resource, cuerpo vacio. |
| `POST /auth/login` | `rejectsIncorrectPasswordWithDocumentedResponse` | Error | HTTP 401, mensaje para username `student`. |
| `POST /auth/login` | `rejectsUnknownUserWithDocumentedResponse` | Error | HTTP 401, `{"message":"Invalid user missing or password"}`. |
| `POST /auth/login` | `rejectsLoginPreflightFromUnapprovedOriginWithEmptyResponse` | Error | Preflight CORS no permitido: HTTP 403, cuerpo vacio. |
| `POST /auth/login` | `rejectsWrongLoginMethodWithEmptyFrameworkResponse` | Error | `GET /auth/login`: HTTP 405, cuerpo vacio. |
| `POST /auth/login` | `rejectsUnacceptableLoginResponseTypeWithEmptyFrameworkResponse` | Error | `Accept: text/plain`: HTTP 406, cuerpo vacio. |
| `POST /auth/login` | `rejectsUnsupportedLoginRequestTypeWithEmptyFrameworkResponse` | Error | `Content-Type: text/plain`: HTTP 415, cuerpo vacio. |
| `POST /auth/login` | `returnsDocumentedServerErrorWhenCredentialLookupThrows` | Error | HTTP 500, `message` y `exception` corresponden a la excepcion lanzada. |
| `POST /auth/login` | `returnsDocumentedServerErrorOverHttpWhenCredentialLookupThrows` | Error / integracion | HTTP 500 con los campos `message` y `exception` documentados, a traves de RestAssured. |
| `POST /auth/login` | `usesTokenSecretFromExternalConfiguration` | Borde / configuracion | HTTP 200; al decodificar Base64 se encuentra el secreto ficticio configurado para test. |
| `POST /auth/login` | `rejectsCorsOriginOutsideConfiguredAllowlist` | Error / seguridad | Preflight desde origen no permitido: HTTP 403. |
| `POST /auth/login` | `allowsConfiguredCorsOrigin` | Camino feliz / seguridad | Preflight permitido: HTTP 200 y `Access-Control-Allow-Origin` coincide con el origen configurado. |

## Codigo de los tests

El codigo ejecutable completo de los 29 tests, imports, perfil de configuracion y helpers SQLite esta en [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). El perfil usa solamente `test-only-secret`, `https://approved.example` y una SQLite bajo `target/`; no requiere secretos reales. Los fragmentos siguientes reproducen los casos principales de esa fuente.

### Camino feliz y bordes

```java
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
void usesTokenSecretFromExternalConfiguration() {
    String token = given()
            .contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(200)
            .extract().path("token");
    String tokenPayload = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);

    assertTrue(tokenPayload.endsWith("|test-only-secret"));
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
```

Los metodos añadidos para completar la matriz son:

```java
@Test
void reportsHealthAsUp() {
    given().when().get("/q/health")
            .then().statusCode(200).body("status", is("UP"));
}

@Test
void reportsNoHealthChecksWhenNoneAreRegistered() {
    given().when().get("/q/health")
            .then().statusCode(200).body("checks", hasSize(0));
}

@Test
void rejectsHealthPreflightFromUnapprovedOrigin() {
    given()
            .header("Origin", "https://unapproved.example")
            .header("Access-Control-Request-Method", "GET")
            .when().options("/q/health")
            .then().statusCode(403).body(isEmptyOrNullString());
}

@Test
void returnsBadRequestForNullLoginRequest() {
    given().contentType("application/json").body("null")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Request is null"));
}

@Test
void returnsBadRequestForEmptyUsername() {
    given().contentType("application/json")
            .body("{\"username\":\"\",\"password\":\"x\"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Username is required"));
}

@Test
void returnsBadRequestForMissingUsername() {
    given().contentType("application/json").body("{\"password\":\"x\"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Username is required"));
}

@Test
void returnsBadRequestForNullUsername() {
    given().contentType("application/json")
            .body("{\"username\":null,\"password\":\"x\"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Username is required"));
}

@Test
void returnsBadRequestForWhitespaceUsername() {
    given().contentType("application/json")
            .body("{\"username\":\"  \",\"password\":\"x\"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Username is required"));
}

@Test
void returnsBadRequestForEmptyPassword() {
    given().contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"\"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Password is required"));
}

@Test
void returnsBadRequestForMissingPassword() {
    given().contentType("application/json").body("{\"username\":\"student\"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Password is required"));
}

@Test
void returnsBadRequestForNullPassword() {
    given().contentType("application/json")
            .body("{\"username\":\"student\",\"password\":null}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Password is required"));
}

@Test
void returnsBadRequestForWhitespacePassword() {
    given().contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"  \"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Password is required"));
}

@Test
void rejectsMalformedLoginJsonWithEmptyFrameworkResponse() {
    given().contentType("application/json").body("{\"username\":")
            .when().post("/auth/login")
            .then().statusCode(400).body(isEmptyOrNullString());
}

@Test
void rejectsUnknownUserWithDocumentedResponse() {
    given().contentType("application/json")
            .body("{\"username\":\"missing\",\"password\":\"bad\"}")
            .when().post("/auth/login")
            .then().statusCode(401)
            .body("message", is("Invalid user missing or password"));
}

@Test
void rejectsLoginPreflightFromUnapprovedOriginWithEmptyResponse() {
    given()
            .header("Origin", "https://unapproved.example")
            .header("Access-Control-Request-Method", "POST")
            .when().options("/auth/login")
            .then().statusCode(403).body(isEmptyOrNullString());
}

@Test
void rejectsWrongLoginMethodWithEmptyFrameworkResponse() {
    given().when().get("/auth/login")
            .then().statusCode(405).body(isEmptyOrNullString());
}

@Test
void rejectsUnacceptableLoginResponseTypeWithEmptyFrameworkResponse() {
    given().accept("text/plain").contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
            .when().post("/auth/login")
            .then().statusCode(406).body(isEmptyOrNullString());
}

@Test
void rejectsUnsupportedLoginRequestTypeWithEmptyFrameworkResponse() {
    given().contentType("text/plain").body("not-json")
            .when().post("/auth/login")
            .then().statusCode(415).body(isEmptyOrNullString());
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
    assertEquals("simulated failure", body.get("message"));
    assertEquals(IllegalStateException.class.getName(), body.get("exception"));
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
            .body("message", is("simulated failure"))
            .body("exception", is(IllegalStateException.class.getName()));
}
```

Los casos anteriores que ya existian (`logsInWithWorkshopUser`, `logsInWithWorkshopTrainer`, `rejectsIncorrectPasswordWithDocumentedResponse`, almacenamiento/migracion BCrypt, secreto externo y CORS permitido/no permitido) permanecen en la misma fuente enlazada.

## Comandos y resultados

Directorio de ejecucion: raiz de `workshop-auth-backend/`, junto a `pom.xml`. JDK 21; Maven 3.10.0. SQLite quedo aislada en `target/` mediante `AUTH_DB_URL`. `AuthConfigurationProfile` aporta un secreto ficticio y un origen CORS de prueba. JaCoCo 0.8.12 se ejecuto mediante `mvn verify`; no se excluyo ni desactivo ningun quality gate.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:AUTH_DB_URL = "jdbc:sqlite:./target/docs-testing-final-20261019.db"
mvn verify
```

- Codigo de salida: `0` (`BUILD SUCCESS`).
- Resultado Surefire: 29 tests, 0 fallos, 0 errores, 0 omitidos.
- Cobertura JaCoCo por lineas: `AuthResource` 25/25 (100.0%); `LegacyDatabase` 48/51 (94.1%); `PasswordHasher` 5/5 (100.0%); `LegacyTokenUtil` 4/4 (100.0%); paquete `com.bcp.workshop.auth` 85/88 (96.6%). Todas las clases de produccion cubiertas superan la meta del 85% de REGLAS.md.
- Reportes: `target/site/jacoco/index.html` y `target/site/jacoco/jacoco.csv`.
- Metodo: JaCoCo `LINE` por clase y paquete, `covered / (covered + missed)`, redondeado a una decimal. La prueba HTTP 500 se ejecuto por separado antes de `verify`: `mvn '-Dtest=AuthResourceTest#returnsDocumentedServerErrorOverHttpWhenCredentialLookupThrows' test`; codigo de salida `0`, 1 test, 0 fallos y 0 errores.
- No se modifico codigo de produccion. No se ejecutaron CodeQL ni Dependency Review en esta tarea.

## Fallos de ejecucion

Ninguno en la ultima ejecucion completa de `mvn verify` ni en la prueba HTTP 500 enfocada.

## Fallos conocidos del producto

- H-01 sigue pendiente: `LegacyDatabase.findByCredentials` concatena `username` en SQL. Las pruebas de este documento no intentan explotarlo ni presentan el comportamiento como aceptable.
- H-03 sigue pendiente: el token es Base64 reversible y no tiene firma.
- H-05 sigue pendiente: el `500` expone el mensaje y la clase de excepcion. Los tests caracterizan el contrato actual, no aprueban esa exposicion.

## Pendientes y pruebas propuestas

- El error `500` esta cubierto por una prueba HTTP con `QuarkusMock` y por una prueba directa del metodo. La prueba conserva el cuerpo actual; una vez que H-05 se corrija, sustituir sus expectativas por el cuerpo generico acordado.
- La consulta de persistencia convierte sus propias excepciones en resultado nulo y login las presenta como `401`; no se simula por separado una caida real de SQLite. No se agrega una prueba que normalice ese defecto como comportamiento deseable.
- No hay health checks registrados, por lo que `DOWN`/`503` no puede reproducirse con el codigo actual. Si se incorpora un check, definir y probar primero el resultado esperado.
- Las respuestas de framework que no estan descritas en `docs/ENDPOINTS.md` quedan fuera de esta bateria; no se inventaron codigos ni cuerpos.

## Documentos consultados

- [docs/ENDPOINTS.md](ENDPOINTS.md)
- [REGLAS.md](../REGLAS.md)
- [docs/GOOD_PRACTICES.md](GOOD_PRACTICES.md)
- [HALLAZGOS.md](../HALLAZGOS.md)
- [README.md](../README.md)
- [pom.xml](../pom.xml)
- [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java)
- [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java)
- [application.properties](../src/main/resources/application.properties)
- [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java)
- [LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java)
- [LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java)
- [PasswordHasher.java](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java)
