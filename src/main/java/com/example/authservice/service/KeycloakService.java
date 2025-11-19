package com.example.authservice.service;

import com.example.authservice.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Servicio para interactuar con la API de administración de Keycloak.
 * 
 * Este servicio permite crear, actualizar y eliminar usuarios en Keycloak
 * desde el microservicio, manteniendo sincronización entre ambos sistemas.
 * 
 * En la industria, este patrón se usa comúnmente para:
 * - Sincronizar usuarios entre sistemas
 * - Provisionar usuarios automáticamente
 * - Mantener una fuente de verdad (Keycloak) para autenticación
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakService {

    private final RestTemplate restTemplate;

    @Value("${keycloak.url:http://localhost:8080}")
    private String keycloakUrl;

    @Value("${keycloak.realm:master}")
    private String realm;

    @Value("${keycloak.client-id:spring-auth-service}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    /**
     * Obtiene un token de administrador usando Service Account (Client Credentials Grant).
     * 
     * Ver INDUSTRY_PRACTICES.md para más detalles sobre este patrón.
     */
    private String getAdminToken() {
        // Validar que el client_secret esté configurado
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            log.error("KEYCLOAK_CLIENT_SECRET no está configurado. " +
                    "Obtén el secret desde Keycloak: Clients > {} > Credentials > Secret", clientId);
            return null;
        }
        
        try {
            String url = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, realm);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            
            ParameterizedTypeReference<Map<String, Object>> responseType = 
                    new ParameterizedTypeReference<Map<String, Object>>() {};
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, responseType);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
            
            log.error("Error obteniendo token de Keycloak. Status: {}, Response: {}", 
                    response.getStatusCode(), response.getBody());
            return null;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                log.error("Error 401: Client secret inválido o cliente no tiene Service Account habilitado. " +
                        "Verifica: 1) KEYCLOAK_CLIENT_SECRET está correcto, 2) Cliente tiene 'Service Accounts Enabled' = ON");
            } else {
                log.error("Error HTTP al obtener token de Keycloak: {}", e.getStatusCode(), e);
            }
            return null;
        } catch (Exception e) {
            log.error("Excepción al obtener token de Keycloak. URL: {}/realms/{}/protocol/openid-connect/token", 
                    keycloakUrl, realm, e);
            return null;
        }
    }

    /**
     * Crea un usuario en Keycloak.
     * 
     * Nota: Para producción, ver mejoras recomendadas en SECURITY.md
     * (envío de password por email, políticas más estrictas, etc.)
     */
    public void createUserInKeycloak(UserDTO userDTO, String temporaryPassword) {
        String token = getAdminToken();
        if (token == null) {
            throw new RuntimeException("No se pudo obtener token de administrador de Keycloak");
        }

        try {
            String url = String.format("%s/admin/realms/%s/users", keycloakUrl, realm);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            
            Map<String, Object> userData = new HashMap<>();
            userData.put("username", userDTO.getUsername());
            userData.put("email", userDTO.getEmail());
            userData.put("firstName", extractFirstName(userDTO.getFullName()));
            userData.put("lastName", extractLastName(userDTO.getFullName()));
            userData.put("enabled", userDTO.getActive() != null ? userDTO.getActive() : true);
            userData.put("emailVerified", false);
            
            // Credenciales con password temporal
            List<Map<String, Object>> credentials = new ArrayList<>();
            Map<String, Object> credential = new HashMap<>();
            credential.put("type", "password");
            credential.put("value", temporaryPassword);
            credential.put("temporary", true); // Requiere cambio en primer login
            credentials.add(credential);
            userData.put("credentials", credentials);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(userData, headers);
            
            ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Usuario '{}' creado exitosamente en Keycloak", userDTO.getUsername());
                
                // Asignar rol USER por defecto
                assignDefaultRole(userDTO.getUsername(), token);
            } else {
                log.error("Error creando usuario en Keycloak: {}", response.getStatusCode());
                throw new RuntimeException("Error al crear usuario en Keycloak: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) {
                log.warn("Usuario '{}' ya existe en Keycloak", userDTO.getUsername());
                // No lanzamos excepción, solo logueamos (idempotencia)
            } else {
                log.error("Error HTTP al crear usuario en Keycloak", e);
                throw new RuntimeException("Error al crear usuario en Keycloak: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Excepción al crear usuario en Keycloak", e);
            throw new RuntimeException("Error al crear usuario en Keycloak", e);
        }
    }

    /**
     * Asigna el rol USER por defecto a un usuario.
     */
    private void assignDefaultRole(String username, String token) {
        try {
            // Obtener ID del usuario
            String userId = getUserIdByUsername(username, token);
            if (userId == null) {
                log.warn("No se pudo encontrar el ID del usuario '{}' en Keycloak", username);
                return;
            }
            
            // Obtener el rol USER
            String roleId = getRoleId("USER", token);
            if (roleId == null) {
                log.warn("No se pudo encontrar el rol USER en Keycloak");
                return;
            }
            
            // Asignar rol
            String url = String.format("%s/admin/realms/%s/users/%s/role-mappings/realm", 
                    keycloakUrl, realm, userId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            
            Map<String, Object> role = new HashMap<>();
            role.put("id", roleId);
            role.put("name", "USER");
            
            HttpEntity<List<Map<String, Object>>> request = new HttpEntity<>(List.of(role), headers);
            restTemplate.postForEntity(url, request, Void.class);
            
            log.info("Rol USER asignado al usuario '{}' en Keycloak", username);
        } catch (Exception e) {
            log.error("Error asignando rol USER al usuario '{}'", username, e);
            // No lanzamos excepción, solo logueamos (no crítico)
        }
    }

    /**
     * Obtiene el ID de un usuario por su username.
     */
    private String getUserIdByUsername(String username, String token) {
        try {
            String url = String.format("%s/admin/realms/%s/users?username=%s", keycloakUrl, realm, username);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ParameterizedTypeReference<List<Map<String, Object>>> responseType = 
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {};
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, responseType);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> users = response.getBody();
                if (!users.isEmpty()) {
                    return (String) users.get(0).get("id");
                }
            }
        } catch (Exception e) {
            log.error("Error obteniendo ID del usuario '{}'", username, e);
        }
        return null;
    }

    /**
     * Obtiene el ID de un rol por su nombre.
     */
    private String getRoleId(String roleName, String token) {
        try {
            String url = String.format("%s/admin/realms/%s/roles/%s", keycloakUrl, realm, roleName);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ParameterizedTypeReference<Map<String, Object>> responseType = 
                    new ParameterizedTypeReference<Map<String, Object>>() {};
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, responseType);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("id");
            }
        } catch (Exception e) {
            log.error("Error obteniendo ID del rol '{}'", roleName, e);
        }
        return null;
    }

    /**
     * Elimina un usuario de Keycloak.
     */
    public void deleteUserFromKeycloak(String username) {
        String token = getAdminToken();
        if (token == null) {
            log.warn("No se pudo obtener token de administrador de Keycloak para eliminar usuario");
            return;
        }

        try {
            String userId = getUserIdByUsername(username, token);
            if (userId == null) {
                log.warn("Usuario '{}' no encontrado en Keycloak", username);
                return;
            }
            
            String url = String.format("%s/admin/realms/%s/users/%s", keycloakUrl, realm, userId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
            
            log.info("Usuario '{}' eliminado exitosamente de Keycloak", username);
        } catch (Exception e) {
            log.error("Error eliminando usuario '{}' de Keycloak", username, e);
            // No lanzamos excepción, solo logueamos (no crítico si falla)
        }
    }

    /**
     * Extrae el primer nombre del fullName.
     */
    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    /**
     * Extrae el apellido del fullName.
     */
    private String extractLastName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length > 1) {
            return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        }
        return "";
    }
}

