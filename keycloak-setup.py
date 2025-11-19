#!/usr/bin/env python3
"""
Script para configurar Keycloak automáticamente
Crea el cliente, roles y usuarios de prueba
"""
import requests
import time
import sys

KEYCLOAK_URL = "http://keycloak-service:8080"
ADMIN_USER = "admin"
ADMIN_PASS = "admin"
READINESS_URL = f"{KEYCLOAK_URL}/realms/master/.well-known/openid-configuration"

def wait_for_keycloak():
    """Espera a que el endpoint de descubrimiento OIDC responda OK."""
    print("Esperando a que Keycloak esté disponible...")
    max_attempts = 60  # 2 minutos
    for i in range(max_attempts):
        try:
            response = requests.get(READINESS_URL, timeout=5)
            if response.status_code == 200:
                print("Keycloak está listo!")
                return True
        except requests.RequestException:
            pass
        time.sleep(2)
        if i % 10 == 0:
            print(f"Esperando... ({i*2}s)")
    print("Error: Keycloak no está disponible después de 2 minutos")
    return False

def get_admin_token():
    """Obtiene el token de administrador"""
    url = f"{KEYCLOAK_URL}/realms/master/protocol/openid-connect/token"
    data = {
        "username": ADMIN_USER,
        "password": ADMIN_PASS,
        "grant_type": "password",
        "client_id": "admin-cli"
    }
    response = requests.post(url, data=data, timeout=10)
    if response.status_code == 200:
        return response.json()["access_token"]
    else:
        print(f"Error obteniendo token: {response.status_code}")
        return None

def configure_realm_token_settings(token):
    """Configura tiempos de expiración más largos para los tokens"""
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    # Obtener configuración actual del realm
    url = f"{KEYCLOAK_URL}/admin/realms/master"
    response = requests.get(url, headers=headers, timeout=10)
    
    if response.status_code == 200:
        realm_data = response.json()
        # Configurar tiempos de expiración más largos (1 hora = 3600 segundos)
        realm_data["accessTokenLifespan"] = 3600  # 1 hora
        realm_data["accessTokenLifespanForImplicitFlow"] = 3600
        realm_data["ssoSessionIdleTimeout"] = 1800  # 30 minutos
        realm_data["ssoSessionMaxLifespan"] = 36000  # 10 horas
        
        # Actualizar realm
        requests.put(url, headers=headers, json=realm_data, timeout=10)
        print("✓ Tiempos de expiración de tokens configurados (1 hora)")

def create_or_update_client(token):
    """Crea o actualiza el cliente spring-auth-service"""
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    # Verificar si el cliente existe
    url = f"{KEYCLOAK_URL}/admin/realms/master/clients"
    params = {"clientId": "spring-auth-service"}
    response = requests.get(url, headers=headers, params=params, timeout=10)
    
    client_data = {
        "clientId": "spring-auth-service",
        "enabled": True,
        "redirectUris": ["*"],
        "webOrigins": ["*"],
        "publicClient": False,
        "directAccessGrantsEnabled": True,
        "serviceAccountsEnabled": True
    }
    
    if response.status_code == 200 and len(response.json()) > 0:
        client_id = response.json()[0]["id"]
        print(f"Cliente 'spring-auth-service' ya existe, actualizando...")
        url = f"{KEYCLOAK_URL}/admin/realms/master/clients/{client_id}"
        requests.put(url, headers=headers, json=client_data, timeout=10)
    else:
        print("Creando cliente 'spring-auth-service'...")
        requests.post(url, headers=headers, json=client_data, timeout=10)
    print("✓ Cliente configurado")

def assign_service_account_roles(token, client_id):
    """Asigna roles de administrador al Service Account del cliente"""
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    # Obtener el Service Account User ID
    # El username del Service Account es: service-account-{clientId}
    service_account_username = f"service-account-spring-auth-service"
    url = f"{KEYCLOAK_URL}/admin/realms/master/users"
    params = {"username": service_account_username}
    response = requests.get(url, headers=headers, params=params, timeout=10)
    
    if response.status_code != 200 or len(response.json()) == 0:
        print(f"⚠ Service Account '{service_account_username}' no encontrado. Puede que necesite tiempo para crearse.")
        return
    
    service_account_user_id = response.json()[0]["id"]
    
    # Obtener el cliente master-realm (contiene los roles de administración)
    url = f"{KEYCLOAK_URL}/admin/realms/master/clients"
    params = {"clientId": "master-realm"}
    response = requests.get(url, headers=headers, params=params, timeout=10)
    
    if response.status_code != 200 or len(response.json()) == 0:
        print("⚠ Cliente master-realm no encontrado")
        return
    
    master_realm_client_id = response.json()[0]["id"]
    
    # Asignar roles específicos del cliente master-realm
    roles_to_assign = []
    for role_name in ["manage-users", "view-users", "query-users"]:
        role_url = f"{KEYCLOAK_URL}/admin/realms/master/clients/{master_realm_client_id}/roles/{role_name}"
        role_response = requests.get(role_url, headers=headers, timeout=10)
        if role_response.status_code == 200:
            roles_to_assign.append(role_response.json())
    
    if roles_to_assign:
        # Asignar roles del cliente al Service Account
        url = f"{KEYCLOAK_URL}/admin/realms/master/users/{service_account_user_id}/role-mappings/clients/{master_realm_client_id}"
        response = requests.post(url, headers=headers, json=roles_to_assign, timeout=10)
        if response.status_code in [200, 204]:
            role_names = [r["name"] for r in roles_to_assign]
            print(f"✓ Roles asignados al Service Account: {', '.join(role_names)}")
        else:
            print(f"⚠ Error asignando roles: {response.status_code}")
    else:
        print("⚠ No se pudieron encontrar roles para asignar al Service Account.")

