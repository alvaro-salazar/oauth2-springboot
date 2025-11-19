# Comunicación entre Microservicios

Este documento explica qué tokens usar para comunicación entre microservicios.

## 🎯 Respuesta Corta

Para comunicación **service-to-service** (microservicio a microservicio), usa:

✅ **Client Credentials Grant** → Obtiene un **Service Account Token (JWT)**

**NO uses** el `access_token` del usuario (user token) para comunicación entre servicios.

## 📊 Comparación de Tokens

| Escenario | Token a Usar | Grant Type | Contexto |
|-----------|--------------|------------|----------|
| **Usuario → Microservicio** | User Access Token (JWT) | `password` o `authorization_code` | El usuario está autenticado |
| **Microservicio → Microservicio** | Service Account Token (JWT) | `client_credentials` | Sin contexto de usuario |
| **Microservicio → Keycloak Admin API** | Service Account Token (JWT) | `client_credentials` | Operaciones administrativas |

## 🔑 Service Account Token (Client Credentials Grant)

### ¿Qué es?

Un token JWT obtenido usando **Client Credentials Grant** que representa al **microservicio mismo**, no a un usuario.

### Características

- **Grant Type**: `client_credentials`
- **Formato**: JWT (igual que user tokens)
- **Contexto**: Sin usuario (no tiene `sub` de usuario)
- **Vida útil**: Típicamente más largo que user tokens (ej: 1 hora)
- **Claims**: Incluye información del cliente, no del usuario

### Estructura del Token

**Payload típico (decodificado):**
```json
{
  "iss": "http://localhost:8080/realms/master",
  "aud": "spring-auth-service",
  "exp": 1704067200,
  "iat": 1704063600,
  "azp": "spring-auth-service",  // Authorized party (el cliente)
  "realm_access": {
    "roles": ["manage-users", "view-users"]
  },
  "resource_access": {
    "spring-auth-service": {
      "roles": ["service-account"]
    }
  }
}
```

**Nota**: No tiene `sub` (subject) porque no representa a un usuario.

### Cómo Obtenerlo

```bash
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=spring-auth-service" \
  -d "client_secret=tu-client-secret"
```

**Respuesta:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",  // Service Account JWT
  "token_type": "Bearer",
  "expires_in": 3600
}
```

**Nota**: No incluye `refresh_token` porque los service accounts no necesitan refresh tokens (pueden obtener nuevos tokens cuando quieran).

## 🔄 Casos de Uso

### 1. Microservicio A → Microservicio B

**Escenario**: El `auth-service` necesita llamar a otro microservicio (ej: `user-service`).

**Solución**:
```java
// En auth-service
String serviceToken = getServiceAccountToken(); // Client Credentials Grant

// Llamar a otro microservicio
HttpHeaders headers = new HttpHeaders();
headers.setBearerAuth(serviceToken);
HttpEntity<Void> request = new HttpEntity<>(headers);
restTemplate.exchange("http://user-service/api/users", HttpMethod.GET, request, UserDTO.class);
```

**El otro microservicio valida el token**:
- Verifica la firma (JWK Set de Keycloak)
- Verifica que el `azp` (authorized party) sea un cliente confiable
- Verifica roles del service account

### 2. Microservicio → Keycloak Admin API

**Escenario**: El `auth-service` necesita crear usuarios en Keycloak (ya implementado).

**Solución** (ya implementada en `KeycloakService`):
```java
// Obtener token de service account
String adminToken = getAdminToken(); // Usa client_credentials

