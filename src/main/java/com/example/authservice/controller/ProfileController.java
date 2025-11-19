package com.example.authservice.controller;

import com.example.authservice.dto.ProfileDTO;
import com.example.authservice.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la gestión del perfil del usuario autenticado.
 * 
 * Este controlador extrae información del token JWT para mostrar
 * el perfil del usuario que está haciendo la petición.
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "API para gestión del perfil del usuario autenticado")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final ProfileService profileService;

    @Operation(
        summary = "Obtener perfil del usuario autenticado",
        description = """
            Retorna la información del perfil del usuario autenticado.
            La información se extrae del token JWT.
            
            El token JWT debe contener los siguientes claims:
            - `sub`: Subject (ID del usuario)
            - `preferred_username`: Nombre de usuario
            - `email`: Email del usuario
            - `realm_access.roles`: Roles del usuario
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Perfil obtenido exitosamente",
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Token JWT inválido o expirado"
        )
    })
    @GetMapping
    public ResponseEntity<ProfileDTO> getProfile(
            @AuthenticationPrincipal Jwt jwt) {
        ProfileDTO profile = profileService.getProfileFromJwt(jwt);
        return ResponseEntity.ok(profile);
    }

    @Operation(
        summary = "Obtener información del token JWT",
        description = """
            Retorna información detallada del token JWT actual.
            Útil para debugging y entender qué claims contiene el token.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Información del token obtenida exitosamente"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Token JWT inválido o expirado"
        )
    })
    @GetMapping("/token-info")
    public ResponseEntity<ProfileDTO> getTokenInfo(
            @AuthenticationPrincipal Jwt jwt) {
        ProfileDTO tokenInfo = profileService.getTokenInfo(jwt);
        return ResponseEntity.ok(tokenInfo);
    }
}
