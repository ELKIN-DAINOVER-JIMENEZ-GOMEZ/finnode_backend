package com.finnode.account.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("AccountNotFoundException retorna 404")
    void handleNotFoundReturns404() {
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleNotFound(
                new AccountNotFoundException(UUID.randomUUID())
        );
        Map<String, Object> body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("status", "error", "message", "timestamp");
        assertThat(body.get("status")).isEqualTo(404);
    }

    @Test
    @DisplayName("InsufficientFundsException retorna 422")
    void handleInsufficientFundsReturns422() {
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInsufficientFunds(
                new InsufficientFundsException(UUID.randomUUID(), new BigDecimal("10.00"), new BigDecimal("30.00"))
        );
        Map<String, Object> body = response.getBody();

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(422);
        assertThat(body.get("error")).isEqualTo("Unprocessable Entity");
    }

    @Test
    @DisplayName("AccountSuspendedException retorna 403")
    void handleSuspendedReturns403() {
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleSuspended(
                new AccountSuspendedException(UUID.randomUUID())
        );
        Map<String, Object> body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(403);
    }

    @Test
    @DisplayName("Exception genérica retorna 500 con mensaje estándar")
    void handleGenericReturns500() {
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGeneric(
                new RuntimeException("boom")
        );
        Map<String, Object> body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(500);
        assertThat(body.get("message")).isEqualTo("Error interno del servidor");
    }
}


