package com.finnode.payment.model;

public enum SagaStep {

    /**
     * Paso 1: Evaluación del riesgo de fraude con Spring AI.
     * Si el riskScore supera el umbral configurado, el Saga no arranca.
     */
    FRAUD_CHECK,

    /**
     * Paso 2: Solicitud de reserva de fondos a account-service via Kafka.
     * Se publica PaymentInitiatedEvent y se espera FundsReservedEvent.
     */
    RESERVE_FUNDS,

    /**
     * Paso 3: Solicitud de registro de asientos contables a ledger-service via Kafka.
     * Se publica PaymentCompletedEvent y se espera LedgerEntriesRecordedEvent.
     */
    RECORD_LEDGER,

    /**
     * Paso 4: Confirmación final del pago.
     * Todos los pasos del Saga fueron exitosos.
     */
    CONFIRM_PAYMENT,

    /**
     * Paso de compensación: reversión del Saga.
     * Se activa cuando un paso falla después de haber reservado fondos.
     * Publica PaymentReversedEvent para que los servicios reviertan sus operaciones.
     */
    COMPENSATE
}