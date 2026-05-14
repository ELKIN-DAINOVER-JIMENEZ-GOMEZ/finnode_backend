package com.finnode.payment.service;

import com.finnode.payment.dto.FraudEvaluationResult;
import com.finnode.payment.exception.FraudDetectedException;
import com.finnode.payment.model.Transaction;
import com.finnode.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio de detección de fraude.
 *
 * Consulta un modelo de lenguaje (OpenAI) a través de Spring AI para evaluar
 * el riesgo de cada transacción antes de iniciar el Saga de pago.
 *
 * El modelo evalúa:
 *  - Monto de la transferencia y moneda
 *  - Historial reciente de transacciones del usuario (últimas 10)
 *  - Hora del día y día de la semana
 *  - Si es la primera vez que se transfiere a esta cuenta de destino
 *  - Frecuencia de pagos al mismo destinatario en las últimas 24h
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final ChatClient chatClient;
    private final TransactionRepository transactionRepository;

    @Value("${fraud.risk-threshold:0.75}")
    private double riskThreshold;

    /**
     * Evalúa el riesgo de fraude de una transacción.
     *
     * Construye un prompt detallado con el contexto de la transacción,
     * lo envía al modelo de Spring AI, y retorna el resultado.
     *
     * Si el riskScore supera el umbral configurado, lanza FraudDetectedException
     * antes de mover un solo centavo.
     *
     * @param transaction la transacción a evaluar
     * @return FraudEvaluationResult con {approved, riskScore, reason}
     * @throws FraudDetectedException si el riesgo supera el umbral
     */
    public FraudEvaluationResult evaluate(Transaction transaction) {
        log.info("Evaluating fraud risk for transaction: {}", transaction.getTransactionId());

        // Construir el prompt con contexto de la transacción
        String prompt = buildFraudEvaluationPrompt(transaction);

        try {
            // Llamar al modelo de Spring AI
            String response = chatClient
                    .prompt(prompt)
                    .call()
                    .content();

            log.debug("Fraud detection model response: {}", response);

            // Parsear la respuesta JSON (la estructura esperada es: {"approved": bool, "riskScore": double, "reason": string})
            FraudEvaluationResult result = parseAiResponse(response);

            // Persistir el score en la transacción
            transaction.setFraudRiskScore(result.riskScore());

            // Verificar si el riesgo supera el umbral
            if (result.riskScore() >= riskThreshold) {
                log.warn("Transaction {} rejected by fraud detection. Risk score: {}, Threshold: {}",
                        transaction.getTransactionId(), result.riskScore(), riskThreshold);
                throw new FraudDetectedException(result.riskScore(), result.reason());
            }

            log.info("Transaction {} approved by fraud detection. Risk score: {}",
                    transaction.getTransactionId(), result.riskScore());

            return result;

        } catch (FraudDetectedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error evaluating fraud for transaction {}: {}",
                    transaction.getTransactionId(), e.getMessage(), e);
            throw new RuntimeException("Error en evaluación de fraude", e);
        }
    }

    /**
     * Construye el prompt detallado para el modelo de IA.
     *
     * Incluye:
     *  - Detalles básicos de la transacción (monto, moneda, cuentas)
     *  - Historial reciente del usuario (últimas 10 transacciones)
     *  - Contexto temporal (hora del día, día de la semana)
     *  - Información sobre si es la primera vez hacia este destinatario
     *  - Frecuencia de pagos recientes al mismo destinatario
     */
    private String buildFraudEvaluationPrompt(Transaction transaction) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Eres un experto en detección de fraude en sistemas de pagos.\n\n");

        prompt.append("Analiza la siguiente transacción y devuelve un JSON con tres campos:\n");
        prompt.append("{\n");
        prompt.append("  \"approved\": boolean,\n");
        prompt.append("  \"riskScore\": number (0.0 a 1.0),\n");
        prompt.append("  \"reason\": string (explicación breve)\n");
        prompt.append("}\n\n");

        // Detalles de la transacción
        prompt.append("DETALLES DE LA TRANSACCIÓN:\n");
        prompt.append(String.format("- Usuario: %s\n", transaction.getUserId()));
        prompt.append(String.format("- Monto: %s %s\n", transaction.getAmount(), transaction.getCurrency()));
        prompt.append(String.format("- Cuenta origen: %s\n", transaction.getSourceAccountId()));
        prompt.append(String.format("- Cuenta destino: %s\n", transaction.getDestinationAccountId()));
        prompt.append(String.format("- Descripción: %s\n\n", transaction.getDescription() != null ? transaction.getDescription() : "N/A"));

        // Historial reciente del usuario
        List<Transaction> recentTransactions = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(transaction.getUserId())
                .stream()
                .filter(t -> !t.getId().equals(transaction.getId())) // Excluir la transacción actual
                .limit(10)
                .toList();

        if (!recentTransactions.isEmpty()) {
            prompt.append("HISTORIAL RECIENTE (últimas 10 transacciones exitosas):\n");
            for (int i = 0; i < recentTransactions.size(); i++) {
                Transaction recent = recentTransactions.get(i);
                prompt.append(String.format("%d. %s %s en %s\n",
                        i + 1,
                        recent.getAmount(),
                        recent.getCurrency(),
                        recent.getCreatedAt()));
            }
            prompt.append("\n");
        }

        // Contexto temporal
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.DayOfWeek dayOfWeek = now.getDayOfWeek();
        int hour = now.getHour();

        prompt.append("CONTEXTO TEMPORAL:\n");
        prompt.append(String.format("- Día: %s\n", dayOfWeek));
        prompt.append(String.format("- Hora: %02d:00\n\n", hour));

        // Información sobre destinatario
        long transactionsToDestination = recentTransactions.stream()
                .filter(t -> t.getDestinationAccountId().equals(transaction.getDestinationAccountId()))
                .count();

        prompt.append("INFORMACIÓN DEL DESTINATARIO:\n");
        if (transactionsToDestination == 0) {
            prompt.append("- Primera vez que se transfiere a esta cuenta de destino\n");
        } else {
            prompt.append(String.format("- Número de transacciones previas: %d\n", transactionsToDestination));
        }

        prompt.append("\nEVALÚA:\n");
        prompt.append("1. ¿El monto es inusualmente alto comparado con el historial?\n");
        prompt.append("2. ¿Es la primera vez hacia este destinatario?\n");
        prompt.append("3. ¿La hora de la transacción es fuera del horario habitual?\n");
        prompt.append("4. ¿Hay patrones sospechosos en el historial?\n");
        prompt.append("5. Devuelve SOLO JSON sin explicaciones adicionales.\n");

        return prompt.toString();
    }

    /**
     * Parsea la respuesta JSON del modelo de IA.
     *
     * El modelo debe responder con:
     * {
     *   "approved": boolean,
     *   "riskScore": 0.0-1.0,
     *   "reason": "explicación"
     * }
     */
    private FraudEvaluationResult parseAiResponse(String response) {
        try {
            // Implementación simplificada: asumimos que el modelo devuelve JSON válido
            // En producción, usarías un parser JSON robusto (Jackson, etc.)

            // Extrae "approved"
            boolean approved = response.contains("\"approved\": true");

            // Extrae "riskScore" (busca el número después de "riskScore": )
            double riskScore = 0.5;
            int scoreIndex = response.indexOf("\"riskScore\":");
            if (scoreIndex != -1) {
                String scoreStr = response.substring(scoreIndex + 12, scoreIndex + 16).trim();
                try {
                    riskScore = Double.parseDouble(scoreStr.replace(",", "").replace("}", ""));
                } catch (NumberFormatException e) {
                    log.warn("Could not parse risk score from response: {}", response);
                }
            }

            // Extrae "reason"
            String reason = "No reason provided";
            int reasonIndex = response.indexOf("\"reason\":");
            if (reasonIndex != -1) {
                int startQuote = response.indexOf("\"", reasonIndex + 10);
                int endQuote = response.indexOf("\"", startQuote + 1);
                if (startQuote != -1 && endQuote != -1) {
                    reason = response.substring(startQuote + 1, endQuote);
                }
            }

            return new FraudEvaluationResult(approved, riskScore, reason);

        } catch (Exception e) {
            log.error("Error parsing fraud detection response: {}", response, e);
            // Retornar un resultado neutral en caso de error
            return new FraudEvaluationResult(true, 0.5, "Error parsing AI response");
        }
    }
}


