## ADDED Requirements

### Requirement: Client obligatorio en el login
El sistema MUST exigir el campo `client` en `POST /auth/login` y MUST reflejarlo como claim `client` del token emitido.

#### Scenario: Login válido con client
- **WHEN** se envía `{"username":"student","password":"Workshop123!","client":"angular-workshop"}` con credenciales válidas
- **THEN** la respuesta es `200 OK` con `application/json`, y el token decodificado contiene `client` igual a `angular-workshop`

#### Scenario: client ausente
- **WHEN** se envía un login con credenciales válidas y sin el campo `client`
- **THEN** la respuesta es `400 Bad Request` con cuerpo `{"message":"Client is required"}` y no se emite token

#### Scenario: client vacío tras recorte
- **WHEN** se envía `client` con valor `"   "`
- **THEN** la respuesta es `400 Bad Request` con cuerpo `{"message":"Client is required"}`

#### Scenario: client demasiado largo
- **WHEN** se envía `client` con más de 64 caracteres
- **THEN** la respuesta es `400 Bad Request` con cuerpo `{"message":"Client is invalid"}`

### Requirement: Contrato de login preservado
El sistema MUST mantener los códigos, mensajes y forma de respuesta documentados en ENDPOINTS.md, salvo los cambios de `client` definidos en esta especificación.

#### Scenario: Forma de respuesta exitosa
- **WHEN** el login es válido
- **THEN** el cuerpo contiene `token`, `expiresIn` igual a `3600`, `user` con `username`, `displayName` y `role`, y `debug` igual a `authenticated-with-legacy-flow`

#### Scenario: Credenciales inválidas
- **WHEN** el usuario no existe o la contraseña no coincide
- **THEN** la respuesta es `401 Unauthorized` con `{"message":"Invalid user <usuario> or password"}` y no se emite token

#### Scenario: Error interno sin detalle
- **WHEN** ocurre una excepción inesperada durante el login
- **THEN** la respuesta es `500 Internal Server Error` con `{"message":"Internal server error"}` y el cuerpo no contiene clase, mensaje ni stacktrace
