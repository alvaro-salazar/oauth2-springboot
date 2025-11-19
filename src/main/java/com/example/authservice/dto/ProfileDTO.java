package com.example.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO para representar el perfil del usuario autenticado.
 * 
 * Contiene información extraída del token JWT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para representar el perfil del usuario autenticado")
public class ProfileDTO {

    @Schema(description = "Subject (ID del usuario en el Authorization Server)", example = "123e4567-e89b-12d3-a456-426614174000")
    private String subject;

    @Schema(description = "Nombre de usuario preferido", example = "johndoe")
    private String username;

    @Schema(description = "Email del usuario", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Roles del usuario", example = "[\"USER\", \"ADMIN\"]")
    private List<String> roles;

    @Schema(description = "Todos los claims del token JWT")
    private Map<String, Object> allClaims;

    @Schema(description = "Fecha de expiración del token (timestamp)", example = "1704067200")
    private Long expirationTime;

    @Schema(description = "Fecha de emisión del token (timestamp)", example = "1704063600")
    private Long issuedAt;
}
