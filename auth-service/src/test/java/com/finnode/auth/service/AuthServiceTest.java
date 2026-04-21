package com.finnode.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests unitarios para AuthService.
 *
 * Cubre los tres flujos principales:
 *  1. Registro: validación de email único, encriptación de contraseña, publicación de evento Kafka
 *  2. Login: validación de credenciales, generación de tokens
 *  3. Refresh: renovación de access token con refresh token válido
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtService jwtService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;

	private AuthService authService;

	private final String testEmail = "test@example.com";
	private final String testPassword = "SecurePassword123!";
	private final String testFullName = "Test User";
	private final UUID testUserId = UUID.randomUUID();
	private final String accessToken = "test.access.token";
	private final String refreshToken = "test.refresh.token";

	@BeforeEach
	void setUp() {
		authService = new AuthService(userRepository, jwtService, passwordEncoder, kafkaTemplate);
		ReflectionTestUtils.setField(authService, "userRegisteredTopic", "user.registered");
		ReflectionTestUtils.setField(authService, "accessTokenExpiry", 900_000L);
	}

	// =========================================================================
	// Pruebas de REGISTRO (Register)
	// =========================================================================

	@Test
	@DisplayName("Registro exitoso crea usuario y publica evento Kafka")
	void testRegister_Success() {
		// Arrange
		RegisterRequest request = new RegisterRequest(testFullName, testEmail, testPassword);
		String encodedPassword = "hashed_password";

		when(userRepository.existsByEmail(testEmail)).thenReturn(false);
		when(passwordEncoder.encode(testPassword)).thenReturn(encodedPassword);

		User savedUser = User.builder()
				.id(testUserId)
				.fullName(testFullName)
				.email(testEmail)
				.passwordHash(encodedPassword)
				.role(Role.USER)
				.active(true)
				.createdAt(Instant.now())
				.build();

		when(userRepository.save(any(User.class))).thenReturn(savedUser);

		// Act
		AuthResponse response = authService.register(request);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getAccessToken()).isEqualTo(accessToken);
		assertThat(response.getRefreshToken()).isEqualTo(refreshToken);
		assertThat(response.getTokenType()).isEqualTo("Bearer");

		// Verificar que se guardó el usuario
		verify(userRepository).save(argThat(user ->
				user.getFullName().equals(testFullName) &&
				user.getEmail().equals(testEmail) &&
				user.getPasswordHash().equals(encodedPassword)
		));

		// Verificar que se publicó el evento Kafka
		verify(kafkaTemplate).send(eq("user.registered"), eq(testUserId.toString()), any(UserRegisteredEvent.class));
	}

	@Test
	@DisplayName("Email duplicado lanza UserAlreadyExistsException")
	void testRegister_EmailAlreadyExists() {
		// Arrange
		RegisterRequest request = new RegisterRequest(testFullName, testEmail, testPassword);
		when(userRepository.existsByEmail(testEmail)).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> authService.register(request))
				.isInstanceOf(UserAlreadyExistsException.class)
				.hasMessageContaining("already registered");

		// Verificar que no se guardó nada
		verify(userRepository, never()).save(any());
		verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
	}

	@Test
	@DisplayName("La contraseña debe encriptarse con PasswordEncoder")
	void testRegister_PasswordIsEncrypted() {
		// Arrange
		RegisterRequest request = new RegisterRequest(testFullName, testEmail, testPassword);
		String encodedPassword = "encrypted_hash";

		when(userRepository.existsByEmail(testEmail)).thenReturn(false);
		when(passwordEncoder.encode(testPassword)).thenReturn(encodedPassword);

		User savedUser = User.builder()
				.id(testUserId)
				.fullName(testFullName)
				.email(testEmail)
				.passwordHash(encodedPassword)
				.role(Role.USER)
				.active(true)
				.createdAt(Instant.now())
				.build();

		when(userRepository.save(any(User.class))).thenReturn(savedUser);

		// Act
		authService.register(request);

		// Assert
		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());

		User savedUserArg = userCaptor.getValue();
		assertThat(savedUserArg.getPasswordHash()).isEqualTo(encodedPassword);
		assertThat(savedUserArg.getPasswordHash()).isNotEqualTo(testPassword); // no debe estar en texto plano
	}

	@Test
	@DisplayName("Usuario registrado debe tener rol USER por defecto")
	void testRegister_UserRoleAssigned() {
		// Arrange
		RegisterRequest request = new RegisterRequest(testFullName, testEmail, testPassword);

		when(userRepository.existsByEmail(testEmail)).thenReturn(false);
		when(passwordEncoder.encode(testPassword)).thenReturn("hashed");

		User savedUser = User.builder()
				.id(testUserId)
				.fullName(testFullName)
				.email(testEmail)
				.passwordHash("hashed")
				.role(Role.USER)
				.active(true)
				.createdAt(Instant.now())
				.build();

		when(userRepository.save(any(User.class))).thenReturn(savedUser);

		// Act
		authService.register(request);

		// Assert
		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());

		assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.USER);
		assertThat(userCaptor.getValue().isActive()).isTrue();
	}

	// =========================================================================
	// Pruebas de LOGIN
	// =========================================================================

	@Test
	@DisplayName("Login exitoso con credenciales válidas")
	void testLogin_Success() {
		// Arrange
		LoginRequest request = new LoginRequest(testEmail, testPassword);

		User user = User.builder()
				.id(testUserId)
				.fullName(testFullName)
				.email(testEmail)
				.passwordHash("hashed_password")
				.role(Role.USER)
				.active(true)
				.createdAt(Instant.now())
				.build();

		when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(testPassword, "hashed_password")).thenReturn(true);

		// Act
		AuthResponse response = authService.login(request);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getAccessToken()).isEqualTo(accessToken);
		assertThat(response.getRefreshToken()).isEqualTo(refreshToken);

		verify(userRepository).findByEmail(testEmail);
		verify(passwordEncoder).matches(testPassword, "hashed_password");
	}

	@Test
	@DisplayName("Email no existe lanza InvalidCredentialsException")
	void testLogin_EmailNotFound() {
		// Arrange
		LoginRequest request = new LoginRequest("nonexistent@example.com", testPassword);

		when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());
		when(jwtService.generateAccessToken(any(), anyString(), anyString()))
				.thenReturn(accessToken);
		when(jwtService.generateRefreshToken(any()))
				.thenReturn(refreshToken);

		// Act & Assert
		assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessageContaining("Credenciales inválidas");
	}

	@Test
	@DisplayName("Contraseña incorrecta lanza InvalidCredentialsException")
	void testLogin_WrongPassword() {
		// Arrange
		LoginRequest request = new LoginRequest(testEmail, "wrongPassword");

		User user = User.builder()
				.id(testUserId)
				.fullName(testFullName)
				.email(testEmail)
				.passwordHash("correct_hashed_password")
				.role(Role.USER)
				.active(true)
				.createdAt(Instant.now())
				.build();

		when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrongPassword", "correct_hashed_password")).thenReturn(false);
		when(jwtService.generateAccessToken(any(), anyString(), anyString()))
				.thenReturn(accessToken);
		when(jwtService.generateRefreshToken(any()))
				.thenReturn(refreshToken);

		// Act & Assert
		assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessageContaining("Credenciales inválidas");
	}

	@Test
	@DisplayName("Cuenta inactiva no puede hacer login")
	void testLogin_InactiveAccount() {
		// Arrange
		LoginRequest request = new LoginRequest(testEmail, testPassword);

		User inactiveUser = User.builder()
				.id(testUserId)
				.fullName(testFullName)
				.email(testEmail)
				.passwordHash("hashed_password")
				.role(Role.USER)
				.active(false) // cuenta inactiva
				.createdAt(Instant.now())
				.build();

		when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(inactiveUser));
		when(jwtService.generateAccessToken(any(), anyString(), anyString()))
				.thenReturn(accessToken);
		when(jwtService.generateRefreshToken(any()))
				.thenReturn(refreshToken);

		// Act & Assert
		assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessageContaining("Credenciales inválidas");
	}

	// =========================================================================
	// Pruebas de REFRESH
	// =========================================================================

	@Test
	@DisplayName("Refresh exitoso genera nuevo access token")
	void testRefresh_Success() {
		// Arrange
		RefreshRequest request = new RefreshRequest(refreshToken);

		User user = User.builder()
				.id(testUserId)
				.fullName(testFullName)
				.email(testEmail)
				.passwordHash("hashed")
				.role(Role.USER)
				.active(true)
				.createdAt(Instant.now())
				.build();

		when(jwtService.isRefreshTokenValid(refreshToken)).thenReturn(true);
		when(jwtService.extractUserId(refreshToken)).thenReturn(testUserId);
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));

		// Act
		AuthResponse response = authService.refresh(request);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getAccessToken()).isEqualTo(accessToken);
		assertThat(response.getRefreshToken()).isEqualTo(refreshToken);

		verify(jwtService).isRefreshTokenValid(refreshToken);
		verify(jwtService).extractUserId(refreshToken);
	}

	@Test
	@DisplayName("Refresh token inválido lanza InvalidCredentialsException")
	void testRefresh_InvalidToken() {
		// Arrange
		RefreshRequest request = new RefreshRequest("invalid.refresh.token");

		when(jwtService.isRefreshTokenValid("invalid.refresh.token")).thenReturn(false);

		// Act & Assert
		assertThatThrownBy(() -> authService.refresh(request))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessageContaining("invalid or expired");
	}

	@Test
	@DisplayName("Refresh con usuario no encontrado lanza InvalidCredentialsException")
	void testRefresh_UserNotFound() {
		// Arrange
		RefreshRequest request = new RefreshRequest(refreshToken);

		when(jwtService.isRefreshTokenValid(refreshToken)).thenReturn(true);
		when(jwtService.extractUserId(refreshToken)).thenReturn(testUserId);
		when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
		when(jwtService.generateAccessToken(any(), anyString(), anyString()))
				.thenReturn(accessToken);
		when(jwtService.generateRefreshToken(any()))
				.thenReturn(refreshToken);

		// Act & Assert
		assertThatThrownBy(() -> authService.refresh(request))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessageContaining("Refresh token inválido");
	}

	@Test
	@DisplayName("Refresh con cuenta inactiva falla")
	void testRefresh_InactiveAccount() {
		// Arrange
		RefreshRequest request = new RefreshRequest(refreshToken);

		User inactiveUser = User.builder()
				.id(testUserId)
				.fullName(testFullName)
				.email(testEmail)
				.passwordHash("hashed")
				.role(Role.USER)
				.active(false)
				.createdAt(Instant.now())
				.build();

		when(jwtService.isRefreshTokenValid(refreshToken)).thenReturn(true);
		when(jwtService.extractUserId(refreshToken)).thenReturn(testUserId);
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(inactiveUser));
		when(jwtService.generateAccessToken(any(), anyString(), anyString()))
				.thenReturn(accessToken);
		when(jwtService.generateRefreshToken(any()))
				.thenReturn(refreshToken);

		// Act & Assert
		assertThatThrownBy(() -> authService.refresh(request))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessageContaining("Refresh token inválido");
	}

	// =========================================================================
	// Pruebas de Métodos Privados (indirectas)
	// =========================================================================

	@Test
	@DisplayName("AuthResponse contiene valores correctos")
	void testAuthResponse_ContainsExpectedValues() {
		// Arrange
		RegisterRequest request = new RegisterRequest(testFullName, testEmail, testPassword);

		User savedUser = User.builder()
				.id(testUserId)
				.fullName(testFullName)
				.email(testEmail)
				.passwordHash("hashed")
				.role(Role.USER)
				.active(true)
				.createdAt(Instant.now())
				.build();

		when(userRepository.existsByEmail(testEmail)).thenReturn(false);
		when(passwordEncoder.encode(testPassword)).thenReturn("hashed");
		when(userRepository.save(any(User.class))).thenReturn(savedUser);

		// Act
		AuthResponse response = authService.register(request);

		// Assert
		assertThat(response.getTokenType()).isEqualTo("Bearer");
		assertThat(response.getExpiresIn()).isGreaterThan(0);
		assertThat(response.getAccessToken()).isNotBlank();
		assertThat(response.getRefreshToken()).isNotBlank();
	}

	// =========================================================================
	// Pruebas de Transactionalidad
	// =========================================================================

	@Test
	@DisplayName("El flujo de registro incluye genera claims correctos")
	void testRegister_JwtServiceCalledWithCorrectClaims() {
		// Arrange
		RegisterRequest request = new RegisterRequest(testFullName, testEmail, testPassword);

		User savedUser = User.builder()
				.id(testUserId)
				.fullName(testFullName)
				.email(testEmail)
				.passwordHash("hashed")
				.role(Role.USER)
				.active(true)
				.createdAt(Instant.now())
				.build();

		when(userRepository.existsByEmail(testEmail)).thenReturn(false);
		when(passwordEncoder.encode(testPassword)).thenReturn("hashed");
		when(userRepository.save(any(User.class))).thenReturn(savedUser);

		// Act
		authService.register(request);

		// Assert - verificar que JwtService fue llamado con los claims correctos
		ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
		ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);

		verify(jwtService).generateAccessToken(userIdCaptor.capture(), emailCaptor.capture(), roleCaptor.capture());

		assertThat(userIdCaptor.getValue()).isEqualTo(testUserId);
		assertThat(emailCaptor.getValue()).isEqualTo(testEmail);
		assertThat(roleCaptor.getValue()).isEqualTo("USER");
	}
}
