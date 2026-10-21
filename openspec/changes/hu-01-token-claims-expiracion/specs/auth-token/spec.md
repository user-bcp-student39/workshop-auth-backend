## ADDED Requirements

### Requirement: Claims mínimos del token
El token emitido MUST contener los claims `sub`, `client`, `iat` y `exp`, y MUST conservar el claim `role`.

#### Scenario: Token con claims completos
- **WHEN** se realiza un login válido
- **THEN** el token decodificado contiene `sub` igual al username, `client` igual al valor enviado, `role`, `iat` y `exp`

### Requirement: Coherencia entre expiresIn y exp
El sistema MUST garantizar que `expiresIn` coincida con `exp - now` dentro de una tolerancia de 5 segundos, y que `exp - iat` sea igual a `expiresIn`.

#### Scenario: expiresIn coherente con exp
- **WHEN** se realiza un login válido y se compara `exp - now` en el momento de la respuesta
- **THEN** la diferencia con `expiresIn` es menor o igual a 5 segundos

#### Scenario: Ventana de vida del token
- **WHEN** se decodifica el token emitido
- **THEN** `exp - iat` es igual a `3600`

### Requirement: Rechazo de token expirado
La verificación de tokens MUST rechazar cualquier token cuyo `exp` sea anterior al momento de la verificación.

#### Scenario: Token expirado
- **WHEN** se verifica un token firmado válido cuyo `exp` ya pasó
- **THEN** la verificación falla y el token no se acepta

#### Scenario: Token vigente
- **WHEN** se verifica un token firmado válido cuyo `exp` no ha pasado
- **THEN** la verificación acepta el token

### Requirement: Secreto de firma configurado
El sistema MUST leer el secreto desde `AUTH_TOKEN_SECRET`, MUST NOT usar un secreto por defecto en perfil de producción y MUST documentar el uso del secreto de prueba como exclusivo de laboratorio.

#### Scenario: Arranque en producción sin secreto
- **WHEN** el servicio arranca con perfil de producción y `AUTH_TOKEN_SECRET` no está definido
- **THEN** el arranque falla con un error de configuración

#### Scenario: Secreto documentado
- **WHEN** se revisa la documentación del cambio
- **THEN** se describe `AUTH_TOKEN_SECRET`, su obligatoriedad en producción y que cualquier valor de prueba es exclusivo de laboratorio

### Requirement: Sin datos sensibles en logs
El sistema MUST NOT registrar tokens, secretos ni contraseñas en ningún nivel de log.

#### Scenario: Login exitoso sin fuga en logs
- **WHEN** se realiza un login válido
- **THEN** los logs del servicio no contienen el token emitido, el secreto ni la contraseña enviada

#### Scenario: Error interno sin fuga en logs
- **WHEN** ocurre un error durante la generación del token
- **THEN** el log registra el error sin incluir el token, el secreto ni la contraseña

### Requirement: Decisión de firma documentada
La documentación del cambio MUST registrar la decisión entre conservar HMAC o adoptar una librería JWT, con su justificación.

#### Scenario: Decisión registrada
- **WHEN** se revisa la documentación del cambio
- **THEN** existe una sección con la opción elegida, la alternativa descartada y el motivo
