package com.finnode.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO de entrada para el endpoint POST /payments/transfer.
 *
 * <p>Representa la intención de transferencia del usuario. El {@code userId}
 * NO viene en este objeto; se extrae del JWT en el controller para evitar
 * que el cliente lo manipule.
 *
 * <p>Validado con {@code @Valid} en {@code PaymentController}.
 */
public record TransferRequest(

        @NotBlank(message = "La cuenta de origen es obligatoria")
        String sourceAccountId,

        @NotBlank(message = "La cuenta de destino es obligatoria")
        String destinationAccountId,

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor a cero")
        BigDecimal amount,

        @NotBlank(message = "La moneda es obligatoria")
        @Size(min = 3, max = 3, message = "La moneda debe ser un código ISO 4217 de 3 caracteres (COP, USD, EUR)")
        String currency,

        @Size(max = 255, message = "La descripción no puede superar los 255 caracteres")
        String description

) {}