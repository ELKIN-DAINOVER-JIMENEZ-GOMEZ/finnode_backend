package com.finnode.payment.service;

import com.finnode.payment.dto.FraudEvaluationResult;
import com.finnode.payment.exception.FraudDetectedException;
import com.finnode.payment.model.SagaStep;
import com.finnode.payment.model.Transaction;
import com.finnode.payment.model.TransactionStatus;
import com.finnode.payment.repository.TransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link FraudDetectionService}.
 *
 * ChatClient (Spring AI) usa una API fluida:
 *   chatClient.prompt(String).call().content()
 * Por eso se mockean también los "spec" intermedios (ChatClientRequestSpec y
 * CallResponseSpec) para poder controlar el texto JSON que "responde" el modelo.
 *
 * riskThreshold se inyecta con ReflectionTestUtils porque @Value no se procesa
 * fuera de un ApplicationContext de Spring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectionService")
class FraudDetectionServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private TransactionRepository transactionRepository;

    private FraudDetectionService fraudDetectionService;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        fraudDetectionService = new FraudDetectionService(chatClient, transactionRepository);
        ReflectionTestUtils.setField(fraudDetectionService, "riskThreshold", 0.75);

        transaction = Transaction.builder()
                .transactionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .sourceAccountId(UUID.randomUUID())
                .destinationAccountId(UUID.randomUUID())
                .amount(new BigDecimal("500000.00"))
                .currency("COP")
                .status(TransactionStatus.PENDING)
                .currentStep(SagaStep.FRAUD_CHECK)
                .build();

        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(transaction.getUserId()))
                .thenReturn(List.of());
    }

    private void mockAiResponse(String jsonResponse) {
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(jsonResponse);
    }

    @Test
    @DisplayName("Debe aprobar la transacción cuando el riskScore está por debajo del umbral")
    void evaluate_whenRiskScoreBelowThreshold_shouldApprove() {
        mockAiResponse("{\"approved\": true, \"riskScore\": 0.20, \"reason\": \"Comportamiento habitual\"}");

        FraudEvaluationResult result = fraudDetectionService.evaluate(transaction);

        assertThat(result.approved()).isTrue();
        assertThat(result.riskScore()).isEqualTo(0.20);
        assertThat(transaction.getFraudRiskScore()).isEqualTo(0.20);
    }

    @Test
    @DisplayName("Debe lanzar FraudDetectedException cuando el riskScore alcanza el umbral")
    void evaluate_whenRiskScoreAtThreshold_shouldThrowFraudDetectedException() {
        // Se usa un umbral de un solo decimal (0.7) y un riskScore de un solo decimal (0.7)
        // para probar la condición de borde ">= threshold" sin toparse con el bug de
        // truncado descrito en evaluate_knownBug_parseAiResponseTruncatesToOneDecimal().
        ReflectionTestUtils.setField(fraudDetectionService, "riskThreshold", 0.7);
        mockAiResponse("{\"approved\": false, \"riskScore\": 0.7, \"reason\": \"Monto inusual\"}");

        assertThatThrownBy(() -> fraudDetectionService.evaluate(transaction))
                .isInstanceOf(FraudDetectedException.class)
                .satisfies(ex -> {
                    FraudDetectedException fde = (FraudDetectedException) ex;
                    assertThat(fde.getRiskScore()).isEqualTo(0.7);
                    assertThat(fde.getReason()).isEqualTo("Monto inusual");
                });
    }

    @Test
    @DisplayName("Debe lanzar FraudDetectedException cuando el riskScore supera el umbral")
    void evaluate_whenRiskScoreAboveThreshold_shouldThrowFraudDetectedException() {
        mockAiResponse("{\"approved\": false, \"riskScore\": 0.9, \"reason\": \"Cuenta destino nueva y monto alto\"}");

        assertThatThrownBy(() -> fraudDetectionService.evaluate(transaction))
                .isInstanceOf(FraudDetectedException.class);

        assertThat(transaction.getFraudRiskScore()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("BUG CONOCIDO: parseAiResponse trunca el riskScore a un solo decimal")
    void evaluate_knownBug_parseAiResponseTruncatesToOneDecimal() {
        // parseAiResponse() usa un offset fijo de 4 caracteres tras "riskScore":,
        // por lo que un valor de dos decimales como 0.75 se interpreta como 0.7.
        // Este test documenta el comportamiento ACTUAL (no el deseado) para dejar
        // registrado el bug; se recomienda reemplazar el parsing manual por Jackson
        // (ObjectMapper) para leer el JSON real que devuelve el modelo.
        mockAiResponse("{\"approved\": false, \"riskScore\": 0.75, \"reason\": \"Monto inusual\"}");

        // Con el umbral por defecto (0.75) y el riskScore truncado a 0.7, la transacción
        // NO debería rechazarse (0.7 < 0.75) aunque el modelo haya dicho "approved: false"
        // y un riskScore real de 0.75 — evidencia clara del bug.
        FraudEvaluationResult result = fraudDetectionService.evaluate(transaction);

        assertThat(result.riskScore())
                .as("riskScore truncado por el bug de parsing: 0.75 -> 0.7")
                .isEqualTo(0.7);
        assertThat(transaction.getFraudRiskScore()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("Debe persistir el riskScore en la transacción incluso cuando es aprobada")
    void evaluate_shouldPersistRiskScoreOnTransaction() {
        mockAiResponse("{\"approved\": true, \"riskScore\": 0.3, \"reason\": \"Sin novedades\"}");

        fraudDetectionService.evaluate(transaction);

        assertThat(transaction.getFraudRiskScore()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("Debe envolver errores del ChatClient en RuntimeException")
    void evaluate_whenChatClientFails_shouldWrapInRuntimeException() {
        when(chatClient.prompt(anyString())).thenThrow(new IllegalStateException("Timeout llamando al modelo"));

        assertThatThrownBy(() -> fraudDetectionService.evaluate(transaction))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error en evaluación de fraude")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("No debe propagar como RuntimeException genérica si ya es FraudDetectedException")
    void evaluate_shouldNotDoubleWrapFraudDetectedException() {
        mockAiResponse("{\"approved\": false, \"riskScore\": 0.99, \"reason\": \"Fraude evidente\"}");

        assertThatThrownBy(() -> fraudDetectionService.evaluate(transaction))
                .isExactlyInstanceOf(FraudDetectedException.class);
    }

    @Test
    @DisplayName("Debe incluir el historial reciente del usuario en el prompt enviado al modelo")
    void evaluate_shouldIncludeRecentHistoryInPrompt() {
        Transaction previous = Transaction.builder()
                .transactionId(UUID.randomUUID())
                .userId(transaction.getUserId())
                .sourceAccountId(transaction.getSourceAccountId())
                .destinationAccountId(UUID.randomUUID())
                .amount(new BigDecimal("15000.00"))
                .currency("COP")
                .status(TransactionStatus.COMPLETED)
                .currentStep(SagaStep.CONFIRM_PAYMENT)
                .build();

        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(transaction.getUserId()))
                .thenReturn(List.of(previous));

        mockAiResponse("{\"approved\": true, \"riskScore\": 0.10, \"reason\": \"Destinatario conocido\"}");

        fraudDetectionService.evaluate(transaction);

        verify(chatClient).prompt(argThat((String prompt) ->
                prompt.contains("HISTORIAL RECIENTE") && prompt.contains("15000.00")));
    }

    @Test
    @DisplayName("Cuando el modelo responde JSON malformado, debe retornar resultado neutral en lugar de fallar")
    void evaluate_whenAiResponseIsMalformed_shouldReturnNeutralResult() {
        mockAiResponse("esto no es un json valido");

        FraudEvaluationResult result = fraudDetectionService.evaluate(transaction);

        // approved=false por defecto (no contiene "approved": true) y riskScore=0.5 (default),
        // por debajo del umbral 0.75, así que no debe lanzar excepción.
        assertThat(result.riskScore()).isEqualTo(0.5);
    }
}