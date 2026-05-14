package com.finnode.payment.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento consumido desde ledger-service cuando el registro de asientos falló.
 *
 * Razones: timeout, error de base de datos, problema de validación contable, etc.
 */
public record LedgerRecordingFailedEvent(
        UUID transactionId,
        String reason,
        Instant timestamp
) {}

