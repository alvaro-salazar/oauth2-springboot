package com.example.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la respuesta de creación de usuario.
 * 
 * Incluye el password temporal generado que debe ser compartido
 * con el usuario para su primer login.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta al crear un nuevo usuario, incluye el password temporal")
public class UserCreateResponseDTO {

    @Schema(description = "Información del usuario creado")
    private UserDTO user;

    @Schema(
        description = "Password temporal generado. El usuario debe usar este password para su primer login y Keycloak le pedirá cambiarlo.",
        example = "TempPass123!@#",
        required = true
    )
    private String temporaryPassword;

    @Schema(
        description = "Mensaje informativo sobre el password temporal",
        example = "Este password es temporal. El usuario debe cambiarlo en su primer login."
    )
    private String message;
}

