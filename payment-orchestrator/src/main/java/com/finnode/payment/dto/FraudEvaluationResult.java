

package com.finnode.payment.dto;

/**
 * Resultado interno de la evaluación de fraude realizada por {@code FraudDetectionService}.
 *
 * <p>Este DTO no sale hacia el cliente directamente; es el contrato entre
 * {@code FraudDetectionService} y {@code PaymentService}. El {@code riskScore}
 * y {@code reason} se persisten en la entidad {@code Transaction} para
 * trazabilidad y auditoría.
 *
 * <p>El modelo de Spring AI responde en JSON estructurado que se deserializa
 * a este record:
 * <pre>
 * {
 *   "approved": false,
 *   "riskScore": 0.87,
 *   "reason": "Transacción de alto valor a cuenta nueva fuera del horario habitual del usuario"
 * }
 * </pre>
 *
 * @param approved   {@code true} si el riskScore está por debajo del umbral configurado
 * @param riskScore  valor entre 0.0 (sin riesgo) y 1.0 (fraude probable)
 * @param reason     explicación del modelo para el score asignado
 */
public record FraudEvaluationResult(

        boolean approved,

        double riskScore,

        String reason

) {}