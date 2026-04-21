package com.finnode.account.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento consumido desde el topic [user.registered] publicado por auth-service.
 * Al recibirlo, account-service crea automáticamente una cuenta bancaria
 * con saldo 0.0000 y estado ACTIVE para el nuevo usuario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {

    /** Identificador único del usuario recién registrado en auth-service. */
    private UUID userId;

    /** Email del usuario — usado como referencia informativa. */
    private String email;

    /** Nombre completo del usuario. */
    private String fullName;

    /** Momento exacto en que el registro fue procesado por auth-service. */
    private Instant timestamp;
}