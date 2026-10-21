# Diseño: HU-01 - Token firmado con expiración y claims mínimos

Referencias: [Issue #9](https://github.com/user-bcp-student39/workshop-auth-backend/issues/9), [proposal.md](proposal.md), [specs/auth-login/spec.md](specs/auth-login/spec.md), [specs/auth-token/spec.md](specs/auth-token/spec.md), [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md).

## Contexto técnico
- `AuthResource.login` valida `request`, `username` y `password`, consulta `LegacyDatabase.findByCredentials` y devuelve el token. Hoy `LoginRequest.client` se acepta y se ignora.
- `LegacyTokenUtil.create(username, role)` firma HS256 con `com.auth0.jwt` y añade `sub`, `role`, `iat` y `exp`. La librería ya está en uso; no hay que añadir dependencia.
- `TOKEN_TTL_SECONDS = 3600` es la fuente de `exp` y de `expiresIn` (`AuthResource` la lee directamente), por lo que `exp - iat` y `expiresIn` coinciden por construcción.
- El secreto se lee en cada firma/verificación desde `auth.token.secret=${AUTH_TOKEN_SECRET}` ([application.properties](../../../src/main/resources/application.properties)). No hay valor por defecto en `application.properties`. Si la variable falta, la lectura lanza una excepción en tiempo de uso, no al arrancar; `AuthResource` la convierte en `500`. Esto contradice la propuesta, que pide fallo de arranque: el diseño debe corregirlo (ver decisiones).
- `LOG.error("Unexpected error during authentication", error)` registra la excepción, no el token ni la contraseña. Hoy no hay prueba que lo verifique.
- `AuthResourceTest` ya cubre: emisión de token con claims (`issuesSignedTokenWithClaimsAndExpiration`), token expirado (`rejectsExpiredSignedToken`), firma alterada, errores 400/401/403/405/406/415/500 y `expiresIn` igual a `3600`. Usa `AUTH_TOKEN_SECRET` de prueba con `TEST_TOKEN_SECRET`.

## Goals
- Exigir `client` en `POST /auth/login`, validarlo y reflejarlo como claim `client`.
- Mantener el resto del contrato documentado en ENDPOINTS.md.
- Hacer observable que el secreto es obligatorio en producción.
- Probar la ausencia de datos sensibles en logs.

## Non-goals
- Lista de clientes permitidos, endpoints de validación de token, refresh o logout.
- Cambiar el algoritmo de firma o la librería JWT.
- Cambiar persistencia, hashing, CORS o `debug`.
- Implementar código en este paso.

## Contratos protegidos
- Forma de la respuesta 200 (`token`, `expiresIn`, `user`, `debug`) y valor `3600`.
- Claim `role` y `sub` (username).
- Mensajes y códigos 400 (`Request is null`, `Username is required`, `Password is required`), 401 (`Invalid user <usuario> or password`), 403/405/406/415 y 500 genérico.
- Consulta de username y password sin recorte; la validación sí usa `trim()`.
- Cambios de contrato explícitos y aprobados: `client` obligatorio (400 `Client is required`), claim `client` en el token, y 400 `Client is invalid` por longitud superior a 64.

## Componentes afectados
- [AuthResource.java](../../../src/main/java/com/bcp/workshop/auth/AuthResource.java): validación de `client` y paso del valor al emisor de token.
- [LegacyTokenUtil.java](../../../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java): firma con claim `client` y verificación de expiración.
- [LoginRequest.java](../../../src/main/java/com/bcp/workshop/auth/LoginRequest.java): sin cambio de forma; ya expone `client`.
- [application.properties](../../../src/main/resources/application.properties): configuración de `auth.token.secret` y perfil de producción.
- [AuthResourceTest.java](../../../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java): pruebas de caracterización y nuevas pruebas.
- [ENDPOINTS.md](../../../docs/ENDPOINTS.md): actualización del contrato tras implementar.

## Decisiones técnicas y alternativas
1. **Orden de validación de `client`:** antes de la consulta a base de datos, junto a `username` y `password`. Alternativa: después de autenticar. Se descarta para no ejecutar bcrypt en peticiones que ya son inválidas, y porque `400` debe prevalecer sobre `401` en peticiones sin `client`. Trade-off: una petición sin `client` con contraseña incorrecta recibe `400` y no `401`; es un cambio de contrato a documentar.
2. **Valor de `client` en el token:** se guarda tal como se envió tras validar con `trim()`, siguiendo la spec (`client` igual al valor enviado). Alternativa: guardar el valor recortado. Trade-off: menos normalización a cambio de coherencia con la spec.
3. **Constantes:** `CLIENT_MAX_LENGTH = 64` y tolerancia de 5 segundos como constantes con nombre, conforme a REGLAS.md (sin números mágicos).
4. **Secreto obligatorio en producción:** se implementa una comprobación explícita al arrancar (validar `auth.token.secret` en el arranque cuando el perfil es `prod`). Alternativa: dejar el comportamiento actual (fallo en primera firma, `500`). Se descarta porque `500` en login no cumple la spec de fallo de arranque. Trade-off: una comprobación más en el arranque.
5. **HMAC frente a librería JWT:** se mantiene HS256 con `com.auth0.jwt`, que ya está en uso. Alternativa: cambiar a otra librería. Se descarta en esta historia por alcance; la decisión queda documentada aquí como exigía el Issue.
6. **Tolerancia `expiresIn` vs `exp - now`:** como ambos vienen de `TOKEN_TTL_SECONDS`, la prueba mide la diferencia en el momento de la respuesta con tolerancia de 5 segundos; no se requiere lógica nueva en producción.

## Estrategia incremental
1. Confirmar pruebas de caracterización verdes sobre el código actual (sin cambios de comportamiento).
2. Añadir pruebas nuevas en rojo: `client` ausente, vacío y demasiado largo; claim `client` presente; ausencia de secreto y de datos sensibles en logs.
3. Implementar validación de `client` y claim en `AuthResource` y `LegacyTokenUtil`.
4. Implementar la comprobación de secreto en arranque para perfil de producción.
5. Actualizar ENDPOINTS.md con los cambios de contrato y documentar la decisión HMAC.
6. Verificar cobertura del código modificado (meta 85%, REGLAS.md) y JaCoCo sin silenciar.

## Pruebas de caracterización necesarias antes de modificar
- Login válido: respuesta 200 con forma completa y `expiresIn` `3600` (ya existe `logsInWithWorkshopUser`).
- Credenciales inválidas y usuario desconocido: 401 con mensaje exacto (ya existe).
- Errores 400 de `username` y `password` (ya existen). Verificar que las peticiones de esas pruebas envían `client`; si no lo envían, el cambio de orden alterará su resultado y hay que ajustarlas.
- Token expirado y firma alterada (ya existen).
- Ausencia de token, secreto y contraseña en logs: no existe; añadir antes de tocar el logger.
- Comportamiento actual sin `client`: documentar como caracterización (`200` hoy) antes de cambiarlo.

## Riesgos y trade-offs
- Clientes que no envían `client` dejarán de funcionar. El frontend de otro repositorio no se ha verificado; queda fuera de alcance y debe revisarse antes de desplegar.
- El cambio de orden de validación puede alterar respuestas de peticiones mal formadas; se mitiga con caracterización previa.
- Tolerancia de 5 segundos y límite de 64 caracteres están confirmados; cualquier cambio posterior requiere actualizar este documento.
- Las pruebas de logs son frágiles si el formato del log cambia; se limitan a comprobar ausencia de cadenas sensibles.

## Rollback
- Revertir los commits del cambio en `main`. El formato del token es aditivo (`client`) y los tokens emitidos siguen válidos hasta su `exp` (3600 segundos).
- No hay migración de datos ni de base de datos; el rollback no requiere script adicional.
- Si la comprobación de secreto en arranque causa problemas, desactivarla vuelve al comportamiento previo (`500` en login sin secreto).
