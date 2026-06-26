package com.finnode.auth.service;



import com.finnode.auth.dto.AuthResponse;
import com.finnode.auth.dto.LoginRequest;
import com.finnode.auth.dto.RefreshRequest;
import com.finnode.auth.dto.RegisterRequest;
import com.finnode.auth.event.UserRegisteredEvent;
import com.finnode.auth.exception.InvalidCredentialsException;
import com.finnode.auth.exception.UserAlreadyExistsException;
import com.finnode.auth.model.Role;
import com.finnode.auth.model.User;
import com.finnode.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Servicio principal del auth-service. Orquesta los tres flujos de identidad:
 *
 *  1. register() → valida, persiste y publica evento Kafka
 *  2. login()    → autentica y emite par de tokens JWT
 *  3. refresh()  → renueva el access_token con un refresh_token válido
 *
 * Este servicio es el único punto que coordina UserRepository, JwtService
 * y KafkaTemplate. El controller solo delega aquí.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // -----------------------------------------------------------------------
    // Dependencias inyectadas por constructor (Lombok @RequiredArgsConstructor)
    // -----------------------------------------------------------------------

    private final UserRepository   userRepository;
    private final JwtService       jwtService;
    private final PasswordEncoder  passwordEncoder;
    private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;

    // -----------------------------------------------------------------------
    // Configuración del topic Kafka (inyectado desde application.yml)
    // -----------------------------------------------------------------------

    @Value("${kafka.topics.user-registered}")
    private String userRegisteredTopic;     // ej: "user.registered"

    // -----------------------------------------------------------------------
    // Tiempo de vida del access_token (para incluirlo en la respuesta)
    // -----------------------------------------------------------------------

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;         // en milisegundos

    // -----------------------------------------------------------------------
    // Flujo 1: Registro de nuevo usuario
    // -----------------------------------------------------------------------

    /**
     * Registra un nuevo usuario en el sistema FinNode.
     *
     * Pasos:
     *  1. Verifica que el email no esté ya registrado
     *  2. Encripta la contraseña con BCrypt
     *  3. Persiste el usuario en PostgreSQL
     *  4. Publica UserRegisteredEvent a Kafka (account-service escucha esto)
     *  5. Genera y retorna el par de tokens JWT
     *
     * @param request DTO con fullName, email y password (ya validado por @Valid en el controller)
     * @return AuthResponse con access_token, refresh_token y metadata
     * @throws UserAlreadyExistsException si el email ya existe en la base de datos
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Intentando registrar usuario con email: {}", request.email());

        // Paso 1: verificar unicidad del email
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registro rechazado — email ya registrado: {}", request.email());
            throw new UserAlreadyExistsException(
                    "El email ya está registrado: " + request.email()
            );
        }

        // Paso 2: construir y persistir el usuario
        User newUser = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.valueOf("USER"))
                .active(true)
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("Usuario registrado exitosamente — id: {}", savedUser.getId());

        // Paso 3: publicar evento a Kafka para que account-service cree la cuenta bancaria
        publishUserRegisteredEvent(savedUser);

        // Paso 4: generar y retornar tokens JWT
        return buildAuthResponse(savedUser);
    }

    // -----------------------------------------------------------------------
    // Flujo 2: Login
    // -----------------------------------------------------------------------

    /**
     * Autentica un usuario y emite un par de tokens JWT.
     *
     * Pasos:
     *  1. Busca el usuario por email (si no existe → 401)
     *  2. Verifica que la cuenta esté activa
     *  3. Compara la contraseña con BCrypt (si no coincide → 401)
     *  4. Genera access_token + refresh_token y los retorna
     *
     * ⚠️ Se usa el mismo mensaje de error para "usuario no encontrado" y
     * "contraseña incorrecta" para no revelar qué campo falló (seguridad).
     *
     * @param request DTO con email y password
     * @return AuthResponse con los tokens JWT
     * @throws InvalidCredentialsException si las credenciales son inválidas
     */
    public AuthResponse login(LoginRequest request) {
        log.info("Intento de login para email: {}", request.email());

        // Paso 1: buscar usuario — mismo error que contraseña incorrecta (no revelar si existe)
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login fallido — email no encontrado: {}", request.email());
                    return new InvalidCredentialsException("Credenciales inválidas");
                });

        // Paso 2: verificar que la cuenta está activa
        if (!user.isActive()) {
            log.warn("Login rechazado — cuenta inactiva para: {}", request.email());
            throw new InvalidCredentialsException("Credenciales inválidas");
        }

        // Paso 3: verificar contraseña con BCrypt
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login fallido — contraseña incorrecta para: {}", request.email());
            throw new InvalidCredentialsException("Credenciales inválidas");
        }

        log.info("Login exitoso — userId: {}", user.getId());

        // Paso 4: generar y retornar tokens
        return buildAuthResponse(user);
    }

    // -----------------------------------------------------------------------
    // Flujo 3: Renovación de token
    // -----------------------------------------------------------------------

    /**
     * Renueva el access_token a partir de un refresh_token válido.
     *
     * El usuario no necesita re-ingresar su contraseña; basta con presentar
     * un refresh_token que no haya expirado y sea auténtico.
     *
     * Pasos:
     *  1. Valida la firma y expiración del refresh_token
     *  2. Verifica que sea de tipo "refresh" (no un access_token reutilizado)
     *  3. Extrae el userId y busca el usuario en BD (para datos actualizados)
     *  4. Genera un nuevo par de tokens y los retorna
     *
     * @param request DTO con el refreshToken
     * @return AuthResponse con nuevo access_token (y nuevo refresh_token rotado)
     * @throws InvalidCredentialsException si el refresh_token es inválido o expiró
     */
    public AuthResponse refresh(RefreshRequest request) {
        log.info("Solicitud de refresh de token recibida");

        // Paso 1 y 2: validar que sea un refresh_token legítimo
        if (!jwtService.isRefreshTokenValid(request.refreshToken())) {
            log.warn("Refresh rechazado — token inválido o expirado");
            throw new InvalidCredentialsException("Refresh token inválido o expirado");
        }

        // Paso 3: extraer userId y cargar usuario actualizado desde BD
        UUID userId = jwtService.extractUserId(request.refreshToken());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Refresh rechazado — userId no encontrado en BD: {}", userId);
                    return new InvalidCredentialsException("Refresh token inválido");
                });

        // Verificar que la cuenta sigue activa
        if (!user.isActive()) {
            log.warn("Refresh rechazado — cuenta inactiva para userId: {}", userId);
            throw new InvalidCredentialsException("Refresh token inválido");
        }

        log.info("Refresh exitoso — nuevo token generado para userId: {}", userId);

        // Paso 4: generar nuevo par de tokens (refresh token rotation)
        return buildAuthResponse(user);
    }

    // -----------------------------------------------------------------------
    // Métodos privados de soporte
    // -----------------------------------------------------------------------

    /**
     * Construye el AuthResponse con el par de tokens JWT para un usuario dado.
     * Centraliza la generación para que register(), login() y refresh() usen
     * exactamente la misma lógica de emisión.
     *
     * @param user entidad User ya persistida en BD
     * @return AuthResponse listo para enviar al cliente
     */
    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                String.valueOf(user.getRole())
        );
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiry / 1000)   // convertir ms → segundos para el cliente
                .build();
    }

    /**
     * Publica el evento UserRegisteredEvent al topic de Kafka configurado.
     *
     * account-service escucha este topic y crea automáticamente
     * la cuenta bancaria del nuevo usuario al recibirlo.
     *
     * @param user usuario recién persistido en BD
     */
    private void publishUserRegisteredEvent(User user) {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(userRegisteredTopic, user.getId().toString(), event);
        log.info("Evento UserRegisteredEvent publicado a Kafka — userId: {}", user.getId());
    }
}