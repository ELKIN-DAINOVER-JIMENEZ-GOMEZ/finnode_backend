package com.finnode.payment.dto;

import com.finnode.payment.model.SagaStep;
import com.finnode.payment.model.TransactionStatus;

import java.time.LocalDateTime;

/**
 * DTO de salida para el endpoint GET /payments/{transactionId}.
 *
 * <p>Expone el estado actual del Saga con suficiente detalle para que
 * el frontend muestre el progreso de la transacción y, en caso de fallo,
 * el motivo al usuario.
 *
 * <p>{@code fraudRiskScore} se incluye para transparencia: el usuario
 * puede ver el score que generó su transacción, incluso si fue aprobada.
 *
 * <p>{@code failureReason} es {@code null} si la transacción está
 * en curso o fue completada exitosamente.
 */
public record TransactionStatusResponse(

        String transactionId,

        TransactionStatus status,

        SagaStep currentStep,

        Double fraudRiskScore,

        String failureReason,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {}