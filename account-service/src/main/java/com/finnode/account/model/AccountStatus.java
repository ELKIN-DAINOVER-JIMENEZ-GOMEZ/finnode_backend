package com.finnode.account.model;

/**
 * Estados posibles de una cuenta bancaria en FinNode.
 *
 * Se persiste como VARCHAR con @Enumerated(EnumType.STRING) para
 * que la BD sea legible e inspeccionable ("ACTIVE" en lugar de 0).
 */
public enum AccountStatus {

    /**
     * Cuenta operativa. Puede enviar, recibir y reservar fondos.
     * Estado inicial al crearse desde UserRegisteredEvent.
     */
    ACTIVE,

    /**
     * Bloqueada temporalmente por administrador o sistema.
     * No puede iniciar transferencias ni reservar fondos.
     * Reversible: puede volver a ACTIVE tras revisión.
     *
     * Casos: sospecha de fraude, verificación de identidad pendiente.
     */
    SUSPENDED,

    /**
     * Cerrada definitivamente. Estado terminal: no reversible.
     * No puede realizar ni recibir ninguna operación financiera.
     *
     * Casos: cierre voluntario, cierre regulatorio, inactividad prolongada.
     */
    CLOSED
}