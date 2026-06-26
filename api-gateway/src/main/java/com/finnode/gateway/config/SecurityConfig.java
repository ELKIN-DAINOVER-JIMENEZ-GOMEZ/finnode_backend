
package com.finnode.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;
/**
 * Configuración de seguridad del api-gateway.
 *
 * <p
 * <ul>
 *   <li>Marca {@code /api/auth/register} y {@code /api/auth/login} como rutas públicas.
 *       El usuario aún no tiene token en estos endpoints.</li>
 *   <li>Requiere JWT válido en todas las demás rutas.</li>
 *   <li>Configura el {@code ReactiveJwtDecoder} apuntando al {@code issuer-uri} del
 *       {@code auth-service}. Spring descarga automáticamente la clave pública
 *       del endpoint JWKS para verificar la firma del token.</li>
 * </ul>
 *
 * <p><strong>¿Por qué {@code @EnableWebSecurity}?</strong>
 * Este módulo usa el starter {@code spring-cloud-starter-gateway-server-webmvc}, así que
 * corre sobre Servlet/Spring MVC. La configuración de seguridad debe ser servlet y no
 * reactiva; de lo contrario, Spring intentaría cargar clases de WebFlux que no están
 * presentes en el classpath.
 *
 * <p><strong>Sin sesiones HTTP:</strong> cada petición se autentica con el JWT del header
 * {@code Authorization}. No se crea ni mantiene ninguna sesión en el gateway.
 */

/**
 * Configuración de seguridad reactiva del api-gateway.
 *
 * Usa ServerHttpSecurity (WebFlux) en lugar de HttpSecurity (MVC/Servlet)
 * porque el gateway corre sobre Netty reactivo, no Tomcat.
 */

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}