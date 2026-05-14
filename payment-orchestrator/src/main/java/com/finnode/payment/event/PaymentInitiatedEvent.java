package com.finnode.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado para arrancar el Saga de pago.
 */
public record PaymentInitiatedEvent(
        UUID transactionId,
        UUID sourceAccountId,
        BigDecimal amount,
        String currency,
        Instant timestamp
) {}

