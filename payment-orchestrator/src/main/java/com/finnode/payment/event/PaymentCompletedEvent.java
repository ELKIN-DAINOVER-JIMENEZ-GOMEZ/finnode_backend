package com.finnode.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado cuando los fondos han sido cobrados y el Saga avanza al registro contable.
 */
public record PaymentCompletedEvent(
        UUID transactionId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        Instant timestamp
) {}

