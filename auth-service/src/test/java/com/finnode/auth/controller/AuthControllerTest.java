package com.finnode.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.*;

import com.finnode.auth.dto.AuthResponse;
import com.finnode.auth.dto.LoginRequest;
import com.finnode.auth.dto.RefreshRequest;
import com.finnode.auth.dto.RegisterRequest;
import com.finnode.auth.exception.InvalidCredentialsException;
import com.finnode.auth.exception.UserAlreadyExistsException;
import com.finnode.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitarios para AuthController.
 *
 * Valida:
 *  - El controller delega correctamente a AuthService
 *  - Las excepciones se propagan correctamente
 *  - Los DTOs de entrada/salida se mapean correctamente
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Unit Tests")
class AuthControllerTest {

	private AuthController authController;

	@Mock
	private AuthService authService;

	private final String testEmail = "test@example.com";
	private final String testPassword = "SecurePassword123!";
	private final String testFullName = "Test User";

	@BeforeEach
	void setUp() {
		authController = new AuthController(authService);
	}

	// =========================================================================
	// Pruebas de POST /auth/register
	// =========================================================================

	@Test
	@DisplayName("POST /auth/register exitoso devuelve AuthResponse")
	void testRegister_Success() {
		// Arrange
		RegisterRequest request = new RegisterRequest(testFullName, testEmail, testPassword);

		AuthResponse response = AuthResponse.builder()
				.accessToken("test.access.token")
				.refreshToken("test.refresh.token")
				.tokenType("Bearer")
				.expiresIn(900)
				.build();

		when(authService.register(any(RegisterRequest.class))).thenReturn(response);

		// Act
		var result = authController.register(request);

		// Assert
		assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(result.getBody()).isNotNull();
		assertThat(result.getBody().getAccessToken()).isEqualTo("test.access.token");
		assertThat(result.getBody().getRefreshToken()).isEqualTo("test.refresh.token");
	}

	@Test
	@DisplayName("POST /auth/register con email duplicado lanza UserAlreadyExistsException")
	void testRegister_DuplicateEmail() {
		// Arrange
		RegisterRequest request = new RegisterRequest(testFullName, testEmail, testPassword);

		when(authService.register(any(RegisterRequest.class)))
				.thenThrow(new UserAlreadyExistsException("El email ya está registrado"));

		// Act & Assert
		assertThatThrownBy(() -> authController.register(request))
				.isInstanceOf(UserAlreadyExistsException.class)
				.hasMessageContaining("El email ya está registrado");
	}

	// =========================================================================
	// Pruebas de POST /auth/login
	// =========================================================================

	@Test
	@DisplayName("POST /auth/login exitoso devuelve AuthResponse")
	void testLogin_Success() {
		// Arrange
		LoginRequest request = new LoginRequest(testEmail, testPassword);

		AuthResponse response = AuthResponse.builder()
				.accessToken("test.access.token")
				.refreshToken("test.refresh.token")
				.tokenType("Bearer")
				.expiresIn(900)
				.build();

		when(authService.login(any(LoginRequest.class))).thenReturn(response);

		// Act
		var result = authController.login(request);

		// Assert
		assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(result.getBody()).isNotNull();
		assertThat(result.getBody().getAccessToken()).isEqualTo("test.access.token");
	}

	@Test
	@DisplayName("POST /auth/login con credenciales inválidas lanza InvalidCredentialsException")
	void testLogin_InvalidCredentials() {
		// Arrange
		LoginRequest request = new LoginRequest(testEmail, "wrongPassword");

		when(authService.login(any(LoginRequest.class)))
				.thenThrow(new InvalidCredentialsException("Credenciales inválidas"));

		// Act & Assert
		assertThatThrownBy(() -> authController.login(request))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessageContaining("inválidas");
	}

	// =========================================================================
	// Pruebas de POST /auth/refresh
	// =========================================================================

	@Test
	@DisplayName("POST /auth/refresh exitoso devuelve AuthResponse")
	void testRefresh_Success() {
		// Arrange
		RefreshRequest request = new RefreshRequest("test.refresh.token");

		AuthResponse response = AuthResponse.builder()
				.accessToken("new.access.token")
				.refreshToken("new.refresh.token")
				.tokenType("Bearer")
				.expiresIn(900)
				.build();

		when(authService.refresh(any(RefreshRequest.class))).thenReturn(response);

		// Act
		var result = authController.refresh(request);

		// Assert
		assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(result.getBody()).isNotNull();
		assertThat(result.getBody().getAccessToken()).isEqualTo("new.access.token");
	}

	@Test
	@DisplayName("POST /auth/refresh con token inválido lanza InvalidCredentialsException")
	void testRefresh_InvalidToken() {
		// Arrange
		RefreshRequest request = new RefreshRequest("invalid.token");

		when(authService.refresh(any(RefreshRequest.class)))
				.thenThrow(new InvalidCredentialsException("Refresh token inválido o expirado"));

		// Act & Assert
		assertThatThrownBy(() -> authController.refresh(request))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessageContaining("Refresh token inválido o expirado");
	}

	// =========================================================================
	// Pruebas de Delegación Correcta
	// =========================================================================

	@Test
	@DisplayName("Controller debe pasar RequestBody sin modificar al servicio")
	void testController_PassesRequestUnmodified() {
		// Arrange
		LoginRequest request = new LoginRequest(testEmail, testPassword);
		AuthResponse response = AuthResponse.builder()
				.accessToken("token")
				.refreshToken("refresh")
				.tokenType("Bearer")
				.expiresIn(900)
				.build();

		when(authService.login(request)).thenReturn(response);

		// Act
		authController.login(request);

		// Assert - verificar que se pasó exactamente el request recibido
		org.mockito.Mockito.verify(authService).login(request);
	}
}
