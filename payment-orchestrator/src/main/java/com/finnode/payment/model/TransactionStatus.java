package com.finnode.payment.model;

public enum TransactionStatus {

    /**
     * La transacción fue recibida y está siendo evaluada por el motor de fraude.
     * Estado inicial al crear la entidad.
     */
    PENDING,

    /**
     * El motor de Spring AI rechazó la transacción por riesgo de fraude.
     * Estado terminal — no se inicia el Saga.
     */
    FRAUD_REJECTED,

    /**
     * account-service confirmó la reserva de fondos exitosamente.
     * El Saga avanza al paso de registro contable.
     */
    FUNDS_RESERVED,

    /**
     * ledger-service confirmó el registro de los asientos DEBIT/CREDIT.
     * El Saga está a punto de completarse.
     */
    LEDGER_RECORDED,

    /**
     * El pago se completó exitosamente en todos los pasos del Saga.
     * Estado terminal exitoso.
     */
    COMPLETED,

    /**
     * El Saga falló en algún paso sin haber reservado fondos
     * (ej: saldo insuficiente). No requiere compensación.
     * Estado terminal con error.
     */
    FAILED,

    /**
     * El Saga fue revertido después de haber reservado fondos.
     * Los eventos de compensación ya fueron publicados y ejecutados.
     * Estado terminal con reversión.
     */
    REVERSED
}