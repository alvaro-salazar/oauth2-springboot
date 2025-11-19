package com.example.authservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de Swagger/OpenAPI para documentación de la API.
 * 
 * Incluye configuración de seguridad OAuth2 para poder probar
 * los endpoints directamente desde Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        
        return new OpenAPI()
            .info(new Info()
                .title("Spring Auth Service API")
                .version("1.0.0")
                .description("""
                    Microservicio de autenticación y autorización con OAuth 2.0 y OIDC.
                    
                    Este servicio actúa como un Resource Server que valida tokens JWT
                    emitidos por un Authorization Server compatible con OIDC.
                    
                    ## Autenticación
                    
                    Para usar esta API, necesitas obtener un token JWT de tu Authorization Server
                    (Keycloak, Auth0, etc.) y enviarlo en el header Authorization:
                    
                    ```
                    Authorization: Bearer <tu-token-jwt>
                    ```
                    
                    ## Endpoints
                    
                    - `/api/v1/users` - Gestión de usuarios
                    - `/api/v1/profile` - Información del perfil del usuario autenticado
                    - `/actuator` - Endpoints de observabilidad y métricas
                    """)
                .contact(new Contact()
                    .name("Equipo de Desarrollo")
                    .email("dev@example.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .addSecurityItem(new SecurityRequirement()
                .addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("""
                            Ingresa tu token JWT obtenido del Authorization Server.
                            El token debe ser válido y no expirado.
                            """)));
    }
}
