package com.finnode.ledger.model;

/**
 * Tipo de asiento contable dentro del libro mayor.
 *
 * Contabilidad de Partida Doble: por cada transacción se generan
 * exactamente dos asientos con tipos opuestos (DEBIT + CREDIT).
 * Sus montos SIEMPRE deben ser iguales. Si no cuadran, la transacción
 * es rechazada con LedgerImbalanceException.
 *
 * Flujo de una transferencia exitosa (Juan → María, $500.000 COP):
 * ┌────────────────────────────────────────────────────────────┐
 * │  DEBIT  │ account: Juan  │ amount: 500.000 │ Salida de fondos  │
 * │  CREDIT │ account: María │ amount: 500.000 │ Entrada de fondos │
 * └────────────────────────────────────────────────────────────┘
 *
 * Flujo de una reversión Saga (pago fallido, se deshace el movimiento):
 * ┌────────────────────────────────────────────────────────────────────┐
 * │  REVERSAL_CREDIT │ account: Juan  │ amount: 500.000 │ Devuelve fondos   │
 * │  REVERSAL_DEBIT  │ account: María │ amount: 500.000 │ Retira los fondos │
 * └────────────────────────────────────────────────────────────────────┘
 *
 * NOTA: Los asientos originales (DEBIT/CREDIT) NUNCA se eliminan.
 * Los asientos de reversión los neutralizan matemáticamente.
 * El saldo neto de ambas cuentas vuelve a cero después de una reversión completa.
 */
public enum EntryType {

    /**
     * Salida de fondos de la cuenta.
     * Reduce el saldo disponible. Se crea en la cuenta ORIGEN de una transferencia.
     *
     * Efecto en el saldo: balance = balance - amount
     */
    DEBIT,

    /**
     * Entrada de fondos a la cuenta.
     * Incrementa el saldo disponible. Se crea en la cuenta DESTINO de una transferencia.
     *
     * Efecto en el saldo: balance = balance + amount
     */
    CREDIT,

    /**
     * Reversión de un DEBIT previo.
     * Devuelve los fondos que habían salido de la cuenta origen.
     * Se crea cuando el Patrón Saga revierte una transferencia fallida.
     *
     * Efecto en el saldo: balance = balance + amount  (cancela el DEBIT original)
     */
    REVERSAL_CREDIT,

    /**
     * Reversión de un CREDIT previo.
     * Retira los fondos que habían entrado a la cuenta destino.
     * Se crea cuando el Patrón Saga revierte una transferencia fallida.
     *
     * Efecto en el saldo: balance = balance - amount  (cancela el CREDIT original)
     */
    REVERSAL_DEBIT;

    /**
     * Indica si este tipo de asiento INCREMENTA el saldo de la cuenta.
     * Útil para BalanceCalculatorService al calcular el saldo por Event Sourcing.
     *
     * CREDIT y REVERSAL_CREDIT suman al saldo.
     * DEBIT  y REVERSAL_DEBIT  restan del saldo.
     *
     * @return true si el asiento incrementa el saldo
     */
    public boolean incrementsBalance() {
        return this == CREDIT || this == REVERSAL_CREDIT;
    }

    /**
     * Indica si este tipo de asiento REDUCE el saldo de la cuenta.
     *
     * @return true si el asiento reduce el saldo
     */
    public boolean decrementsBalance() {
        return this == DEBIT || this == REVERSAL_DEBIT;
    }

    /**
     * Indica si este asiento es una reversión (compensación Saga).
     *
     * @return true si es un asiento de reversión
     */
    public boolean isReversal() {
        return this == REVERSAL_DEBIT || this == REVERSAL_CREDIT;
    }
}