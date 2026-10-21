# Specs: auth-token

## REQ-1 Claims obligatorios
El token debe contener `sub`, `client`, `iat` y `exp`.
- Escenario: dado un login válido con `client: "angular-workshop"`, cuando se decodifica el token, entonces contiene los cuatro claims y `client` vale `angular-workshop`.

## REQ-2 Validación de `client`
Un `client` ausente o vacío se rechaza con 400.
- Escenario: dado un login sin `client`, entonces responde `400` con mensaje de error.
- Nota: pendiente confirmar si `client` es obligatorio (decisión abierta).

## REQ-3 Coherencia de `expiresIn`
`expiresIn` debe coincidir con `exp - iat` dentro de la tolerancia documentada.
- Escenario: dado un login válido, cuando se compara `expiresIn` con `exp - now`, entonces la diferencia está dentro de la tolerancia.

## REQ-4 Errores preservados
- Escenario: dado credenciales inválidas, entonces responde `401` con `{"message":"Invalid user student or password"}`.
- Escenario: dado un token expirado, entonces la verificación lo rechaza.

## REQ-5 Redacción
No aparecen tokens, secretos ni contraseñas en logs.
- Escenario: dado un login exitoso, cuando se revisan los logs, entonces no contienen el token ni el secreto.

## REQ-6 Secreto configurado
`AUTH_TOKEN_SECRET` no usa un valor por defecto fuera de laboratorio y está documentado.
