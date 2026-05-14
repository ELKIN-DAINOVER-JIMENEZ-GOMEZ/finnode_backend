package com.finnode.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Sin estado: el gateway no guarda sesiones
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(exchanges -> exchanges
                        // Rutas públicas: el usuario aún no tiene token
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()

                        // Todo lo demás requiere JWT válido
                        .anyRequest().authenticated()
                )
                // Resource Server servlet: valida JWT en cada petición
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))

                .build();
    }
}