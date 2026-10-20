# API de autenticacion

Servicio local por defecto: `http://localhost:8080`. El puerto y host se configuran en [application.properties](../src/main/resources/application.properties#L1). Los cuerpos JSON de login usan `application/json`; las rutas de login y sus tipos de contenido estan declarados en [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L15).

## POST /auth/login

### 1. Que hace

Autentica las credenciales de un usuario del workshop y devuelve su perfil junto con un token JWT firmado: [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L35), [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L39), [LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L20).

### 2. Autenticacion

No requiere autenticacion previa. No se envia header `Authorization`; el usuario se autentica con `username` y `password` en el cuerpo. Se requiere `Content-Type: application/json`; el cliente debe aceptar `application/json` en `Accept` ([AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L16), [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L17)).

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| `username` | string | Si | Nombre de usuario. No puede ser null ni quedar vacio despues de `trim()`; el valor original se envia a la consulta. [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L4), [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L29) |
| `password` | string | Si | Contrasena. No puede ser null ni quedar vacia despues de `trim()`; el valor original se usa para verificar el hash. [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L5), [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L32) |
| `client` | string | No | Campo aceptado por el DTO; el endpoint no lo usa para autenticar ni construir la respuesta. [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L6) |

Ejemplo completo, con credenciales locales de workshop ([README.md](../README.md)):

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

La comprobacion de campos no recorta los valores antes de buscar/verificar credenciales ([AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L35)). Las propiedades JSON desconocidas se permiten por configuracion ([application.properties](../src/main/resources/application.properties#L8)).

### 4. Response exitosa

HTTP `200 OK`, con `application/json`, cuando la consulta encuentra al usuario y verifica su contrasena ([AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L39), [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L38)).

| campo | tipo | descripcion |
|---|---|---|
| `token` | string | JWT firmado HS256 por `LegacyTokenUtil`; contiene claims `sub`, `role`, `iat` y `exp`. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L41), [LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L20) |
| `expiresIn` | integer | Valor fijo `3600`, en segundos. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L42) |
| `user` | object | Perfil del usuario autenticado. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L43) |
| `user.username` | string | Nombre de usuario del registro. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L44) |
| `user.displayName` | string | Nombre de presentacion; se aplica `escapeHtml4` antes de serializar. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L45) |
| `user.role` | string | Rol almacenado para el usuario, por ejemplo `STUDENT` o `TRAINER`. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L46) |
| `debug` | string | Valor constante `authenticated-with-legacy-flow`. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L48) |

Ejemplo completo (el token es dinamico y se representa con un marcador):

