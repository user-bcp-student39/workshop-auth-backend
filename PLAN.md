# Plan de correccion

Se seleccionan los tres items pendientes de mayor prioridad: H-01 y H-03 (severidad alta), y H-05 (severidad media, exposicion directa de detalles HTTP). Cada apartado es independiente. No se modifica codigo en esta propuesta.

## Item: H-01 — SQL: concatenacion de credenciales

- **Item y estado:** H-01 de `HALLAZGOS.md`, actualmente Pendiente.
- **Problema:** `LegacyDatabase.findByCredentials` concatena `username` en el SELECT y lo ejecuta con `Statement`. Una entrada con sintaxis SQL puede cambiar el predicado de busqueda; la comparacion BCrypt posterior no neutraliza una consulta que devuelve una fila ajena.
- **Cambio propuesto:** parametrizar el username con `PreparedStatement` y `?`. Mantener la verificacion de contrasena con `PasswordHasher.matches` sobre la fila seleccionada. Mantener la forma de retorno y las respuestas HTTP existentes. Limitar este item al acceso SQL; no mezclarlo con la migracion/hash de H-02 ni la separacion de capas de H-07.
- **Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Prueba a escribir antes de tocar esos archivos:** agregar una prueba HTTP que envie un username con un apostrofe como dato literal y compruebe que recibe el 401 documentado, nunca el perfil de otro usuario. Conservar las pruebas de credenciales validas e incorrectas. Usar la SQLite aislada por `AUTH_DB_URL` y no incluir un payload destructivo.
- **Contrato:** preservar ruta, codigos y cuerpos de `POST /auth/login`; no cambiar `docs/ENDPOINTS.md`.
- **Clean Code:** parametrizar en vez de concatenar; mantener la consulta y el mapeo del registro en la responsabilidad de persistencia; no duplicar SQL ni agregar manejo anidado.

## Item: H-03 — Token: Base64 con secreto embebido

- **Item y estado:** H-03 de `HALLAZGOS.md`, actualmente Pendiente.
- **Problema:** `LegacyTokenUtil.create` codifica de forma reversible username, rol, epoch y secreto. El valor de expiracion se genera, pero el backend no firma ni valida el token; alterar claims no se detecta aqui.
- **Cambio propuesto:** sustituir el formato casero por un token firmado estandar con claims explicitos de sujeto, rol y expiracion; el secreto se usa para firmar y no se incluye en el payload. Seleccionar una implementacion JOSE/JWT mantenida compatible con Quarkus y revisar advisories/versiones antes de agregar dependencia. Conservar la configuracion externa existente y usar un secreto exclusivamente ficticio en tests. Extraer generacion/verificacion a una responsabilidad de seguridad pequena; nombrar la duracion como constante en vez de repetir `3600`.
- **Archivos a tocar:** `pom.xml` solo si la implementacion elegida requiere una dependencia; `src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` o una clase de seguridad/token dedicada; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y tests unitarios del codec/verificador si se separa; `docs/ENDPOINTS.md` para documentar el nuevo formato y las garantias reales.
- **Prueba a escribir antes de tocar esos archivos:** caracterizar HTTP 200 y el esquema JSON actual (`token`, `expiresIn`, `user`, `debug`). Despues, con clave ficticia, verificar firma y claims, ausencia del secreto en el payload, rechazo de firma modificada y rechazo de token vencido. No simular como segura una simple decodificacion Base64.
- **Contrato:** este hallazgo requiere cambiar el formato del valor de `token`; el nombre/campo y demas respuestas deben conservarse. Antes de activar el formato nuevo, coordinar la verificacion con los consumidores externos, que estan fuera de este repositorio; actualizar `docs/ENDPOINTS.md` solo para reflejar el formato aprobado y compatible.
- **Clean Code:** separar creacion y validacion del token de HTTP/persistencia, usar claims y duracion con nombre, y no agregar una dependencia criptografica sin justificacion de seguridad/compatibilidad.

## Item: H-05 — Errores: respuesta expone detalles internos

- **Item y estado:** H-05 de `HALLAZGOS.md`, actualmente Pendiente.
- **Problema:** el catch de `AuthResource.login` envia `error.getMessage()` y `error.getClass().getName()` al cliente. Mensajes de infraestructura pueden revelar detalles internos; las excepciones de consulta que `LegacyDatabase` ya absorbe quedan fuera de este catch.
- **Cambio propuesto:** ante excepciones inesperadas, devolver un mensaje generico y estable con HTTP 500, y registrar el detalle solo en logging del servidor. No modificar la respuesta de validacion 400 ni la de credenciales 401. Mantener el cambio dentro del manejo de errores del Resource; no incluir la refactorizacion completa de capas de H-07.
- **Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/AuthResource.java`; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`; `docs/ENDPOINTS.md` para sustituir el ejemplo/cuerpo actual de 500 por el cuerpo generico acordado.
- **Prueba a escribir antes de tocar esos archivos:** actualizar la prueba HTTP con `QuarkusMock` y la prueba directa de `login` para inyectar una excepcion con mensaje/clase reconocibles; comprobar HTTP 500 y que el cuerpo no contiene ninguno de esos detalles. Mantener las pruebas existentes de 400 y 401. Acordar en la prueba y documento el mensaje generico exacto antes de cambiar la implementacion.
- **Contrato:** este item pide explicitamente dejar de exponer los detalles del 500, por lo que esa respuesta documentada debe cambiar. Mantener rutas, respuestas 200/400/401 y el resto del esquema intactos.
- **Clean Code:** conservar un solo punto de mapeo de excepciones, usar un mensaje constante con nombre y logging controlado; evitar duplicar respuestas genericas en ramas distintas.