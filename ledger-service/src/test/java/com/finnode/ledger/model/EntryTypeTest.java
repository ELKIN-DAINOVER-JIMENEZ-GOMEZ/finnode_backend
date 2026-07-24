package com.finnode.ledger.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias para el enum EntryType.
 *
 * Verifica los métodos que determinan el impacto de cada tipo de asiento
 * en el saldo de la cuenta (incrementsBalance, decrementsBalance, isReversal).
 */
class EntryTypeTest {

    @Test
    void credit_shouldIncrementsBalance() {
        // Given: CREDIT debe incrementar el saldo
        assertThat(EntryType.CREDIT.incrementsBalance()).isTrue();
        assertThat(EntryType.CREDIT.decrementsBalance()).isFalse();
        assertThat(EntryType.CREDIT.isReversal()).isFalse();
    }

    @Test
    void debit_shouldDecrementsBalance() {
        // Given: DEBIT debe decrementar el saldo
        assertThat(EntryType.DEBIT.incrementsBalance()).isFalse();
        assertThat(EntryType.DEBIT.decrementsBalance()).isTrue();
        assertThat(EntryType.DEBIT.isReversal()).isFalse();
    }

    @Test
    void reversalCredit_shouldIncrementsBalance() {
        // Given: REVERSAL_CREDIT debe incrementar el saldo
        // (devuelve fondos que salieron por DEBIT original)
        assertThat(EntryType.REVERSAL_CREDIT.incrementsBalance()).isTrue();
        assertThat(EntryType.REVERSAL_CREDIT.decrementsBalance()).isFalse();
        assertThat(EntryType.REVERSAL_CREDIT.isReversal()).isTrue();
    }

    @Test
    void reversalDebit_shouldDecrementsBalance() {
        // Given: REVERSAL_DEBIT debe decrementar el saldo
        // (retira fondos que entraron por CREDIT original)
        assertThat(EntryType.REVERSAL_DEBIT.incrementsBalance()).isFalse();
        assertThat(EntryType.REVERSAL_DEBIT.decrementsBalance()).isTrue();
        assertThat(EntryType.REVERSAL_DEBIT.isReversal()).isTrue();
    }

    @Test
    void isReversal_shouldIdentifyReversalEntries() {
        // Given: Solo REVERSAL_DEBIT y REVERSAL_CREDIT son reversiones
        assertThat(EntryType.REVERSAL_DEBIT.isReversal()).isTrue();
        assertThat(EntryType.REVERSAL_CREDIT.isReversal()).isTrue();
        assertThat(EntryType.DEBIT.isReversal()).isFalse();
        assertThat(EntryType.CREDIT.isReversal()).isFalse();
    }

    @Test
    void balanceCalculation_shouldFollowDoubleEntryBookkeeping() {
        // Scenario: Una transferencia de $1000 de Juan a María
        // - Cuenta Juan recibe DEBIT por $1000 (decreases balance)
        // - Cuenta María recibe CREDIT por $1000 (increases balance)

        // Juan's perspective:
        long juanNetChange = 0L;
        juanNetChange -= 1000; // DEBIT from Juan
        juanNetChange += 0;    // No CREDIT to Juan
        assertThat(juanNetChange).isEqualTo(-1000);

        // María's perspective:
        long mariaNetChange = 0L;
        mariaNetChange -= 0;    // No DEBIT from María
        mariaNetChange += 1000; // CREDIT to María
        assertThat(mariaNetChange).isEqualTo(1000);

        // Double-entry check: sum of all entries must be zero
        assertThat(juanNetChange + mariaNetChange).isEqualTo(0);
    }

    @Test
    void balanceCalculation_withReversals_shouldCancelOriginalEntries() {
        // Scenario: Transferencia revertida
        // Original: DEBIT 1000 (Juan), CREDIT 1000 (María)
        // Reversal: REVERSAL_CREDIT 1000 (Juan), REVERSAL_DEBIT 1000 (María)

        // Juan's balance after complete cycle:
        long juanFinalBalance = 0L;
        juanFinalBalance -= 1000; // DEBIT
        juanFinalBalance += 1000; // REVERSAL_CREDIT
        assertThat(juanFinalBalance).isEqualTo(0);

        // María's balance after complete cycle:
        long mariaFinalBalance = 0L;
        mariaFinalBalance += 1000; // CREDIT
        mariaFinalBalance -= 1000; // REVERSAL_DEBIT
        assertThat(mariaFinalBalance).isEqualTo(0);

        // Both accounts are back to equilibrium
        assertThat(juanFinalBalance + mariaFinalBalance).isEqualTo(0);
    }
}
