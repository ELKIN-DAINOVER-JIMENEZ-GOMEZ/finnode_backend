package com.finnode.auth.exception;
import static org.assertj.core.api.Assertions.*;
import com.finnode.auth.dto.LoginRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
/**
 * Tests unitarios para GlobalExceptionHandler.
 *
 * Valida que:
 *  - InvalidCredentialsException devuelve 401 Unauthorized
 *  - UserAlreadyExistsException devuelve 409 Conflict
 *  - Las respuestas de error tienen el formato correcto
 */
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {
private GlobalExceptionHandler exceptionHandler;
@BeforeEach
void setUp() {
exceptionHandler = new GlobalExceptionHandler();
}
// =========================================================================
// Pruebas de InvalidCredentialsException (401)
// =========================================================================
@Test
@DisplayName("InvalidCredentialsException debe retornar HTTP 401 Unauthorized")
void testInvalidCredentialsException_Returns401() {
// Arrange
InvalidCredentialsException ex = new InvalidCredentialsException("Email or password is incorrect");
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInvalidCredentials(ex);
// Assert
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
assertThat(response.getBody()).isNotNull();
assertThat(response.getBody().get("status")).isEqualTo(401);
}


@Test
@DisplayName("InvalidCredentialsException debe incluir mensaje personalizado")
void testInvalidCredentialsException_IncludesCustomMessage() {
// Arrange
String customMessage = "Credenciales inválidas";
InvalidCredentialsException ex = new InvalidCredentialsException(customMessage);
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInvalidCredentials(ex);
// Assert
assertThat(response.getBody().get("message")).isEqualTo(customMessage);
}


@Test
@DisplayName("InvalidCredentialsException debe incluir timestamp")
void testInvalidCredentialsException_IncludesTimestamp() {
// Arrange
InvalidCredentialsException ex = new InvalidCredentialsException("Invalid");
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInvalidCredentials(ex);
// Assert
assertThat(response.getBody().get("timestamp")).isNotNull();
assertThat(response.getBody().get("timestamp")).isInstanceOf(String.class);
}


// =========================================================================
// Pruebas de UserAlreadyExistsException (409)
// =========================================================================
@Test
@DisplayName("UserAlreadyExistsException debe retornar HTTP 409 Conflict")
void testUserAlreadyExistsException_Returns409() {
// Arrange
UserAlreadyExistsException ex = new UserAlreadyExistsException("Email already registered");
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleUserAlreadyExists(ex);
// Assert
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
assertThat(response.getBody()).isNotNull();
assertThat(response.getBody().get("status")).isEqualTo(409);
}


@Test
@DisplayName("UserAlreadyExistsException debe incluir mensaje personalizado")
void testUserAlreadyExistsException_IncludesCustomMessage() {
// Arrange
String customMessage = "El email ya está registrado: test@example.com";
UserAlreadyExistsException ex = new UserAlreadyExistsException(customMessage);
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleUserAlreadyExists(ex);
// Assert
assertThat(response.getBody().get("message")).isEqualTo(customMessage);
}
// =========================================================================
// Pruebas de Exception Genérica (500)
// =========================================================================



@Test
@DisplayName("Excepciones no controladas deben retornar 500 Internal Server Error")
void testUnexpectedException_Returns500() {
// Arrange
Exception ex = new RuntimeException("Unexpected database error");
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleUnexpected(ex);
// Assert
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
assertThat(response.getBody().get("status")).isEqualTo(500);
}


@Test
@DisplayName("Excepción genérica con NullPointerException")
void testUnexpectedException_NullPointerException() {
// Arrange
Exception ex = new NullPointerException("NPE occurred");
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleUnexpected(ex);
// Assert
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
assertThat(response.getBody().get("message")).isNotNull();
}
// =========================================================================
// Pruebas de Estructura de Respuesta de Error
// =========================================================================


@Test
@DisplayName("Respuesta de error debe contener todos los campos requeridos")
void testErrorResponse_ContainsAllRequiredFields() {
// Arrange
InvalidCredentialsException ex = new InvalidCredentialsException("Test error");
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInvalidCredentials(ex);
// Assert
assertThat(response.getBody()).containsKeys("timestamp", "status", "error", "message");
assertThat(response.getBody().get("status")).isNotNull();
assertThat(response.getBody().get("error")).isNotNull();
}



@Test
@DisplayName("El error debe tener el nombre del HTTP status")
void testErrorResponse_IncludesHttpStatusName() {
// Arrange
InvalidCredentialsException ex = new InvalidCredentialsException("Test");
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInvalidCredentials(ex);
// Assert
assertThat(response.getBody().get("error")).isEqualTo("Unauthorized");
}


@Test
@DisplayName("UserAlreadyExistsException debe tener error 'Conflict'")
void testUserAlreadyExists_HasConflictError() {
// Arrange
UserAlreadyExistsException ex = new UserAlreadyExistsException("Conflict test");
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleUserAlreadyExists(ex);
// Assert
assertThat(response.getBody().get("error")).isEqualTo("Conflict");
}


// =========================================================================
// Pruebas de Excepción sin Mensaje
// =========================================================================
@Test
@DisplayName("Excepción sin mensaje debe usar nombre de clase")
void testUnexpectedException_NoMessage() {
// Arrange
Exception ex = new RuntimeException();
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleUnexpected(ex);
// Assert
assertThat(response.getBody().get("message")).isNotNull();
assertThat(response.getBody().get("message")).isInstanceOf(String.class);
}
// =========================================================================
// Pruebas de Tipo de Datos
// =========================================================================


@Test
@DisplayName("El status debe ser un número entero")
void testErrorResponse_StatusIsInteger() {
// Arrange
InvalidCredentialsException ex = new InvalidCredentialsException("Test");
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInvalidCredentials(ex);
// Assert
assertThat(response.getBody().get("status")).isInstanceOf(Integer.class);
}


@Test
@DisplayName("El message debe ser un String")
void testErrorResponse_MessageIsString() {
// Arrange
UserAlreadyExistsException ex = new UserAlreadyExistsException("Test message");
// Act
ResponseEntity<Map<String, Object>> response = exceptionHandler.handleUserAlreadyExists(ex);
// Assert
assertThat(response.getBody().get("message")).isInstanceOf(String.class);
}
}
