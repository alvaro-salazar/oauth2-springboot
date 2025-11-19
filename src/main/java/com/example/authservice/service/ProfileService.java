package com.example.authservice.service;

import com.example.authservice.dto.ProfileDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para extraer información del perfil del usuario desde el token JWT.
 * 
 * Este servicio parsea los claims del token JWT para construir
 * el perfil del usuario autenticado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    /**
     * Extrae la información del perfil del usuario desde el token JWT.
     * 
     * @param jwt Token JWT autenticado
     * @return ProfileDTO con la información del usuario
     */
    public ProfileDTO getProfileFromJwt(Jwt jwt) {
        log.debug("Extrayendo perfil del usuario desde JWT");
        
        String subject = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        
        // Extraer roles del token
        // En Keycloak, los roles están en realm_access.roles
        List<String> roles = extractRoles(jwt);
        
        return ProfileDTO.builder()
                .subject(subject)
                .username(username)
                .email(email)
                .roles(roles)
                .expirationTime(jwt.getExpiresAt() != null ? jwt.getExpiresAt().getEpochSecond() : null)
                .issuedAt(jwt.getIssuedAt() != null ? jwt.getIssuedAt().getEpochSecond() : null)
                .build();
    }

    /**
     * Obtiene información detallada del token JWT, incluyendo todos los claims.
     * 
     * @param jwt Token JWT autenticado
     * @return ProfileDTO con toda la información del token
     */
    public ProfileDTO getTokenInfo(Jwt jwt) {
        log.debug("Extrayendo información completa del token JWT");
        
        ProfileDTO profile = getProfileFromJwt(jwt);
        
        // Agregar todos los claims
        Map<String, Object> allClaims = new HashMap<>(jwt.getClaims());
        profile.setAllClaims(allClaims);
        
        return profile;
    }

    /**
     * Extrae los roles del token JWT.
     * 
     * Soporta diferentes formatos de claims según el Authorization Server:
     * - Keycloak: realm_access.roles
     * - Auth0: permissions o roles
     * - Custom: roles
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        List<String> roles = new ArrayList<>();
        
        // Intentar extraer roles de realm_access (Keycloak)
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List) {
                roles.addAll((List<String>) rolesObj);
            }
        }
        
        // Intentar extraer roles directos
        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof List) {
            roles.addAll((List<String>) rolesClaim);
        }
        
        // Intentar extraer de resource_access (Keycloak client roles)
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            resourceAccess.values().forEach(resource -> {
                if (resource instanceof Map) {
                    Map<String, Object> resourceMap = (Map<String, Object>) resource;
                    Object resourceRoles = resourceMap.get("roles");
                    if (resourceRoles instanceof List) {
                        roles.addAll((List<String>) resourceRoles);
                    }
                }
            });
        }
        
        log.debug("Roles extraídos del token: {}", roles);
        return roles;
    }
}