def create_role_if_not_exists(token, role_name):
    """Crea un rol si no existe"""
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    url = f"{KEYCLOAK_URL}/admin/realms/master/roles/{role_name}"
    response = requests.get(url, headers=headers, timeout=10)
    
    if response.status_code == 404:
        url = f"{KEYCLOAK_URL}/admin/realms/master/roles"
        requests.post(url, headers=headers, json={"name": role_name}, timeout=10)
        print(f"✓ Rol '{role_name}' creado")
    else:
        print(f"✓ Rol '{role_name}' ya existe")

def create_or_update_user(token, username, password, email, first_name, last_name, roles):
    """Crea o actualiza un usuario"""
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    # Verificar si el usuario existe
    url = f"{KEYCLOAK_URL}/admin/realms/master/users"
    params = {"username": username}
    response = requests.get(url, headers=headers, params=params, timeout=10)
    
    user_data = {
        "username": username,
        "email": email,
        "firstName": first_name,
        "lastName": last_name,
        "enabled": True,
        "credentials": [{
            "type": "password",
            "value": password,
            "temporary": False
        }],
        "realmRoles": roles
    }
    
    if response.status_code == 200 and len(response.json()) > 0:
        user_id = response.json()[0]["id"]
        print(f"Usuario '{username}' ya existe, actualizando...")
        
        # Actualizar usuario
        url = f"{KEYCLOAK_URL}/admin/realms/master/users/{user_id}"
        update_data = {
            "email": email,
            "firstName": first_name,
            "lastName": last_name,
            "enabled": True
        }
        requests.put(url, headers=headers, json=update_data, timeout=10)
        
        # Actualizar contraseña
        url = f"{KEYCLOAK_URL}/admin/realms/master/users/{user_id}/reset-password"
        password_data = {
            "type": "password",
            "value": password,
            "temporary": False
        }
        requests.put(url, headers=headers, json=password_data, timeout=10)
        
        # Actualizar roles
        url = f"{KEYCLOAK_URL}/admin/realms/master/users/{user_id}/role-mappings/realm"
        role_mappings = []
        for role in roles:
            role_url = f"{KEYCLOAK_URL}/admin/realms/master/roles/{role}"
            role_response = requests.get(role_url, headers=headers, timeout=10)
            if role_response.status_code == 200:
                role_mappings.append(role_response.json())
        if role_mappings:
            requests.post(url, headers=headers, json=role_mappings, timeout=10)
    else:
        print(f"Creando usuario '{username}'...")
        url = f"{KEYCLOAK_URL}/admin/realms/master/users"
        response = requests.post(url, headers=headers, json=user_data, timeout=10)
        if response.status_code in [201, 409]:
            # Obtener el ID del usuario creado para asignar roles
            response = requests.get(f"{KEYCLOAK_URL}/admin/realms/master/users", 
                                  headers=headers, params={"username": username}, timeout=10)
            if response.status_code == 200 and len(response.json()) > 0:
                user_id = response.json()[0]["id"]
                # Asignar roles
                url = f"{KEYCLOAK_URL}/admin/realms/master/users/{user_id}/role-mappings/realm"
                role_mappings = []
                for role in roles:
                    role_url = f"{KEYCLOAK_URL}/admin/realms/master/roles/{role}"
                    role_response = requests.get(role_url, headers=headers, timeout=10)
                    if role_response.status_code == 200:
                        role_mappings.append(role_response.json())
                if role_mappings:
                    requests.post(url, headers=headers, json=role_mappings, timeout=10)
    
    print(f"✓ Usuario '{username}' configurado")

def main():
    if not wait_for_keycloak():
        sys.exit(1)
    
    # Esperar un poco más para que Keycloak esté completamente listo
    time.sleep(5)
    
    token = get_admin_token()
    if not token:
        print("Error: No se pudo obtener el token de administrador")
        sys.exit(1)
    
    print("\n=== Configurando Keycloak ===\n")
    
    # Configurar tiempos de expiración de tokens
    configure_realm_token_settings(token)
    
    # Crear cliente
    create_or_update_client(token)
    
    # Obtener el ID del cliente para asignar roles al Service Account
    url = f"{KEYCLOAK_URL}/admin/realms/master/clients"
    params = {"clientId": "spring-auth-service"}
    response = requests.get(url, headers={"Authorization": f"Bearer {token}"}, params=params, timeout=10)
    client_id = None
    if response.status_code == 200 and len(response.json()) > 0:
        client_id = response.json()[0]["id"]
    
    # Esperar un poco para que Keycloak cree el Service Account User
    time.sleep(2)
    
    # Asignar roles al Service Account
    if client_id:
        print("\n--- Asignando roles al Service Account ---")
        assign_service_account_roles(token, client_id)
    
    # Crear roles
    print("\n--- Creando roles ---")
    create_role_if_not_exists(token, "USER")
    create_role_if_not_exists(token, "ADMIN")
    
    # Crear usuarios
    print("\n--- Creando usuarios ---")
    create_or_update_user(token, "testuser", "test123", "testuser@example.com", 
                         "Test", "User", ["USER"])
    create_or_update_user(token, "admin", "admin123", "admin@example.com", 
                         "Admin", "User", ["ADMIN", "USER"])
    
    print("\n=== Configuración completada! ===")
    print("\nUsuarios de prueba:")
    print("  - testuser / test123 (rol: USER)")
    print("  - admin / admin123 (rol: ADMIN, USER)")
    print("\nCliente configurado: spring-auth-service")

if __name__ == "__main__":
    main()

