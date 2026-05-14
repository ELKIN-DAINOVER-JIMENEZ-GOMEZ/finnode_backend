package com.finnode.payment.client;

import com.finnode.payment.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cliente OpenFeign para comunicación síncrona con el account-service.
 *
 * <p><strong>Responsabilidad única:</strong> validaciones previas al inicio del Saga.
 * Permite verificar que las cuentas de origen y destino existen y están activas
 * antes de consultar el motor de fraude o mover ningún fondo.
 *
 * <p><strong>¿Por qué solo lectura?</strong>
 * Toda operación que modifica saldos (reservar, liberar fondos) se realiza
 * de forma asíncrona a través de Kafka. Las llamadas síncronas vía Feign
 * se limitan a consultas de validación para no generar acoplamiento temporal
 * en el camino crítico del Saga.
 *
 * <p><strong>Configuración:</strong> Ver {@link FeignConfig} para el interceptor JWT,
 * timeouts (connectTimeout: 3s, readTimeout: 5s) y política de reintentos.
 *
 * <p>La URL base se resuelve desde {@code feign.client.config.account-service}
 * en {@code application.yml}.
 */
@FeignClient(
        name          = "account-service",
        url           = "${feign.client.config.account-service.url}",
        configuration = FeignConfig.class
)
public interface AccountServiceClient {

    /**
     * Verifica que una cuenta existe y está en estado ACTIVE antes de iniciar
     * el flujo de transferencia.
     *
     * <p>Llamado por {@code PaymentService.initiateTransfer()} para las cuentas
     * de origen y destino. Si cualquiera retorna 404 o está en estado FROZEN/CLOSED,
     * el pago se rechaza con HTTP 422 antes de arrancar el Saga.
     *
     * @param accountId identificador único de la cuenta a verificar
     * @return {@code true} si la cuenta existe y está activa, {@code false} en otro caso
     */
    @GetMapping("/api/accounts/{accountId}/active")
    boolean isAccountActive(@PathVariable("accountId") String accountId);
}