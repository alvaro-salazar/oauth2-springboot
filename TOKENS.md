# Tokens: OAuth 2.0, OIDC y JWT

Este documento explica los tipos de tokens que genera Keycloak y cómo se usan.

## 📋 Resumen

| Token | Formato | Estándar | Vida Útil | Uso |
|-------|---------|----------|-----------|-----|
| **Access Token** | JWT | OIDC | 1 hora | Autenticarse en el auth-service |
| **Refresh Token** | Opaco | OAuth 2.0 | ~30 días | Obtener nuevo access_token de Keycloak |

## 🔑 Access Token (JWT)

### ¿Qué es?

El `access_token` es un **JWT (JSON Web Token)** que sigue el estándar **OIDC (OpenID Connect)**.

### Características

- **Formato**: JWT con estructura `header.payload.signature`
- **Estándar**: OIDC (OpenID Connect) - extensión de OAuth 2.0
- **Firmado**: Con RS256 (RSA con SHA-256)
- **Vida útil**: 1 hora (3600 segundos)
- **Validación**: El auth-service valida la firma usando las claves públicas de Keycloak (JWK Set)

### Estructura

```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3OCIsIm5hbWUiOiJKb2huIERvZSJ9.signature
│─────────────────────────────────────────────────────────────────────────│
│                              JWT (3 partes)                              │
```

**Header (decodificado):**
```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-id"
}
```

**Payload (decodificado):**
```json
{
  "sub": "123e4567-e89b-12d3-a456-426614174000",
  "preferred_username": "testuser",
  "email": "testuser@example.com",
  "realm_access": {
    "roles": ["USER", "ADMIN"]
  },
  "iss": "http://localhost:8080/realms/master",
  "aud": "spring-auth-service",
  "exp": 1704067200,
  "iat": 1704063600,
  "jti": "token-id"
}
```

### Claims Estándar OIDC

| Claim | Descripción | Ejemplo |
|-------|-------------|---------|
| `sub` | Subject (ID único del usuario) | `123e4567-e89b-12d3-a456-426614174000` |
| `preferred_username` | Nombre de usuario | `testuser` |
| `email` | Email del usuario | `testuser@example.com` |
| `realm_access.roles` | Roles del realm | `["USER", "ADMIN"]` |
| `iss` | Issuer (quién emitió el token) | `http://localhost:8080/realms/master` |
| `aud` | Audience (para quién es el token) | `spring-auth-service` |
| `exp` | Expiration (timestamp) | `1704067200` |
| `iat` | Issued At (timestamp) | `1704063600` |

### Validación en el Auth Service

El auth-service valida el JWT usando:

1. **JWK Set de Keycloak**: Obtiene las claves públicas desde `/protocol/openid-connect/certs`
2. **Issuer**: Verifica que `iss` coincida con `http://localhost:8080/realms/master`
3. **Expiración**: Verifica que `exp` no haya pasado
4. **Firma**: Valida la firma RS256 usando la clave pública correspondiente

## 🔄 Refresh Token

### ¿Qué es?

El `refresh_token` es un token **opaco** (no es JWT) que sigue el estándar **OAuth 2.0**.

### Características

- **Formato**: String aleatorio opaco (no decodificable)
- **Estándar**: OAuth 2.0
- **Vida útil**: ~30 días (configurable en Keycloak)
- **Uso**: Se envía a Keycloak para obtener un nuevo `access_token`

### ¿Por qué no es JWT?

Los refresh tokens suelen ser opacos porque:
- No necesitan ser decodificados por el cliente
- Solo Keycloak necesita validarlos
- Pueden ser revocados fácilmente (blacklist)
- Son más seguros si se comprometen (no contienen información)

### Uso

```bash
# Obtener nuevo access_token usando refresh_token
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=spring-auth-service" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=<refresh_token>"
```

**Respuesta:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",  // Nuevo JWT
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",  // Nuevo refresh token (opcional)
  "token_type": "Bearer",
  "expires_in": 3600
}
```

## 🔐 Relación entre OAuth 2.0, OIDC y JWT

```
OAuth 2.0 (Protocolo de Autorización)
    │
    ├── Access Token (cualquier formato)
    ├── Refresh Token (opaco)
    └── Authorization Code
            │
            └── OIDC (OpenID Connect) - Extensión de OAuth 2.0
                    │
                    ├── ID Token (JWT con información de identidad)
                    └── Access Token (JWT con claims estándar)
```

### OAuth 2.0

- **Propósito**: Protocolo de autorización
- **Define**: Cómo obtener tokens para acceder a recursos
- **No define**: Formato de los tokens ni información de identidad

### OIDC (OpenID Connect)

- **Propósito**: Extensión de OAuth 2.0 que agrega autenticación e identidad
- **Define**: 
  - Formato de tokens (JWT)
  - Claims estándar (`sub`, `preferred_username`, `email`, etc.)
  - Endpoints estándar (`.well-known/openid-configuration`)
- **Usa**: JWT para transportar información de identidad

### JWT (JSON Web Token)

- **Propósito**: Formato estándar para tokens
- **Estructura**: `header.payload.signature`
- **Ventajas**: 
  - Autocontenido (toda la información está en el token)
  - Verificable (firma criptográfica)
  - No requiere consultas a base de datos para validar

## 🎯 Flujo Completo

```
1. Usuario hace login
   └─> Keycloak emite:
       ├─ access_token (JWT - OIDC) ✅
       └─ refresh_token (opaco - OAuth 2.0) ✅

2. Cliente usa access_token
   └─> Auth Service valida JWT:
       ├─ Verifica firma (JWK Set)
       ├─ Verifica issuer
       ├─ Verifica expiración
       └─ Extrae roles y usuario

3. access_token expira (1 hora)
   └─> Cliente usa refresh_token
       └─> Keycloak emite nuevo access_token

4. refresh_token expira (~30 días)
   └─> Usuario debe hacer login nuevamente
```

## 📚 Referencias

- [OAuth 2.0 RFC 6749](https://oauth.net/2/)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [JWT RFC 7519](https://tools.ietf.org/html/rfc7519)
- [Keycloak Token Documentation](https://www.keycloak.org/docs/latest/securing_apps/#_token)

