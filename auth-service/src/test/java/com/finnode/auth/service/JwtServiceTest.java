package com.finnode.auth.service;

import static org.assertj.core.api.Assertions.*;

import com.finnode.auth.config.JwtConfig;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitarios para JwtService.
 * Valida que:
 *  - Se generan tokens con claims correctos
 *  - Los tokens incluyen la expiración esperada
 *  - Los claims se extraen correctamente
 *  - Se rechazan tokens inválidos/expirados
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Tests")
class JwtServiceTest {

	private JwtService jwtService;

	private static final String JWT_SECRET = "my-super-secret-key-that-must-be-long-enough-for-256-bits-minimum";
	private static final long ACCESS_TOKEN_EXPIRY = 900_000;  // 15 minutos
	private static final long REFRESH_TOKEN_EXPIRY = 604_800_000;  // 7 días
	private static final String ISSUER = "finnode-auth";

	private final UUID testUserId = UUID.randomUUID();
	private final String testEmail = "test@example.com";
	private final String testRole = "USER";

	@BeforeEach
	void setUp() {
		JwtConfig jwtConfig = new JwtConfig();
		jwtConfig.setSecret(JWT_SECRET);
		jwtConfig.setAccessTokenExpiry(ACCESS_TOKEN_EXPIRY);
		jwtConfig.setRefreshTokenExpiry(REFRESH_TOKEN_EXPIRY);
		jwtConfig.setIssuer(ISSUER);
		jwtService = new JwtService(jwtConfig);
	}

	// =========================================================================
	// Pruebas de Generación de Access Token
	// =========================================================================

	@Test
	@DisplayName("Debe generar un access token válido con todos los claims")
	void testGenerateAccessToken_Success() {
		// Act
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Assert
		assertThat(token).isNotBlank();
		assertThat(token.split("\\.")).hasSize(3); // JWT tiene 3 partes separadas por punto
	}

	@Test
	@DisplayName("El access token debe contener el userId en el claim")
	void testGenerateAccessToken_ContainsUserId() {
		// Act
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Assert
		UUID extractedUserId = jwtService.extractUserId(token);
		assertThat(extractedUserId).isEqualTo(testUserId);
	}

	@Test
	@DisplayName("El access token debe contener el email en el claim")
	void testGenerateAccessToken_ContainsEmail() {
		// Act
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Assert
		String extractedEmail = jwtService.extractEmail(token);
		assertThat(extractedEmail).isEqualTo(testEmail);
	}

	@Test
	@DisplayName("El access token debe contener el role en el claim")
	void testGenerateAccessToken_ContainsRole() {
		// Act
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Assert
		String extractedRole = jwtService.extractRole(token);
		assertThat(extractedRole).isEqualTo(testRole);
	}

	@Test
	@DisplayName("El access token debe tener la expiración correcta")
	void testGenerateAccessToken_ExpirationTime() {
		// Act
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);
		Date expiration = jwtService.extractExpiration(token);
		Date now = new Date();

