package com.finnode.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento consumido desde account-service cuando una reserva de fondos fue exitosa.
 */
public record FundsReservedEvent(
        UUID transactionId,
        UUID accountId,
        BigDecimal amount,
        Instant timestamp
) {}

