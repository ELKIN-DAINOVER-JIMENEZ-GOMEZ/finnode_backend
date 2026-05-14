package com.finnode.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado para indicar que una transacción debe ser revertida (compensación Saga).
 */
public record PaymentReversedEvent(
        UUID transactionId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String reversalReason,
        Instant timestamp
) {}

