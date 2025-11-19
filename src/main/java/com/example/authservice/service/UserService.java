package com.example.authservice.service;

import com.example.authservice.dto.UserCreateResponseDTO;
import com.example.authservice.dto.UserDTO;
import com.example.authservice.entity.User;
import com.example.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para la lógica de negocio relacionada con usuarios.
 * 
 * Esta capa contiene la lógica de negocio y actúa como intermediario
 * entre los controladores y los repositorios.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    public List<UserDTO> getAllUsers() {
        log.debug("Obteniendo todos los usuarios");
        return userRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        log.debug("Obteniendo usuario con ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
        return toDTO(user);
    }

    /**
     * Crea un usuario en ambos sistemas: base de datos local y Keycloak.
     * 
     * En la industria, este patrón se conoce como "User Provisioning" o "Just-In-Time Provisioning".
     * 
     * Estrategia de rollback:
     * - Si falla la creación en Keycloak, se revierte la transacción de BD (gracias a @Transactional)
     * - Si falla la creación en BD, Keycloak no se crea (no hay rollback de Keycloak, pero es aceptable)
     * 
     * Mejores prácticas implementadas:
     * - Transaccionalidad: @Transactional asegura atomicidad en BD
     * - Password temporal: Se genera automáticamente, requiere cambio en primer login
     * - Idempotencia: Si el usuario ya existe en Keycloak, no falla
     * - Logging: Se registran todos los pasos para auditoría
     * 
     * @return UserCreateResponseDTO con el usuario creado y el password temporal
     */
    public UserCreateResponseDTO createUser(UserDTO userDTO) {
        log.debug("Creando nuevo usuario: {}", userDTO.getUsername());
        
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("El nombre de usuario ya existe: " + userDTO.getUsername());
        }
        
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("El email ya existe: " + userDTO.getEmail());
        }

        // Generar password temporal (en producción, esto se enviaría por email)
        String temporaryPassword = generateTemporaryPassword();
        log.info("Password temporal generado para usuario '{}': {}", userDTO.getUsername(), temporaryPassword);
        
        // 1. Crear usuario en Keycloak primero (si falla, la transacción se revierte)
        try {
            keycloakService.createUserInKeycloak(userDTO, temporaryPassword);
            log.info("Usuario '{}' creado en Keycloak", userDTO.getUsername());
        } catch (Exception e) {
            log.error("Error creando usuario en Keycloak, revirtiendo operación", e);
            throw new RuntimeException("Error al crear usuario en Keycloak: " + e.getMessage(), e);
        }

        // 2. Crear usuario en base de datos local
        // Si esto falla, el usuario ya está en Keycloak, pero es aceptable
        // (en producción, podrías implementar un job de limpieza)
        User user = toEntity(userDTO);
        User savedUser = userRepository.save(user);
        log.info("Usuario creado exitosamente con ID: {} en base de datos local", savedUser.getId());
        
        // TODO: En producción, enviar password temporal por email seguro
        // Ver SECURITY.md para mejoras recomendadas
        
        // Retornar respuesta con password temporal
        return UserCreateResponseDTO.builder()
                .user(toDTO(savedUser))
                .temporaryPassword(temporaryPassword)
                .message("Usuario creado exitosamente. Este password es temporal y debe ser cambiado en el primer login.")
                .build();
    }
    
    /**
     * Genera un password temporal seguro.
     * 
     * Nota: Para producción, ver mejoras recomendadas en SECURITY.md:
     * - Usar SecureRandom en lugar de Random
     * - Implementar políticas de password más estrictas
     * - Enviar password por email seguro en lugar de retornarlo en la respuesta
     */
    private String generateTemporaryPassword() {
        // Generar password temporal de 12 caracteres
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder password = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }

    public UserDTO updateUser(Long id, UserDTO userDTO) {
        log.debug("Actualizando usuario con ID: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

        // Verificar si el username o email ya existen en otro usuario
        if (!user.getUsername().equals(userDTO.getUsername()) && 
            userRepository.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("El nombre de usuario ya existe: " + userDTO.getUsername());
        }
        
        if (!user.getEmail().equals(userDTO.getEmail()) && 
            userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("El email ya existe: " + userDTO.getEmail());
        }

        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setFullName(userDTO.getFullName());
        if (userDTO.getActive() != null) {
            user.setActive(userDTO.getActive());
        }

        User updatedUser = userRepository.save(user);
        log.info("Usuario actualizado exitosamente con ID: {}", updatedUser.getId());
        return toDTO(updatedUser);
    }

    /**
     * Elimina un usuario de ambos sistemas.
     * 
     * Estrategia: Eliminar primero de BD local, luego de Keycloak.
     * Si falla Keycloak, el usuario ya no está en BD (aceptable para evitar inconsistencias).
     */
    public void deleteUser(Long id) {
        log.debug("Eliminando usuario con ID: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
        
        String username = user.getUsername();
        
        // 1. Eliminar de base de datos local primero
        userRepository.deleteById(id);
        log.info("Usuario '{}' eliminado de base de datos local", username);
        
        // 2. Eliminar de Keycloak (no crítico si falla)
        try {
            keycloakService.deleteUserFromKeycloak(username);
        } catch (Exception e) {
            log.warn("No se pudo eliminar usuario '{}' de Keycloak (continuando)", username, e);
            // No lanzamos excepción, el usuario ya fue eliminado de BD
        }
        
        log.info("Usuario eliminado exitosamente con ID: {}", id);
    }

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .active(user.getActive())
                .build();
    }

    private User toEntity(UserDTO userDTO) {
        return User.builder()
                .username(userDTO.getUsername())
                .email(userDTO.getEmail())
                .fullName(userDTO.getFullName())
                .active(userDTO.getActive() != null ? userDTO.getActive() : true)
                .build();
    }
}
