# Inicio Rápido - Guía Completa

Esta guía te llevará desde cero hasta tener el proyecto funcionando completamente.

## Requisitos Previos

Asegúrate de tener instalado:

- **Docker** 20.10+ ([Descargar](https://www.docker.com/get-started))
- **Docker Compose** 2.0+ (viene con Docker Desktop)
- **Git** (opcional, solo si clonas el repositorio)

Verifica las instalaciones:

```bash
docker --version
docker compose version
```

## Paso 1: Preparar el Proyecto

### Opción A: Si ya tienes el proyecto

```bash
cd spring-auth-service
```

### Opción B: Si necesitas clonar

```bash
git clone <repository-url>
cd spring-auth-service
```

## Paso 2: Configurar KEYCLOAK_CLIENT_SECRET

El proyecto necesita el `client_secret` de Keycloak para la integración. Hay dos formas de obtenerlo:

### Opción A: Automático (Recomendado)

```bash
# Dar permisos de ejecución al script
chmod +x get-keycloak-secret.sh

# Ejecutar el script (primero necesitas levantar Keycloak en el paso 3)
./get-keycloak-secret.sh
```

El script te mostrará el secret y las instrucciones para configurarlo.

### Opción B: Manual

1. Levanta Keycloak primero (ver Paso 3)
2. Abre http://localhost:8080
3. Login con `admin` / `admin`
4. Ve a **Clients** → **spring-auth-service**
5. Ve a la pestaña **Credentials**
6. Copia el valor de **Secret**

## Paso 3: Levantar los Servicios

### 3.1 Levantar Keycloak y Bases de Datos

```bash
# Levantar Keycloak, PostgreSQL para Keycloak, y PostgreSQL para auth-service
docker compose up -d keycloak-service keycloak-db auth-db
```

Espera unos 30-60 segundos a que Keycloak esté completamente listo. Verifica:

```bash
# Ver logs de Keycloak
docker compose logs -f keycloak-service

# O verificar que responda
curl http://localhost:8080/realms/master/.well-known/openid-configuration
```

Cuando veas en los logs algo como:
```
Keycloak x.x.x started in XXms
```

Keycloak está listo.

### 3.2 Configurar Keycloak Automáticamente

```bash
# Ejecutar el script de configuración automática
docker compose run --rm keycloak-init
```

Este script:
- ✅ Crea el cliente `spring-auth-service`
- ✅ Habilita Service Accounts
- ✅ Asigna roles al Service Account
- ✅ Crea roles `USER` y `ADMIN`
- ✅ Crea usuarios de prueba (`testuser` y `admin`)
- ✅ Configura tiempos de expiración de tokens

**Salida esperada:**
```
Esperando a que Keycloak esté disponible...
Keycloak está listo!

=== Configurando Keycloak ===

✓ Tiempos de expiración de tokens configurados (1 hora)
✓ Cliente configurado

--- Asignando roles al Service Account ---
✓ Service Account encontrado: ...
✓ Roles asignados al Service Account: manage-users, view-users, query-users

--- Creando roles ---
✓ Rol 'USER' creado
✓ Rol 'ADMIN' creado

--- Creando usuarios ---
✓ Usuario 'testuser' configurado
✓ Usuario 'admin' configurado

=== Configuración completada! ===
```

### 3.3 Obtener y Configurar KEYCLOAK_CLIENT_SECRET

Ahora que Keycloak está configurado, obtén el secret:

**Opción A: Script automático**
```bash
./get-keycloak-secret.sh
```

**Opción B: Manual**
1. Abre http://localhost:8080
2. Login: `admin` / `admin`
3. **Clients** → **spring-auth-service** → **Credentials**
4. Copia el **Secret**

**Crear archivo .env:**
```bash
# Crear archivo .env en la raíz del proyecto
echo "KEYCLOAK_CLIENT_SECRET=tu-secret-aqui" > .env
```

**O editar directamente:**
```bash
# Editar docker-compose.yml línea 99 pero no se recomienda
# Reemplazar: KEYCLOAK_CLIENT_SECRET: ${KEYCLOAK_CLIENT_SECRET:-}
# Con: KEYCLOAK_CLIENT_SECRET: tu-secret-aqui
```

### 3.4 Levantar el Auth Service

```bash
# Levantar el microservicio
docker compose up -d auth-service
```

Verifica que esté funcionando:

```bash
# Ver logs
docker compose logs -f auth-service

# O verificar health check
curl http://localhost:8081/api/v1/actuator/health
```

**Salida esperada del health check:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    }
  }
}
```

## Paso 4: Verificar que Todo Funciona

### 4.1 Verificar Servicios

```bash
# Ver estado de todos los servicios
docker compose ps
```

Todos deberían estar en estado `Up` o `Running`.

### 4.2 Verificar Endpoints

```bash
# Health check del auth-service
curl http://localhost:8081/api/v1/actuator/health

# Swagger UI (abre en navegador)
open http://localhost:8081/swagger-ui.html
# O en Linux:
# xdg-open http://localhost:8081/swagger-ui.html
```

### 4.3 Obtener un Token de Prueba

```bash
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=spring-auth-service" \
  -d "username=testuser" \
  -d "password=test123" \
  -d "grant_type=password"
