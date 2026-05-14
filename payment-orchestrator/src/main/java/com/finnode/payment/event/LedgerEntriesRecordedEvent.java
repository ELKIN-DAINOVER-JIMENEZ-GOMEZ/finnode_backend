package com.finnode.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento consumido desde ledger-service cuando los asientos fueron registrados.
 */
public record LedgerEntriesRecordedEvent(
        UUID transactionId,
        UUID debitEntryId,
        UUID creditEntryId,
        BigDecimal amount,
        String currency,
        boolean reversalConfirmed,
        Instant timestamp
) {}

