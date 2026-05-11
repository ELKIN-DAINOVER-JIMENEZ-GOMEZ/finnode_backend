package com.finnode.ledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad del ledger-service como OAuth2 Resource Server.
 *
 * Este servicio NO emite tokens JWT — eso es responsabilidad exclusiva del
 * auth-service. Este servicio VALIDA los tokens que llegan en cada petición
 * HTTP usando la clave pública del auth-service (configurada en application.yml).
 *
 * POLÍTICA DE ACCESO:
 * Todos los endpoints de consulta requieren un JWT válido.
 * No existen endpoints públicos en este servicio — el libro mayor
 * es información financiera sensible que nunca debe ser accesible sin
 * autenticación.
 *
 * SESIONES:
 * Se usa STATELESS porque JWT es el mecanismo de autenticación.
 * El servidor no guarda ningún estado de sesión entre peticiones.
 * Cada request lleva su propio token y se valida de forma independiente.
 *
 * CSRF:
 * Se deshabilita porque este es un API REST stateless.
 * La protección CSRF solo es necesaria en aplicaciones con sesiones
 * y formularios HTML — no aplica a APIs que usan JWT.
 *
 * NOTA IMPORTANTE SOBRE LA ARQUITECTURA:
 * En producción, el api-gateway ya validó el JWT antes de enrutar
 * la petición a este servicio. La validación aquí es una segunda capa
 * de defensa (defense in depth) en caso de que alguien acceda
 * directamente al puerto 8084 sin pasar por el gateway.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Define la cadena de filtros de seguridad HTTP.
     *
     * @param http builder de configuración de Spring Security
     * @return SecurityFilterChain configurado como Resource Server JWT
     * @throws Exception si la configuración falla al construirse
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // ----------------------------------------------------------------
                // CSRF deshabilitado — API REST stateless con JWT
                // ----------------------------------------------------------------
                .csrf(AbstractHttpConfigurer::disable)

                // ----------------------------------------------------------------
                // SESIONES — completamente stateless
                // El servidor nunca crea ni consulta sesiones HTTP
                // ----------------------------------------------------------------
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ----------------------------------------------------------------
                // REGLAS DE AUTORIZACIÓN
                // ----------------------------------------------------------------
                .authorizeHttpRequests(auth -> auth

                        // Endpoints de consulta del libro mayor — requieren JWT válido
                        // Solo lectura: GET es el único método HTTP permitido
                        .requestMatchers(HttpMethod.GET, "/ledger/**").authenticated()

                        // Cualquier otra ruta o método HTTP no contemplado → denegado
                        // Esto incluye POST, PUT, DELETE, PATCH sobre /ledger/**
                        // Los asientos solo se crean via Kafka, nunca via HTTP
                        .anyRequest().denyAll()
                )

                // ----------------------------------------------------------------
                // RESOURCE SERVER JWT
                // El issuer-uri configurado en application.yml apunta al auth-service.
                // Spring Security descarga automáticamente la clave pública del
                // auth-service para validar la firma de cada token entrante.
                // ----------------------------------------------------------------
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> {})
                );

        return http.build();
    }
}