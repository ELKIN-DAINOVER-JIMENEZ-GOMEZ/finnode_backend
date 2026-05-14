package com.finnode.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración programática de rutas del api-gateway.
 *
 * <p>Define las rutas del gateway de forma reactiva (WebFlux) usando Java,
 * permitiendo añadir filtros por ruta de forma expresiva (headers personalizados,
 * reescritura de paths, circuit breakers, rate limiting) sin sobrecargar el YAML.
 *
 * <p><strong>¿Por qué en código y no solo en YAML?</strong>
 * La configuración YAML es suficiente para enrutamiento simple. Esta clase
 * existe para escalar: cuando se necesite añadir un filtro específico por ruta
 * (ej: rate limiting solo en {@code /api/payments/**}), el código Java es más
 * legible y testeable que el YAML equivalente.
 *
 * <p><strong>Rutas definidas:</strong>
 * <pre>
 * /api/auth/register    → auth-service      :8081  [sin JWT]
 * /api/auth/login       → auth-service      :8081  [sin JWT]
 * /api/auth/**          → auth-service      :8081  [JWT requerido]
 * /api/accounts/**      → account-service   :8082  [JWT requerido]
 * /api/payments/**      → payment-orchestrator :8083 [JWT requerido]
 * /api/ledger/**        → ledger-service    :8084  [JWT requerido]
 * </pre>
 *
 * <p>La validación del JWT ocurre en {@link SecurityConfig} antes de que
 * la petición llegue al filtro de enrutamiento. Esta clase solo define
 * hacia dónde enrutar; no decide si la petición está autenticada.
 */
@Configuration
public class GatewayRoutesConfig {

    @Value("${services.auth-service.url}")
    private String authServiceUrl;

    @Value("${services.account-service.url}")
    private String accountServiceUrl;

    @Value("${services.payment-orchestrator.url}")
    private String paymentOrchestratorUrl;

    @Value("${services.ledger-service.url}")
    private String ledgerServiceUrl;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-register", r -> r
                        .path("/api/auth/register")
                        .uri(authServiceUrl))
                .route("auth-login", r -> r
                        .path("/api/auth/login")
                        .uri(authServiceUrl))
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .uri(authServiceUrl))
                .route("account-service", r -> r
                        .path("/api/accounts/**")
                        .uri(accountServiceUrl))
                .route("payment-orchestrator", r -> r
                        .path("/api/payments/**")
                        .uri(paymentOrchestratorUrl))
                .route("ledger-service", r -> r
                        .path("/api/ledger/**")
                        .uri(ledgerServiceUrl))
                .build();
    }
}