```json
{
  "token": "<jwt-hs256-generado-por-el-servidor>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

### 5. Respuestas de error

| codigo | cuando ocurre | ejemplo del cuerpo |
|---|---|---|
| `400 Bad Request` | El JSON es `null`. [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L183) | `{"message":"Request is null"}` |
| `400 Bad Request` | `username` es null, falta o queda vacio tras `trim()`. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L29) | `{"message":"Username is required"}` |
| `400 Bad Request` | `password` es null, falta o queda vacia tras `trim()`. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L32) | `{"message":"Password is required"}` |
| `400 Bad Request` | El JSON esta mal formado y Quarkus lo rechaza antes de invocar el metodo. [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L282) | Cuerpo vacio. |
| `401 Unauthorized` | No se encuentra un usuario con las credenciales verificadas. La persistencia tambien convierte sus excepciones de consulta en resultado nulo, por lo que un fallo de base puede verse como `401`. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L37), [LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L99) | `{"message":"Invalid user student or password"}` |
| `403 Forbidden` | Preflight CORS `OPTIONS /auth/login` desde un origen no permitido. [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L305) | Cuerpo vacio. |
| `405 Method Not Allowed` | Se usa otro metodo HTTP, por ejemplo `GET /auth/login`. [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L315) | Cuerpo vacio. |
| `406 Not Acceptable` | `Accept` no acepta JSON, por ejemplo `text/plain`. [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L324) | Cuerpo vacio. |
| `415 Unsupported Media Type` | El request usa un `Content-Type` distinto de JSON. [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L336) | Cuerpo vacio. |
| `500 Internal Server Error` | Una excepcion inesperada escapa del flujo de login, por ejemplo al generar el token. El detalle se registra en el servidor y no se devuelve al cliente. [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L58) | `{"message":"Internal server error"}` |

### 6. Notas de seguridad

- No se exige token previo para llamar a login. En un despliegue accesible por red, transportar las credenciales solo por un canal protegido; este servicio no configura TLS en estas propiedades.
- `AUTH_TOKEN_SECRET` alimenta la configuracion `auth.token.secret` ([application.properties](../src/main/resources/application.properties#L9), [LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L36)). El token se firma con HS256; el secreto no se incluye en el payload JWT.
- El JWT incluye `sub` (username), `role`, `iat` y `exp`; `exp` y el campo de respuesta `expiresIn` usan la duracion de `TOKEN_TTL_SECONDS` ([LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L12), [LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L20), [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L42)). `LegacyTokenUtil.verify` verifica firma y vencimiento ([LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L29)); ninguno de los endpoints HTTP listados consume un bearer token. Los consumidores externos deben coordinar la verificacion del nuevo formato; no exponer el secreto de firma a clientes de navegador.
- Ante un HTTP 500, el cliente recibe solo `{"message":"Internal server error"}`. El servidor registra la excepcion; no devuelve su mensaje, clase ni stacktrace en el cuerpo ([AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L58)).
- Los origenes CORS se configuran con `AUTH_CORS_ORIGINS`; metodos y cabeceras configurados estan en [application.properties](../src/main/resources/application.properties#L5). Un origen no permitido recibe el rechazo de preflight documentado arriba.

## GET /q/health

### 1. Que hace

Permite comprobar si el servicio de autenticacion esta disponible. La ruta la proporciona `quarkus-smallrye-health`, no un recurso Java propio ([pom.xml](../pom.xml#L40)); las pruebas actuales observan estado `UP` sin checks registrados ([AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L154), [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L163)).

### 2. Autenticacion

No requiere autenticacion ni header `Authorization`; la prueba GET obtiene el estado sin enviar ese header ([AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L154)).

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Ninguno | — | No | El endpoint GET no declara ni lee campos de query o cuerpo en este componente. |

Ejemplo: no se envia cuerpo JSON. `{}` representa un objeto vacio solo como referencia de que no hay campos; no es necesario enviarlo.

```json
{}
```

Request HTTP habitual: `GET /q/health` con `Accept: application/json`.

### 4. Response exitosa

HTTP `200 OK`, con `application/json`, cuando no hay health checks registrados y la extension reporta el proceso `UP` ([AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L154), [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L163)).

| campo | tipo | descripcion |
|---|---|---|
| `status` | string | Estado agregado del servicio; actualmente `UP` en las pruebas. |
| `checks` | array | Lista de checks de salud; actualmente vacia porque no hay checks registrados. |

Ejemplo JSON completo observado:

```json
{
  "status": "UP",
  "checks": []
}
```

### 5. Respuestas de error

| codigo | cuando ocurre | ejemplo del cuerpo |
|---|---|---|
| `403 Forbidden` | Preflight CORS `OPTIONS /q/health` desde un origen no permitido. [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L172) | Cuerpo vacio. |

Pregunta abierta: el componente no registra checks que puedan producir estado `DOWN`; no hay evidencia local para documentar un codigo/cuerpo `503` ni las respuestas de otros errores internos de la extension.

### 6. Notas de seguridad

- No requiere autenticacion; cualquier cliente que alcance el host puede consultar el estado basico.
- CORS usa `AUTH_CORS_ORIGINS` y el preflight de origen no permitido se rechaza. CORS no sustituye autenticacion ni control de acceso a red.

## Documentos consultados

- [README.md](../README.md): ejecucion, endpoints y credenciales locales de ejemplo.
- [RETO.md](../RETO.md): alcance y hallazgos de seguridad del laboratorio.
- [REGLAS.md](../REGLAS.md): preservacion del contrato y restricciones del laboratorio.
- [docs/GOOD_PRACTICES.md](GOOD_PRACTICES.md): convenciones de seguridad.
- [HALLAZGOS.md](../HALLAZGOS.md): estado de los hallazgos conocidos.