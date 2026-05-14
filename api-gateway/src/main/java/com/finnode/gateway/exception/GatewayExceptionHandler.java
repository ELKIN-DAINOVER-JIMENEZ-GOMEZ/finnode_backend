package com.finnode.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Manejador global de errores del api-gateway.
 *
 * <p>Intercepta todas las excepciones antes de que salgan al cliente
 * y las convierte en respuestas JSON con estructura uniforme:
 *
 * <pre>
 * {
 *   "status": 401,
 *   "error": "Unauthorized",
 *   "message": "Token JWT inválido o expirado",
 *   "timestamp": "2025-01-15T10:30:00Z"
 * }
 * </pre>
 *
 * <p><strong>{@code @Order(-1)}:</strong> prioridad más alta que el manejador
 * por defecto de Spring ({@code @Order(-2) = DefaultErrorWebExceptionHandler}).
 * Sin esto, Spring respondería con su formato de error propio en lugar del
 * JSON estructurado de FinNode.
 *
 * <p><strong>Errores manejados:</strong>
 * <ul>
 *   <li>{@code OAuth2AuthenticationException} → 401 Unauthorized (JWT inválido o expirado)</li>
 *   <li>{@code AccessDeniedException} → 403 Forbidden (ruta protegida sin permisos)</li>
 *   <li>{@code ResponseStatusException 404} → 404 Not Found (ruta no configurada en el gateway)</li>
 *   <li>{@code ResponseStatusException 503} → 503 Service Unavailable (microservicio caído)</li>
 *   <li>Cualquier otra excepción → 500 Internal Server Error</li>
 * </ul>
 */
@Component
@Order(-1)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = resolveStatus(ex);
        String message = resolveMessage(ex);

        return writeErrorResponse(exchange, status, message);
    }

    // ── Resolución de status HTTP según el tipo de excepción ─────────────────

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof OAuth2AuthenticationException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof AccessDeniedException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof ResponseStatusException rse) {
            return HttpStatus.valueOf(rse.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    // ── Resolución del mensaje según el tipo de excepción ────────────────────

    private String resolveMessage(Throwable ex) {
        if (ex instanceof OAuth2AuthenticationException) {
            return "Token JWT inválido o expirado";
        }
        if (ex instanceof AccessDeniedException) {
            return "No tienes permisos para acceder a este recurso";
        }
        if (ex instanceof ResponseStatusException rse) {
            return switch (rse.getStatusCode().value()) {
                case 404 -> "La ruta solicitada no existe en el sistema";
                case 503 -> "El servicio no está disponible en este momento. Intenta más tarde";
                default  -> rse.getReason() != null ? rse.getReason() : "Error en el gateway";
            };
        }
        return "Error interno del servidor";
    }

    // ── Escritura de la respuesta JSON ────────────────────────────────────────

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange,
                                          HttpStatus status,
                                          String message) {
        Map<String, Object> errorBody = Map.of(
                "status",    status.value(),
                "error",     status.getReasonPhrase(),
                "message",   message,
                "timestamp", Instant.now().toString()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorBody);
        } catch (JsonProcessingException e) {
            bytes = "{\"status\":500,\"error\":\"Internal Server Error\"}".getBytes();
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(bytes);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}