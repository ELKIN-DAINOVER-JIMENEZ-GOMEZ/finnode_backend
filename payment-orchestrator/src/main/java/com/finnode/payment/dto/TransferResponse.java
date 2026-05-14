package com.finnode.payment.dto;

import com.finnode.payment.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de salida para el endpoint POST /payments/transfer.
 *
 * <p>El status retornado siempre será {@code PENDING} en una respuesta exitosa
 * (HTTP 202 Accepted), ya que el Saga continúa de forma asíncrona.
 * El resultado final llega al frontend vía WebSocket.
 */
public record TransferResponse(

        String transactionId,

        TransactionStatus status,

        BigDecimal amount,

        String currency,

        LocalDateTime timestamp,

        String message

) {}