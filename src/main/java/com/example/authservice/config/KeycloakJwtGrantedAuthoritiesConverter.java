package com.example.authservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Convierte los roles del token JWT de Keycloak a las authorities de Spring Security.
 * 
 * Keycloak envía los roles en el claim "realm_access.roles" sin el prefijo "ROLE_",
 * pero Spring Security espera authorities con el prefijo "ROLE_" para hasRole().
 * 
 * Este converter:
 * 1. Extrae los roles de realm_access.roles (Keycloak)
 * 2. Los convierte a authorities con el prefijo "ROLE_"
 * 3. Permite que @PreAuthorize("hasRole('ADMIN')") funcione correctamente
 */
public class KeycloakJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // Extraer roles de realm_access (Keycloak)
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) rolesObj;
                authorities.addAll(
                    roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList())
                );
            }
        }
        
        // También extraer roles de resource_access (client roles en Keycloak)
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            resourceAccess.values().forEach(resource -> {
                if (resource instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resourceMap = (Map<String, Object>) resource;
                    Object resourceRoles = resourceMap.get("roles");
                    if (resourceRoles instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> roles = (List<String>) resourceRoles;
                        authorities.addAll(
                            roles.stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                .collect(Collectors.toList())
                        );
                    }
                }
            });
        }
        
        return authorities;
    }
}

