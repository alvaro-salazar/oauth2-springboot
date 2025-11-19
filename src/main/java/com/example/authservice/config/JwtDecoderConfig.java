package com.example.authservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Configuración personalizada del JwtDecoder para resolver el problema
 * de validación de tokens cuando Keycloak y el auth-service están en Docker.
 * 
 * Este decoder:
 * - Obtiene las claves públicas desde Keycloak usando la red interna de Docker
 * - Valida tokens con el issuer correcto (http://localhost:8080/realms/master)
 */
@Configuration
public class JwtDecoderConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /**
     * Crea un JwtDecoder personalizado que:
     * 1. Usa la red interna de Docker para obtener las claves desde Keycloak
     * 2. Valida tokens con el issuer correcto (desde application.yml)
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Reemplazar localhost con keycloak-service para acceso interno
        // El endpoint correcto para JWK Set es /protocol/openid-connect/certs
        String jwkSetUri = issuerUri.replace("localhost:8080", "keycloak-service:8080") 
                                    + "/protocol/openid-connect/certs";
        
        // Crear el decoder usando el JWK Set URI interno
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .build();
        
        // Configurar el issuer esperado (el del token real)
        jwtDecoder.setJwtValidator(
            org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(issuerUri)
        );
        
        return jwtDecoder;
    }
}