// Llamar a Keycloak Admin API
HttpHeaders headers = new HttpHeaders();
headers.setBearerAuth(adminToken);
// ... crear usuario en Keycloak
```

### 3. Usuario → Microservicio (con contexto de usuario)

**Escenario**: Un usuario hace una petición que requiere llamar a otro microservicio con su contexto.

**Opciones**:

**Opción A: Forward del User Token** (Recomendado)
```java
// El microservicio A recibe el user token y lo reenvía al microservicio B
@GetMapping("/users")
public ResponseEntity<List<UserDTO>> getUsers(
    @AuthenticationPrincipal Jwt jwt) {
    
    // Reenviar el token del usuario al otro microservicio
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(jwt.getTokenValue()); // Token del usuario
    // ... llamar a otro microservicio
}
```

**Opción B: Service Account Token** (Si no necesitas contexto de usuario)
```java
// Si el otro microservicio no necesita saber quién es el usuario
String serviceToken = getServiceAccountToken();
// ... llamar con service token
```

## 🏗️ Arquitectura Recomendada

```
┌─────────────┐
│   Usuario    │
└──────┬───────┘
       │ User Token (JWT)
       ▼
┌─────────────────┐
│  Microservicio  │  ──Service Token──▶  ┌─────────────────┐
│   A (Frontend)  │                       │  Microservicio  │
└─────────────────┘                       │   B (Backend)   │
       │                                  └─────────────────┘
       │ User Token (forward)
       ▼
┌─────────────────┐
│  Microservicio  │
│   C (Backend)   │
└─────────────────┘
```

### Reglas de Oro

1. **Service-to-Service sin contexto de usuario** → Service Account Token
2. **Service-to-Service con contexto de usuario** → Forward User Token
3. **Service-to-Keycloak Admin API** → Service Account Token
4. **Usuario-to-Service** → User Token

## 💻 Implementación en el Proyecto

### Ya Implementado

El proyecto ya usa **Client Credentials Grant** en `KeycloakService`:

```java
// src/main/java/com/example/authservice/service/KeycloakService.java
private String getAdminToken() {
    // ...
    body.add("grant_type", "client_credentials");
    body.add("client_id", clientId);
    body.add("client_secret", clientSecret);
    // ...
}
```

### Para Llamar a Otros Microservicios

Si necesitas llamar a otro microservicio, puedes crear un servicio similar:

```java
@Service
@RequiredArgsConstructor
public class OtherServiceClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${keycloak.url}")
    private String keycloakUrl;
    
    @Value("${keycloak.realm}")
    private String realm;
    
    @Value("${keycloak.client-id}")
    private String clientId;
    
    @Value("${keycloak.client-secret}")
    private String clientSecret;
    
    /**
     * Obtiene un token de service account para llamar a otros microservicios.
     */
    private String getServiceAccountToken() {
        String url = String.format("%s/realms/%s/protocol/openid-connect/token", 
                                   keycloakUrl, realm);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request, 
            new ParameterizedTypeReference<Map<String, Object>>() {});
        
        return (String) response.getBody().get("access_token");
    }
    
    /**
     * Llama a otro microservicio usando service account token.
     */
    public void callOtherMicroservice() {
        String token = getServiceAccountToken();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        restTemplate.exchange(
            "http://other-service/api/endpoint", 
            HttpMethod.GET, request, 
            String.class);
    }
}
```

## 🔒 Seguridad

### Ventajas del Service Account Token

1. **Principio de menor privilegio**: Solo tiene los permisos del cliente
2. **Sin contexto de usuario**: No puede ser usado para impersonar usuarios
3. **Auditable**: Todas las operaciones están asociadas al cliente
4. **Fácil de revocar**: Solo revocar el cliente en Keycloak

### Mejores Prácticas

1. **Un Service Account por microservicio**: No compartir el mismo cliente entre servicios
2. **Permisos mínimos**: Asignar solo los roles necesarios
3. **Rotación de secrets**: Rotar `client_secret` periódicamente
4. **Validación en el receptor**: El microservicio receptor debe validar el `azp` (authorized party)

## 📚 Referencias

- [OAuth 2.0 Client Credentials Grant](https://oauth.net/2/grant-types/client-credentials/)
- [Keycloak Service Accounts](https://www.keycloak.org/docs/latest/server_admin/#_service_accounts)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)

