#!/bin/bash
# Script para obtener el client_secret de Keycloak automáticamente

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${REALM:-master}"
CLIENT_ID="${CLIENT_ID:-spring-auth-service}"
ADMIN_USER="${ADMIN_USER:-admin}"
# Intentar con diferentes contraseñas comunes
ADMIN_PASS="${ADMIN_PASS:-admin123}"

echo "Obteniendo client_secret de Keycloak..."
echo "URL: $KEYCLOAK_URL"
echo "Realm: $REALM"
echo "Client ID: $CLIENT_ID"
echo ""

# Obtener token de admin
echo "1. Obteniendo token de administrador..."
TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password")

TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('access_token', ''))" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "❌ Error: No se pudo obtener token de administrador"
  echo "Respuesta: $TOKEN_RESPONSE"
  exit 1
fi

echo "✓ Token obtenido"
echo ""

# Obtener ID del cliente
echo "2. Obteniendo ID del cliente..."
CLIENT_RESPONSE=$(curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" \
  -H "Authorization: Bearer $TOKEN")

CLIENT_ID_VALUE=$(echo "$CLIENT_RESPONSE" | python3 -c "import sys, json; clients=json.load(sys.stdin); print(clients[0]['id'] if clients else '')" 2>/dev/null)

if [ -z "$CLIENT_ID_VALUE" ]; then
  echo "❌ Error: Cliente '$CLIENT_ID' no encontrado"
  echo "Respuesta: $CLIENT_RESPONSE"
  exit 1
fi

echo "✓ Cliente encontrado: $CLIENT_ID_VALUE"
echo ""

# Obtener secret
echo "3. Obteniendo client_secret..."
SECRET_RESPONSE=$(curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients/$CLIENT_ID_VALUE/client-secret" \
  -H "Authorization: Bearer $TOKEN")

SECRET=$(echo "$SECRET_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('value', ''))" 2>/dev/null)

if [ -z "$SECRET" ]; then
  echo "❌ Error: No se pudo obtener el secret"
  echo "Respuesta: $SECRET_RESPONSE"
  exit 1
fi

echo "✓ Secret obtenido"
echo ""
echo "=========================================="
echo "KEYCLOAK_CLIENT_SECRET=$SECRET"
echo "=========================================="
echo ""
echo "Para configurarlo en docker-compose.yml, agrega:"
echo "  KEYCLOAK_CLIENT_SECRET=$SECRET"
echo ""
echo "O crea un archivo .env con:"
echo "  KEYCLOAK_CLIENT_SECRET=$SECRET"
echo ""
echo "Luego reinicia el servicio:"
echo "  docker compose up -d auth-service"

