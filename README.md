# Spring Auth Service

Microservicio Spring Boot que implementa autenticación y autorización con **OAuth 2.0** y **OIDC** (OpenID Connect), siguiendo las mejores prácticas de la industria.

## 📋 Tabla de Contenidos

- [Descripción](#descripción)
- [Inicio Rápido](#inicio-rápido)
- [Configuración](#configuración)
- [Endpoints](#endpoints)
- [Autenticación y Tokens](#autenticación-y-tokens)
- [Integración con Keycloak](#integración-con-keycloak)
- [Testing con Postman](#testing-con-postman)
- [Observabilidad](#observabilidad)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Mejoras Futuras](#mejoras-futuras)

## 🎯 Descripción

Este microservicio actúa como un **Resource Server** en el flujo OAuth 2.0/OIDC. Su función principal es:

1. **Validar tokens JWT** emitidos por un Authorization Server (Keycloak)
2. **Proteger endpoints** mediante autenticación y autorización basada en roles
3. **Gestionar usuarios** sincronizando con Keycloak automáticamente
4. **Exponer métricas y health checks** para observabilidad

### Flujo OAuth 2.0/OIDC

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Cliente   │────────▶│ Authorization │────────▶│   Usuario   │
│  (Frontend) │         │    Server     │         │             │
└─────────────┘         │  (Keycloak)   │         └─────────────┘
       │                 └──────────────┘
       │                        │ Token JWT
       │                        ▼
       │                 ┌─────────────┐
       └────────────────▶│   Resource  │
                         │   Server    │
                         │ (Este MS)   │
                         └─────────────┘
```

## 🚀 Inicio Rápido

> **📖 Para instrucciones detalladas paso a paso, ver [QUICK_START.md](QUICK_START.md)**

### Resumen Rápido

```bash
# 1. Levantar Keycloak y bases de datos
docker compose up -d keycloak-service keycloak-db auth-db

# 2. Configurar Keycloak automáticamente
docker compose run --rm keycloak-init

# 3. Obtener KEYCLOAK_CLIENT_SECRET y crear .env
./get-keycloak-secret.sh
echo "KEYCLOAK_CLIENT_SECRET=tu-secret-aqui" > .env

# 4. Levantar auth-service
docker compose up -d auth-service

# 5. Verificar
curl http://localhost:8081/api/v1/actuator/health
```

**Servicios disponibles:**
- **Keycloak**: http://localhost:8080 (admin/admin)
- **Auth Service**: http://localhost:8081
- **Swagger UI**: http://localhost:8081/swagger-ui.html

**Usuarios de prueba (preconfigurados):**
- `testuser` / `test123` (rol: USER)
- `admin` / `admin123` (rol: ADMIN, USER)

## ⚙️ Configuración

### Variables de Entorno

Crea un archivo `.env` en la raíz del proyecto:

```bash
# Keycloak Client Secret (obligatorio para integración)
KEYCLOAK_CLIENT_SECRET=tu-secret-obtenido-de-keycloak

# OAuth2 Configuration (ya configurado en docker-compose.yml)
OAUTH2_ISSUER_URI=http://localhost:8080/realms/master

# Database (producción)
DB_HOST=auth-db
DB_PORT=5432
DB_NAME=authdb
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

### Obtener KEYCLOAK_CLIENT_SECRET

**Opción 1: Desde Keycloak UI**
1. Abre http://localhost:8080
2. Login con `admin` / `admin`
3. Ve a **Clients** → **spring-auth-service** → **Credentials**
4. Copia el **Secret**

**Opción 2: Script automático**
```bash
./get-keycloak-secret.sh
```

## 📡 Endpoints

### API Principal

| Método | Endpoint | Descripción | Autenticación | Roles |
|--------|----------|-------------|---------------|-------|
| GET | `/api/v1/users` | Listar usuarios | ✅ | ADMIN |
| GET | `/api/v1/users/{id}` | Obtener usuario | ✅ | - |
| POST | `/api/v1/users` | Crear usuario | ✅ | ADMIN |
| PUT | `/api/v1/users/{id}` | Actualizar usuario | ✅ | - |
| DELETE | `/api/v1/users/{id}` | Eliminar usuario | ✅ | ADMIN |
| GET | `/api/v1/profile` | Perfil del usuario | ✅ | - |
| GET | `/api/v1/profile/token-info` | Info del token | ✅ | - |

### Observabilidad

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/v1/actuator/health` | Health check |
| GET | `/api/v1/actuator/info` | Información del servicio |
| GET | `/api/v1/actuator/prometheus` | Métricas Prometheus |

### Documentación

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/swagger-ui.html` | Swagger UI interactivo |
| GET | `/api-docs` | OpenAPI JSON |

## 🔐 Autenticación y Tokens

### Tipos de Tokens

Keycloak genera tokens que siguen los estándares **OAuth 2.0** y **OIDC (OpenID Connect)**:

1. **Access Token (JWT)**
   - **Formato**: JWT (JSON Web Token)
   - **Estándar**: OIDC (OpenID Connect) - capa sobre OAuth 2.0
   - **Contenido**: Claims estándar OIDC (`sub`, `preferred_username`, `email`, `realm_access`, etc.)
   - **Vida útil**: 1 hora (3600 segundos)
   - **Uso**: Se envía en el header `Authorization: Bearer <token>` para autenticarse en el auth-service

2. **Refresh Token**
   - **Formato**: Token opaco (no es JWT, es un string aleatorio)
   - **Estándar**: OAuth 2.0
   - **Vida útil**: ~30 días (configurable en Keycloak)
   - **Uso**: Se envía a Keycloak para obtener un nuevo access_token sin re-autenticarse

### Obtener Tokens

```bash
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=spring-auth-service" \
  -d "username=testuser" \
  -d "password=test123" \
  -d "grant_type=password"
```

**Respuesta:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",  // JWT (OIDC)
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",  // Token opaco (OAuth 2.0)
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_expires_in": 2592000
}
```

### Estructura del Access Token (JWT)

El `access_token` es un JWT con tres partes separadas por puntos:

```
header.payload.signature
```

**Payload típico (decodificado):**
```json
{
  "sub": "123e4567-e89b-12d3-a456-426614174000",
  "preferred_username": "testuser",
  "email": "testuser@example.com",
  "realm_access": {
    "roles": ["USER"]
  },
  "iss": "http://localhost:8080/realms/master",
  "aud": "spring-auth-service",
  "exp": 1704067200,
  "iat": 1704063600
}
```

### Usar el Access Token

```bash
curl -X GET http://localhost:8081/api/v1/profile \
  -H "Authorization: Bearer <access_token>"
```

El auth-service valida el JWT usando:
- **JWK Set** de Keycloak (claves públicas)
- **Issuer** (`iss` claim) debe coincidir con la configuración
- **Expiración** (`exp` claim)
- **Firma** del token (RS256)

### Refresh Token

Cuando el `access_token` expire (después de 1 hora), usa el `refresh_token`:

```bash
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=spring-auth-service" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=<refresh_token>"
```

**Nota**: El `refresh_token` se usa **directamente con Keycloak**, no con el auth-service. El auth-service solo valida `access_token` JWT.

## 🔄 Integración con Keycloak

### Sincronización Automática de Usuarios

Cuando creas un usuario con `POST /api/v1/users`, el sistema:

1. ✅ Genera un password temporal (12 caracteres)
2. ✅ Crea el usuario en Keycloak con password temporal
3. ✅ Asigna el rol `USER` por defecto
4. ✅ Crea el usuario en la base de datos local
5. ✅ Retorna el password temporal en la respuesta

**Ejemplo de creación:**

```bash
curl -X POST http://localhost:8081/api/v1/users \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "nuevousuario",
    "email": "nuevo@example.com",
    "fullName": "Nuevo Usuario",
    "active": true
  }'
```

**Respuesta:**
```json
{
  "user": {
    "id": 1,
    "username": "nuevousuario",
    "email": "nuevo@example.com",
    "fullName": "Nuevo Usuario",
    "active": true
  },
  "temporaryPassword": "TempPass123!@#",
  "message": "Usuario creado exitosamente. Este password es temporal y debe ser cambiado en el primer login."
}
```

El usuario puede hacer login inmediatamente con ese password temporal. Keycloak le pedirá cambiarlo en el primer login.

### Configuración del Service Account

El cliente `spring-auth-service` en Keycloak tiene:
- ✅ **Service Accounts Enabled**: Permite usar Client Credentials Grant
- ✅ **Roles asignados**: `manage-users`, `view-users`, `query-users`
- ✅ **Configuración automática**: El script `keycloak-setup.py` lo configura

## 🧪 Testing con Postman

### Importar Colección

1. Abre Postman
2. Click en **Import**
3. Selecciona:
   - `Spring-Auth-Service.postman_collection.json`
   - `Spring-Auth-Service.postman_environment.json`
4. Configura `client_secret` en las variables de entorno

### Flujo de Pruebas

1. **Obtener Token**: Ejecuta "Obtener Token - testuser" o "Obtener Token - admin"
2. **Probar Endpoints**: Los tokens se guardan automáticamente
3. **Refrescar Token**: Si expira, usa "Refrescar Token"
4. **Crear Usuario**: Usa el token de admin para crear usuarios

## 📊 Observabilidad

### Health Check

```bash
curl http://localhost:8081/api/v1/actuator/health
```

### Métricas Prometheus

```bash
curl http://localhost:8081/api/v1/actuator/prometheus
```

### Integración con Prometheus

Agrega a tu `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'spring-auth-service'
    metrics_path: '/api/v1/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8081']
```

## 📁 Estructura del Proyecto

```
spring-auth-service/
├── src/main/java/com/example/authservice/
│   ├── config/              # Configuraciones (Security, JWT, etc.)
│   ├── controller/          # Controladores REST
│   ├── service/             # Lógica de negocio
│   ├── repository/          # Repositorios JPA
│   ├── entity/              # Entidades JPA
│   ├── dto/                 # Data Transfer Objects
│   └── exception/           # Manejo de excepciones
├── src/main/resources/
│   ├── application.yml      # Configuración base
│   └── application-prod.yml # Configuración producción
├── docker-compose.yml       # Stack completo (Keycloak + Auth Service)
├── Dockerfile               # Imagen del microservicio
├── keycloak-setup.py        # Script de configuración automática
├── get-keycloak-secret.sh   # Script para obtener client secret
└── README.md                # Este archivo
```

## 🛠️ Tecnologías

- **Spring Boot 3.2.0** - Framework principal
- **Spring Security OAuth2 Resource Server** - Validación JWT
- **Spring Data JPA** - Acceso a datos
- **PostgreSQL** - Base de datos (producción)
- **Keycloak** - Authorization Server
- **Docker & Docker Compose** - Containerización
- **Swagger/OpenAPI 3** - Documentación API
- **Spring Boot Actuator** - Observabilidad

## 🚧 Mejoras Futuras

Ver [SECURITY.md](SECURITY.md) para mejoras de seguridad recomendadas para producción.

## 🔗 Comunicación entre Microservicios

Para comunicación **service-to-service**, usa **Client Credentials Grant** para obtener un **Service Account Token**.

**Ejemplo:**
```bash
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=spring-auth-service" \
  -d "client_secret=tu-client-secret"
```

Ver [MICROSERVICES_COMMUNICATION.md](MICROSERVICES_COMMUNICATION.md) para detalles completos.

## 🏗️ Arquitectura: API Gateway vs Resource Server

¿Dónde validar tokens? ¿En el API Gateway o en cada microservicio?

**Respuesta corta**: Depende de tu arquitectura. Ambos enfoques son válidos.

- **Resource Server Pattern** (actual): Cada microservicio valida tokens directamente
- **API Gateway Pattern**: El gateway valida y reenvía información en headers

Ver [API_GATEWAY_PATTERN.md](API_GATEWAY_PATTERN.md) para comparación detallada, ventajas/desventajas, y cuándo usar cada uno.

## 🔍 Validación de Tokens

**¿Necesitas un endpoint `/validate`?** 

**NO** - Spring Security valida tokens JWT automáticamente en cada request. No necesitas código adicional.

Ver [TOKEN_VALIDATION.md](TOKEN_VALIDATION.md) para explicación detallada de cómo funciona la validación automática.

## 📚 Recursos Adicionales

- [QUICK_START.md](QUICK_START.md) - **Guía completa paso a paso para iniciar desde cero**
- [TOKENS.md](TOKENS.md) - Explicación detallada de tipos de tokens (OAuth 2.0, OIDC, JWT)
- [TOKEN_VALIDATION.md](TOKEN_VALIDATION.md) - ¿Necesitas un endpoint /validate? Cómo funciona la validación automática
- [MICROSERVICES_COMMUNICATION.md](MICROSERVICES_COMMUNICATION.md) - Cómo comunicarse entre microservicios
- [API_GATEWAY_PATTERN.md](API_GATEWAY_PATTERN.md) - API Gateway vs Resource Server: ¿Dónde validar tokens?
- [SECURITY.md](SECURITY.md) - Mejoras de seguridad para producción
- [INDUSTRY_PRACTICES.md](INDUSTRY_PRACTICES.md) - Prácticas de la industria
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [OpenID Connect Specification](https://openid.net/specs/openid-connect-core-1_0.html)

## 📝 Licencia

Este proyecto está bajo la Licencia Apache 2.0.

---

**Desarrollado con ❤️ para estudiantes de desarrollo de software**
