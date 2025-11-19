package com.example.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO (Data Transfer Object) para la entidad User.
 * 
 * Se utiliza para transferir datos entre capas sin exponer
 * la estructura interna de la entidad.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para representar un usuario")
public class UserDTO {

    @Schema(description = "ID único del usuario", example = "1")
    private Long id;

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 3, max = 50, message = "El nombre de usuario debe tener entre 3 y 50 caracteres")
    @Schema(description = "Nombre de usuario", example = "johndoe", required = true)
    private String username;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe tener un formato válido")
    @Schema(description = "Email del usuario", example = "john.doe@example.com", required = true)
    private String email;

    @Size(max = 100, message = "El nombre completo no puede exceder 100 caracteres")
    @Schema(description = "Nombre completo del usuario", example = "John Doe")
    private String fullName;

    @Schema(description = "Indica si el usuario está activo", example = "true")
    private Boolean active;
}