		// Assert: la expiración debe estar entre ahora y ahora + ACCESS_TOKEN_EXPIRY
		long timeDiff = expiration.getTime() - now.getTime();
		assertThat(timeDiff)
				.isGreaterThanOrEqualTo(ACCESS_TOKEN_EXPIRY - 5000) // permitir 5 segundos de margen
				.isLessThanOrEqualTo(ACCESS_TOKEN_EXPIRY + 5000);
	}

	// =========================================================================
	// Pruebas de Generación de Refresh Token
	// =========================================================================

	@Test
	@DisplayName("Debe generar un refresh token válido")
	void testGenerateRefreshToken_Success() {
		// Act
		String token = jwtService.generateRefreshToken(testUserId);

		// Assert
		assertThat(token).isNotBlank();
		assertThat(token.split("\\.")).hasSize(3);
	}

	@Test
	@DisplayName("El refresh token debe contener el userId")
	void testGenerateRefreshToken_ContainsUserId() {
		// Act
		String token = jwtService.generateRefreshToken(testUserId);

		// Assert
		UUID extractedUserId = jwtService.extractUserId(token);
		assertThat(extractedUserId).isEqualTo(testUserId);
	}

	@Test
	@DisplayName("El refresh token debe tener la expiración correcta")
	void testGenerateRefreshToken_ExpirationTime() {
		// Act
		String token = jwtService.generateRefreshToken(testUserId);
		Date expiration = jwtService.extractExpiration(token);
		Date now = new Date();

		// Assert
		long timeDiff = expiration.getTime() - now.getTime();
		assertThat(timeDiff)
				.isGreaterThanOrEqualTo(REFRESH_TOKEN_EXPIRY - 5000)
				.isLessThanOrEqualTo(REFRESH_TOKEN_EXPIRY + 5000);
	}

	// =========================================================================
	// Pruebas de Validación de Tokens
	// =========================================================================

	@Test
	@DisplayName("Un access token válido debe pasar la validación")
	void testIsTokenValid_ValidAccessToken() {
		// Arrange
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Act
		boolean isValid = jwtService.isTokenValid(token);

		// Assert
		assertThat(isValid).isTrue();
	}

	@Test
	@DisplayName("Un refresh token válido debe pasar la validación")
	void testIsTokenValid_ValidRefreshToken() {
		// Arrange
		String token = jwtService.generateRefreshToken(testUserId);

		// Act
		boolean isValid = jwtService.isTokenValid(token);

		// Assert
		assertThat(isValid).isTrue();
	}

	@Test
	@DisplayName("Un token vacío debe fallar la validación")
	void testIsTokenValid_EmptyToken() {
		// Act & Assert
		assertThat(jwtService.isTokenValid("")).isFalse();
	}

	@Test
	@DisplayName("Un token inválido debe fallar la validación")
	void testIsTokenValid_InvalidToken() {
		// Act & Assert
		assertThat(jwtService.isTokenValid("invalid.token.here")).isFalse();
	}

	@Test
	@DisplayName("Un token null debe fallar la validación sin lanzar excepción")
	void testIsTokenValid_NullToken() {
		// Act & Assert
		assertThat(jwtService.isTokenValid(null)).isFalse();
	}

	@Test
	@DisplayName("Un token con firma incorrecta debe fallar la validación")
	void testIsTokenValid_InvalidSignature() {
		// Arrange: generar un token y luego modificar su contenido
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);
		String[] parts = token.split("\\.");
		String tamperedToken = parts[0] + "." + parts[1] + ".invalidsignature";

		// Act
		boolean isValid = jwtService.isTokenValid(tamperedToken);

		// Assert
		assertThat(isValid).isFalse();
	}

	// =========================================================================
	// Pruebas de Validación de Refresh Token
	// =========================================================================

	@Test
	@DisplayName("Un refresh token válido debe pasar la validación de refresh")
	void testIsRefreshTokenValid_ValidRefreshToken() {
		// Arrange
		String token = jwtService.generateRefreshToken(testUserId);

		// Act
		boolean isValid = jwtService.isRefreshTokenValid(token);

		// Assert
		assertThat(isValid).isTrue();
	}

	@Test
	@DisplayName("Un access token no debe pasar la validación de refresh")
	void testIsRefreshTokenValid_AccessTokenRejected() {
		// Arrange: intentar usar un access_token como refresh_token
		String accessToken = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Act
		boolean isValid = jwtService.isRefreshTokenValid(accessToken);

		// Assert
		assertThat(isValid).isFalse();
	}

	@Test
	@DisplayName("Un refresh token inválido debe fallar la validación")
	void testIsRefreshTokenValid_InvalidToken() {
		// Act & Assert
		assertThat(jwtService.isRefreshTokenValid("invalid.token")).isFalse();
	}

	// =========================================================================
	// Pruebas de Extracción de Claims
	// =========================================================================

	@Test
	@DisplayName("Debe extraer el userId correctamente del token")
	void testExtractUserId_Success() {
		// Arrange
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Act
		UUID extractedUserId = jwtService.extractUserId(token);

		// Assert
		assertThat(extractedUserId).isEqualTo(testUserId);
	}

	@Test
	@DisplayName("Debe extraer el email correctamente del token")
	void testExtractEmail_Success() {
		// Arrange
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Act
		String extractedEmail = jwtService.extractEmail(token);

		// Assert
		assertThat(extractedEmail).isEqualTo(testEmail);
	}

	@Test
	@DisplayName("Debe extraer el role correctamente del token")
	void testExtractRole_Success() {
		// Arrange
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Act
		String extractedRole = jwtService.extractRole(token);

		// Assert
		assertThat(extractedRole).isEqualTo(testRole);
	}

	@Test
	@DisplayName("Debe extraer la expiración correctamente del token")
	void testExtractExpiration_Success() {
		// Arrange
		String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Act
		Date expiration = jwtService.extractExpiration(token);

		// Assert
		assertThat(expiration).isNotNull();
		assertThat(expiration.getTime()).isGreaterThan(System.currentTimeMillis());
	}

	// =========================================================================
	// Pruebas de Edge Cases
	// =========================================================================

	@Test
	@DisplayName("Dos access tokens generados para el mismo usuario deben ser diferentes")
	void testGenerateAccessToken_DifferentTokensEachTime() throws InterruptedException {
		// Act
		String token1 = jwtService.generateAccessToken(testUserId, testEmail, testRole);
		Thread.sleep(10); // Esperar 10ms para que cambie el timestamp
		String token2 = jwtService.generateAccessToken(testUserId, testEmail, testRole);

		// Assert: aunque tienen los mismos claims, tienen timestamps diferentes
		assertThat(token1).isNotEqualTo(token2);
	}

	@Test
	@DisplayName("Diferentes roles deben extractarse correctamente")
	void testGenerateAccessToken_DifferentRoles() {
		// Act
		String tokenUSER = jwtService.generateAccessToken(testUserId, testEmail, "USER");
		String tokenADMIN = jwtService.generateAccessToken(testUserId, testEmail, "ADMIN");

		// Assert
		assertThat(jwtService.extractRole(tokenUSER)).isEqualTo("USER");
		assertThat(jwtService.extractRole(tokenADMIN)).isEqualTo("ADMIN");
	}

	@Test
	@DisplayName("Diferentes UUIDs deben extractarse correctamente")
	void testGenerateAccessToken_DifferentUserIds() {
		// Arrange
		UUID userId1 = UUID.randomUUID();
		UUID userId2 = UUID.randomUUID();

		// Act
		String token1 = jwtService.generateAccessToken(userId1, testEmail, testRole);
		String token2 = jwtService.generateAccessToken(userId2, testEmail, testRole);

		// Assert
		assertThat(jwtService.extractUserId(token1)).isEqualTo(userId1);
		assertThat(jwtService.extractUserId(token2)).isEqualTo(userId2);
	}
}
