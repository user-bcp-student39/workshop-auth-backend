# Propuesta: HU-01 - Token firmado con expiración y claims mínimos

## Issue de referencia
[#9 - HU-01 - Token firmado con expiración y claims mínimos](https://github.com/user-bcp-student39/workshop-auth-backend/issues/9)

## Por qué importa
`POST /auth/login` devuelve un JWT HS256 con `sub`, `role`, `iat` y `exp`. El campo `client` del request se acepta pero se ignora, por lo que el token no permite saber desde qué cliente se autenticó. Además, `expiresIn` no se contrasta con `exp`, y el secreto de firma depende de `AUTH_TOKEN_SECRET` sin una regla explícita para entornos no de laboratorio.

## Alcance
- Exigir `client` en el login, validarlo y reflejarlo como claim `client` del token.
- Garantizar que `sub`, `client`, `iat` y `exp` estén presentes en todo token emitido.
- Garantizar que `expiresIn` coincida con `exp - now` dentro de una tolerancia documentada.
- Probar login válido, credenciales inválidas y token expirado.
- Exigir `AUTH_TOKEN_SECRET` fuera de sandbox/pruebas y documentarlo.
- Garantizar que logs no contengan token, secreto ni contraseña.
- Documentar la decisión HMAC frente a librería JWT (sin implementar cambio de librería).

## Fuera de alcance
- Nuevos endpoints de validación de token, refresh o logout.
- Registro o lista de clientes permitidos (allowlist): no está definida en el Issue.
- Cambio de algoritmo de firma o adopción de otra librería JWT.
- Cambios en persistencia, hashing de contraseñas, CORS o en los repositorios frontend y API de requisitos.
- Eliminar el campo `debug` de la respuesta (se preserva; revisar en otra historia).

## Comportamiento que debe preservarse
Según [docs/ENDPOINTS.md](docs/ENDPOINTS.md):
- Ruta `POST /auth/login`, `Content-Type` y `Accept` JSON.
- Forma de la respuesta 200: `token`, `expiresIn` (3600), `user` (`username`, `displayName` escapado, `role`) y `debug`.
- Claim `role` en el token.
- Mensajes existentes: `Request is null`, `Username is required`, `Password is required` y `Invalid user <usuario> or password`.
- Códigos 403, 405, 406 y 415 de CORS y de media types.
- Error 500 con cuerpo genérico `Internal server error`, sin detalle interno.
- `username` y `password` se consultan sin recorte; la validación sí usa `trim()`.
- Propiedades JSON desconocidas siguen permitidas.

## Cambios de contrato frente a ENDPOINTS.md
ENDPOINTS.md (baseline del Día 1) documenta dos puntos que el Issue cambia de forma explícita:
1. `client` pasa de opcional y sin uso a obligatorio. Un request sin `client`, o con `client` vacío tras `trim()`, responde `400` con `{"message":"Client is required"}`. Motivo: el Issue exige obtenerlo, validarlo y reflejarlo.
2. El token pasa a incluir el claim `client`. Los claims documentados `sub`, `role`, `iat` y `exp` se conservan.

Al implementar, ENDPOINTS.md debe actualizarse con estos dos cambios. Riesgo: cualquier cliente que no envíe `client` dejará de funcionar; no se verificó el frontend porque está fuera de alcance.

## Decisiones confirmadas
- Tolerancia entre `expiresIn` y `exp - now`: 5 segundos.
- Validación de `client`: no vacío tras `trim()` y máximo 64 caracteres.
- Sin `AUTH_TOKEN_SECRET` en producción: fallo al arrancar.

## Decisiones pendientes
- HMAC frente a librería JWT: se documenta la decisión en esta propuesta; la elección queda pendiente de revisión.
