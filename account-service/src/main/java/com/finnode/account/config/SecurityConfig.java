package com.finnode.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configura este servicio como un OAuth2 Resource Server.
 *
 * - Todas las rutas requieren un JWT válido (emitido por auth-service).
 * - CSRF deshabilitado: API REST stateless, no usa sesiones ni cookies.
 * - Sesión stateless: el servidor no guarda estado de sesión.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Cadena de filtros de seguridad principal.
     *
     * El JWT es validado automáticamente por Spring Security usando el
     * issuer-uri configurado en application.yml. Si el token es inválido
     * o está ausente, la petición es rechazada con HTTP 401.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Deshabilitar CSRF: no aplica en APIs REST stateless
                .csrf(AbstractHttpConfigurer::disable)

                // Política de sesión: STATELESS (sin HttpSession)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Todas las rutas requieren autenticación con JWT válido
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().authenticated()
                )

                // Configurar como Resource Server que valida JWT
                // El issuer-uri en application.yml apunta al auth-service
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> {}) // Configuración mínima; issuer-uri viene de application.yml
                );

        return http.build();
    }
}