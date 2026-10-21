# Propuesta: HU-01 - Token firmado con expiración y claims mínimos

Issue: [#9](https://github.com/user-bcp-student39/workshop-auth-backend/issues/9)

## Por qué importa

El token actual de `POST /auth/login` no identifica el cliente que lo solicitó y no hay garantía verificable de que `expiresIn` coincida con la expiración real del token.

## Alcance

- Claims mínimos del token: `sub`, `client`, `iat` y `exp`.
- Validación de `client` del request y reflejo en el token.
- Coherencia de `expiresIn` con `exp - now` dentro de una tolerancia documentada.
- Documentación de `AUTH_TOKEN_SECRET` y eliminación del secreto por defecto fuera del laboratorio.
- Redacción de logs de tokens y secretos.
- Pruebas de login válido, credenciales inválidas y token expirado.

## Fuera de alcance

- Diseño técnico de la implementación.
- Elección final entre HMAC y una librería JWT (se documenta como decisión).
- `tasks.md`.

## Comportamiento a preservar

Según [docs/ENDPOINTS.md](docs/ENDPOINTS.md):
- Códigos de error 400, 401, 403, 405, 406, 415 y 500, con cuerpo `{"message": ...}`.
- Campos `user` y `debug` de la respuesta.
- `GET /q/health`.

## Cambios de contrato respecto a ENDPOINTS.md

- El token pasa de `sub`, `role`, `iat`, `exp` a exigir también `client`.
- El campo `client` del request pasa de ignorarse a validarse y reflejarse en el token.
- `expiresIn` debe ser coherente con `exp - now` dentro de la tolerancia definida.

## Decisiones abiertas

- Si `client` es obligatorio u opcional en el request.
- Tolerancia exacta para `expiresIn` frente a `exp - now`.