```

**Respuesta esperada:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### 4.4 Probar un Endpoint Protegido

```bash
# Guardar el token en una variable
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."  # Reemplaza con tu token

# Probar endpoint de perfil
curl -X GET http://localhost:8081/api/v1/profile \
  -H "Authorization: Bearer $TOKEN"
```

**Respuesta esperada:**
```json
{
  "subject": "123e4567-e89b-12d3-a456-426614174000",
  "username": "testuser",
  "email": "testuser@example.com",
  "roles": ["USER"]
}
```

## Paso 5: Probar con Postman (Opcional)

### 5.1 Importar Colección

1. Abre Postman
2. Click en **Import**
3. Selecciona:
   - `Spring-Auth-Service.postman_collection.json`
   - `Spring-Auth-Service.postman_environment.json`
4. Click en **Import**

### 5.2 Configurar Variables

1. Selecciona el entorno **"Spring Auth Service - Local"**
2. Configura `client_secret`:
   - Abre http://localhost:8080
   - **Clients** → **spring-auth-service** → **Credentials**
   - Copia el **Secret**
   - En Postman, edita la variable `client_secret`

### 5.3 Ejecutar Pruebas

1. **Obtener Token**: Ejecuta "Obtener Token - testuser"
2. **Get Profile**: Ejecuta "Get Profile" (usa el token guardado automáticamente)
3. **Get All Users**: Ejecuta "Get All Users" (requiere token de admin)

## Resumen de URLs

| Servicio | URL | Credenciales |
|----------|-----|--------------|
| **Keycloak** | http://localhost:8080 | `admin` / `admin` |
| **Auth Service** | http://localhost:8081 | (Requiere token JWT) |
| **Swagger UI** | http://localhost:8081/swagger-ui.html | - |
| **Health Check** | http://localhost:8081/api/v1/actuator/health | - |

## Usuarios de Prueba

| Usuario | Password | Roles |
|---------|----------|-------|
| `testuser` | `test123` | USER |
| `admin` | `admin123` | ADMIN, USER |

## 🔍 Comandos Útiles

### Ver Logs

```bash
# Todos los servicios
docker compose logs -f

# Solo auth-service
docker compose logs -f auth-service

# Solo Keycloak
docker compose logs -f keycloak-service
```

### Detener Servicios

```bash
# Detener todos los servicios
docker compose down

# Detener y eliminar volúmenes (reinicio completo)
docker compose down -v
```

### Reiniciar un Servicio

```bash
# Reiniciar auth-service
docker compose restart auth-service

# Reconstruir y reiniciar
docker compose up -d --build auth-service
```

### Verificar Configuración

```bash
# Verificar que KEYCLOAK_CLIENT_SECRET esté configurado
docker compose exec auth-service env | grep KEYCLOAK_CLIENT_SECRET

# Verificar conectividad con Keycloak
docker compose exec auth-service ping keycloak-service
```

## Solución de Problemas

### Error: "No se pudo obtener token de administrador"

**Causa**: `KEYCLOAK_CLIENT_SECRET` no está configurado.

**Solución**:
1. Obtén el secret desde Keycloak UI
2. Configúralo en `.env` o `docker-compose.yml`
3. Reinicia: `docker compose restart auth-service`

### Error: "Keycloak no está disponible"

**Causa**: Keycloak aún no terminó de iniciar.

**Solución**:
```bash
# Esperar y verificar logs
docker compose logs -f keycloak-service

# Verificar que responda
curl http://localhost:8080/realms/master/.well-known/openid-configuration
```

### Error: "Connection refused" en auth-service

**Causa**: El servicio no está corriendo o hay problema de red.

**Solución**:
```bash
# Verificar estado
docker compose ps

# Ver logs
docker compose logs auth-service

# Reiniciar
docker compose restart auth-service
```

### Error: "401 Unauthorized" al obtener token

**Causa**: Credenciales incorrectas o cliente no configurado.

**Solución**:
1. Verifica que `keycloak-init` se ejecutó correctamente
2. Verifica usuarios: http://localhost:8080 → Users
3. Verifica cliente: http://localhost:8080 → Clients → spring-auth-service

##  Revision final

Si llegaste hasta aquí y todos los pasos funcionaron, tienes el proyecto completamente configurado y funcionando.

**Próximos pasos:**
- Explora la documentación en `README.md`
- Prueba los endpoints con Postman
- Revisa `TOKENS.md` para entender los tipos de tokens
- Revisa `MICROSERVICES_COMMUNICATION.md` para comunicación entre servicios

## Documentación Adicional

- [README.md](README.md) - Documentación completa del proyecto
- [TOKENS.md](TOKENS.md) - Explicación de tokens OAuth 2.0/OIDC/JWT
- [MICROSERVICES_COMMUNICATION.md](MICROSERVICES_COMMUNICATION.md) - Comunicación entre microservicios
- [SECURITY.md](SECURITY.md) - Mejoras de seguridad para producción
- [INDUSTRY_PRACTICES.md](INDUSTRY_PRACTICES.md) - Prácticas de la industria

