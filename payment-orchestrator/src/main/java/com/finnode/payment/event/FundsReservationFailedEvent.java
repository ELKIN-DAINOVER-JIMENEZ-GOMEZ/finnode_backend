package com.finnode.payment.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento consumido desde account-service cuando la reserva de fondos falló.
 */
public record FundsReservationFailedEvent(
        UUID transactionId,
        UUID accountId,
        String reason,
        Instant timestamp
) {}

