package com.finnode.ledger.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.*;

import com.finnode.ledger.dto.BalanceResponse;
import com.finnode.ledger.dto.LedgerEntryResponse;
import com.finnode.ledger.dto.LedgerHistoryResponse;
import com.finnode.ledger.exception.LedgerEntryNotFoundException;
import com.finnode.ledger.model.EntryType;
import com.finnode.ledger.service.BalanceCalculatorService;
import com.finnode.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pruebas unitarias para LedgerController.
 *
 * Valida:
 *   - El controlador delega correctamente a LedgerService
 *   - Las excepciones se propagan correctamente
 *   - Los DTOs de entrada/salida se mapean correctamente
 *   - La validación de ownership con JWT funciona
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerController Unit Tests")
class LedgerControllerTest {

    private LedgerController ledgerController;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private BalanceCalculatorService balanceCalculatorService;

    @Mock
    private Jwt mockJwt;

    private UUID accountId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        ledgerController = new LedgerController(ledgerService, balanceCalculatorService);
        accountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
    }

    // =========================================================================
    // GET /ledger/{accountId}/entries - Historial completo
    // =========================================================================

    @Test
    @DisplayName("GET /ledger/{accountId}/entries exitoso devuelve historial")
    void getHistory_Success() {
        // Arrange
        LedgerHistoryResponse history = LedgerHistoryResponse.builder()
                .accountId(accountId)
                .entries(List.of())
                .totalDebits(new BigDecimal("1000.00"))
                .totalCredits(new BigDecimal("2000.00"))
                .netBalance(new BigDecimal("1000.00"))
                .periodFrom(Instant.now().minusSeconds(3600))
                .periodTo(Instant.now())
                .totalEntries(2)
                .build();

        when(ledgerService.getHistory(accountId)).thenReturn(history);
        when(mockJwt.getClaimAsString("accountId")).thenReturn(accountId.toString());

        // Act
        ResponseEntity<LedgerHistoryResponse> result = ledgerController.getHistory(accountId, mockJwt);

        // Assert
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().accountId()).isEqualTo(accountId);
        assertThat(result.getBody().totalEntries()).isEqualTo(2);
        assertThat(result.getBody().totalDebits()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getBody().totalCredits()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(result.getBody().netBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("GET /ledger/{accountId}/entries con cuenta no existente lanza LedgerEntryNotFoundException")
    void getHistory_AccountNotFound() {
        // Arrange
        when(ledgerService.getHistory(accountId))
                .thenThrow(new LedgerEntryNotFoundException(accountId));
        when(mockJwt.getClaimAsString("accountId")).thenReturn(accountId.toString());

        // Act & Assert
        assertThatThrownBy(() -> ledgerController.getHistory(accountId, mockJwt))
                .isInstanceOf(LedgerEntryNotFoundException.class);
    }

    @Test
    @DisplayName("GET /ledger/{accountId}/entries sin ownership válido lanza AccessDeniedException")
    void getHistory_UnauthorizedAccess() {
        // Arrange
        UUID otherAccountId = UUID.randomUUID();
        when(mockJwt.getClaimAsString("accountId")).thenReturn(otherAccountId.toString());

        // Act & Assert
        assertThatThrownBy(() -> ledgerController.getHistory(accountId, mockJwt))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("No tienes permiso");
    }

    // =========================================================================
    // GET /ledger/{accountId}/entries?from=...&to=... - Historial filtrado
    // =========================================================================

    @Test
    @DisplayName("GET /ledger/{accountId}/entries?from=...&to=... exitoso devuelve historial filtrado")
    void getHistoryByDateRange_Success() {
        // Arrange
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");

        LedgerHistoryResponse filteredHistory = LedgerHistoryResponse.builder()
                .accountId(accountId)
                .entries(List.of())
                .totalDebits(new BigDecimal("500.00"))
                .totalCredits(new BigDecimal("1500.00"))
                .netBalance(new BigDecimal("1000.00"))
                .periodFrom(from)
                .periodTo(to)
                .totalEntries(1)
                .build();

        when(ledgerService.getHistoryByDateRange(accountId, from, to))
                .thenReturn(filteredHistory);
        when(mockJwt.getClaimAsString("accountId")).thenReturn(accountId.toString());

        // Act
        ResponseEntity<LedgerHistoryResponse> result = ledgerController.getHistoryByDateRange(
                accountId, from, to, mockJwt);

        // Assert
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().periodFrom()).isEqualTo(from);
        assertThat(result.getBody().periodTo()).isEqualTo(to);
        assertThat(result.getBody().totalEntries()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /ledger/{accountId}/entries?from=...&to=... con fechas invertidas lanza IllegalArgumentException")
    void getHistoryByDateRange_InvertedDates() {
        // Arrange
        Instant from = Instant.parse("2025-01-31T23:59:59Z");
        Instant to = Instant.parse("2025-01-01T00:00:00Z");
        when(mockJwt.getClaimAsString("accountId")).thenReturn(accountId.toString());

        // Act & Assert
        assertThatThrownBy(() -> ledgerController.getHistoryByDateRange(
                accountId, from, to, mockJwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }

    @Test
    @DisplayName("GET /ledger/{accountId}/entries?from=...&to=... con cuenta no existente lanza excepción")
    void getHistoryByDateRange_AccountNotFound() {
        // Arrange
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");

        when(ledgerService.getHistoryByDateRange(accountId, from, to))
                .thenThrow(new LedgerEntryNotFoundException(accountId));
        when(mockJwt.getClaimAsString("accountId")).thenReturn(accountId.toString());

        // Act & Assert
        assertThatThrownBy(() -> ledgerController.getHistoryByDateRange(
                accountId, from, to, mockJwt))
                .isInstanceOf(LedgerEntryNotFoundException.class);
    }

    // =========================================================================
    // GET /ledger/{accountId}/balance - Saldo actual
    // =========================================================================

    @Test
    @DisplayName("GET /ledger/{accountId}/balance exitoso devuelve saldo actual")
    void getBalance_Success() {
        // Arrange
        BalanceResponse balanceResponse = BalanceResponse.builder()
                .accountId(accountId)
                .computedBalance(new BigDecimal("1500000.00"))
                .totalEntries(47)
                .asOf(Instant.now())
                .build();

        when(balanceCalculatorService.calculateCurrentBalance(accountId))
                .thenReturn(balanceResponse);
        when(mockJwt.getClaimAsString("accountId")).thenReturn(accountId.toString());

        // Act
        ResponseEntity<BalanceResponse> result = ledgerController.getBalance(accountId, null, mockJwt);

        // Assert
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().accountId()).isEqualTo(accountId);
        assertThat(result.getBody().computedBalance()).isEqualByComparingTo(new BigDecimal("1500000.00"));
        assertThat(result.getBody().totalEntries()).isEqualTo(47);
    }

    @Test
    @DisplayName("GET /ledger/{accountId}/balance?asOf=... devuelve saldo histórico")
    void getBalance_WithAsOf() {
        // Arrange
        Instant asOf = Instant.parse("2025-06-15T10:31:00Z");
        BalanceResponse historicalBalance = BalanceResponse.builder()
                .accountId(accountId)
                .computedBalance(new BigDecimal("500000.00"))
                .totalEntries(20)
                .asOf(asOf)
                .build();

        when(balanceCalculatorService.calculateBalanceAsOf(accountId, asOf))
                .thenReturn(historicalBalance);
        when(mockJwt.getClaimAsString("accountId")).thenReturn(accountId.toString());

        // Act
        ResponseEntity<BalanceResponse> result = ledgerController.getBalance(accountId, asOf, mockJwt);

        // Assert
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().asOf()).isEqualTo(asOf);
        assertThat(result.getBody().computedBalance()).isEqualByComparingTo(new BigDecimal("500000.00"));
    }

    @Test
    @DisplayName("GET /ledger/{accountId}/balance con cuenta no existente lanza LedgerEntryNotFoundException")
    void getBalance_AccountNotFound() {
        // Arrange
        when(balanceCalculatorService.calculateCurrentBalance(accountId))
                .thenThrow(new LedgerEntryNotFoundException(accountId));
        when(mockJwt.getClaimAsString("accountId")).thenReturn(accountId.toString());

        // Act & Assert
        assertThatThrownBy(() -> ledgerController.getBalance(accountId, null, mockJwt))
                .isInstanceOf(LedgerEntryNotFoundException.class);
    }

    @Test
    @DisplayName("GET /ledger/{accountId}/balance sin ownership válido lanza AccessDeniedException")
    void getBalance_UnauthorizedAccess() {
        // Arrange
        UUID otherAccountId = UUID.randomUUID();
        when(mockJwt.getClaimAsString("accountId")).thenReturn(otherAccountId.toString());

        // Act & Assert
        assertThatThrownBy(() -> ledgerController.getBalance(accountId, null, mockJwt))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // =========================================================================
    // GET /ledger/transactions/{transactionId} - Asientos por transacción
    // =========================================================================

    @Test
    @DisplayName("GET /ledger/transactions/{transactionId} exitoso devuelve asientos")
    void getEntriesByTransaction_Success() {
        // Arrange
        List<LedgerEntryResponse> entries = List.of(
                LedgerEntryResponse.builder()
                        .entryId(UUID.randomUUID())
                        .transactionId(transactionId)
                        .accountId(UUID.randomUUID())
                        .entryType(EntryType.DEBIT)
                        .amount(new BigDecimal("500000.00"))
                        .currency("COP")
                        .build(),
                LedgerEntryResponse.builder()
                        .entryId(UUID.randomUUID())
                        .transactionId(transactionId)
                        .accountId(UUID.randomUUID())
                        .entryType(EntryType.CREDIT)
                        .amount(new BigDecimal("500000.00"))
                        .currency("COP")
                        .build()
        );

        when(ledgerService.getEntriesByTransactionId(transactionId))
                .thenReturn(entries);

        // Act
        ResponseEntity<List<LedgerEntryResponse>> result = ledgerController.getEntriesByTransaction(
                transactionId, mockJwt);

        // Assert
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).entryType()).isEqualTo(EntryType.DEBIT);
        assertThat(result.getBody().get(1).entryType()).isEqualTo(EntryType.CREDIT);
    }

    @Test
    @DisplayName("GET /ledger/transactions/{transactionId} con transacción revertida devuelve 4 asientos")
    void getEntriesByTransaction_WithReversals() {
        // Arrange: Una transacción que fue revertida debe retornar 4 asientos
        List<LedgerEntryResponse> entries = List.of(
                LedgerEntryResponse.builder()
                        .entryId(UUID.randomUUID())
                        .transactionId(transactionId)
                        .entryType(EntryType.DEBIT)
                        .amount(new BigDecimal("1000.00"))
                        .build(),
                LedgerEntryResponse.builder()
                        .entryId(UUID.randomUUID())
                        .transactionId(transactionId)
                        .entryType(EntryType.CREDIT)
                        .amount(new BigDecimal("1000.00"))
                        .build(),
                LedgerEntryResponse.builder()
                        .entryId(UUID.randomUUID())
                        .transactionId(transactionId)
                        .entryType(EntryType.REVERSAL_DEBIT)
                        .amount(new BigDecimal("1000.00"))
                        .build(),
                LedgerEntryResponse.builder()
                        .entryId(UUID.randomUUID())
                        .transactionId(transactionId)
                        .entryType(EntryType.REVERSAL_CREDIT)
                        .amount(new BigDecimal("1000.00"))
                        .build()
        );

        when(ledgerService.getEntriesByTransactionId(transactionId))
                .thenReturn(entries);

        // Act
        ResponseEntity<List<LedgerEntryResponse>> result = ledgerController.getEntriesByTransaction(
                transactionId, mockJwt);

        // Assert
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(4);
    }

    @Test
    @DisplayName("GET /ledger/transactions/{transactionId} con transacción no existente devuelve lista vacía")
    void getEntriesByTransaction_NotFound() {
        // Arrange
        when(ledgerService.getEntriesByTransactionId(transactionId))
                .thenReturn(List.of());

        // Act
        ResponseEntity<List<LedgerEntryResponse>> result = ledgerController.getEntriesByTransaction(
                transactionId, mockJwt);

        // Assert
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    // =========================================================================
    // Pruebas de Delegación Correcta
    // =========================================================================

    @Test
    @DisplayName("Controller debe delegar a LedgerService sin modificar parámetros")
    void testController_DelegationToService() {
        // Arrange
        LedgerHistoryResponse history = LedgerHistoryResponse.builder()
                .accountId(accountId)
                .entries(List.of())
                .totalDebits(BigDecimal.ZERO)
                .totalCredits(BigDecimal.ZERO)
                .netBalance(BigDecimal.ZERO)
                .periodFrom(Instant.now())
                .periodTo(Instant.now())
                .totalEntries(0)
                .build();

        when(ledgerService.getHistory(accountId)).thenReturn(history);
        when(mockJwt.getClaimAsString("accountId")).thenReturn(accountId.toString());

        // Act
        ledgerController.getHistory(accountId, mockJwt);

        // Assert - verificar que se pasó exactamente el accountId recibido
        org.mockito.Mockito.verify(ledgerService).getHistory(accountId);
    }
}