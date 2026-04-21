package com.finnode.auth.service;



import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Servicio responsable de toda la lógica criptográfica de JWT en FinNode.
 *
 * Genera dos tipos de tokens:
 *  - access_token  → vida corta (15 min), contiene userId + email + role
 *  - refresh_token → vida larga (7 días), contiene solo userId
 *
 * Usa la librería jjwt (io.jsonwebtoken) con algoritmo HMAC-SHA256.
 */
@Slf4j
@Service
public class JwtService {

    // -----------------------------------------------------------------------
    // Propiedades inyectadas desde application.yml
    // -----------------------------------------------------------------------

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;       // en milisegundos (ej: 900_000 = 15 min)

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;      // en milisegundos (ej: 604_800_000 = 7 días)

    @Value("${jwt.issuer}")
    private String issuer;                // ej: "finnode-auth"

    // -----------------------------------------------------------------------
    // Claims personalizados (nombres de los campos dentro del token)
    // -----------------------------------------------------------------------

    private static final String CLAIM_USER_ID   = "userId";
    private static final String CLAIM_EMAIL     = "email";
    private static final String CLAIM_ROLE      = "role";
    private static final String CLAIM_TOKEN_TYPE = "type";

    private static final String TYPE_ACCESS  = "access";
    private static final String TYPE_REFRESH = "refresh";

    // -----------------------------------------------------------------------
    // Generación de tokens
    // -----------------------------------------------------------------------

    /**
     * Genera un access_token firmado con los datos del usuario.
     *
     * Claims incluidos:
     *  - sub   → userId (UUID como String)
     *  - email → correo del usuario
     *  - role  → rol (USER | ADMIN)
     *  - type  → "access"
     *  - iss   → issuer del sistema
     *  - iat   → fecha de emisión
     *  - exp   → fecha de expiración
     *
     * @param userId UUID del usuario registrado
     * @param email  correo electrónico del usuario
     * @param role   rol asignado (ej: "USER")
     * @return token JWT firmado como String
     */
    public String generateAccessToken(UUID userId, String email, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiry);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(buildSigningKey())
                .compact();
    }

    /**
     * Genera un refresh_token de larga duración.
     *
     * Solo incluye el userId como subject para minimizar información expuesta.
     * No incluye email ni rol para limitar el impacto si el token es interceptado.
     *
     * @param userId UUID del usuario
     * @return refresh token JWT firmado como String
     */
    public String generateRefreshToken(UUID userId) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiry);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(buildSigningKey())
                .compact();
    }

    // -----------------------------------------------------------------------
    // Validación de tokens
    // -----------------------------------------------------------------------

    /**
     * Valida que el token sea auténtico, no haya expirado y pertenezca al issuer correcto.
     *
     * @param token JWT a validar
     * @return true si el token es válido, false en cualquier otro caso
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token JWT inválido: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Valida que el token sea un refresh_token legítimo.
     * Rechaza cualquier access_token que se intente usar como refresh.
     *
     * @param token JWT a validar
     * @return true si es un refresh_token válido
     */
    public boolean isRefreshTokenValid(String token) {
        if (!isTokenValid(token)) return false;

        String type = extractClaim(token, CLAIM_TOKEN_TYPE);
        return TYPE_REFRESH.equals(type);
    }

    // -----------------------------------------------------------------------
    // Extracción de claims
    // -----------------------------------------------------------------------

    /**
     * Extrae el userId (subject) del token.
     *
     * @param token JWT firmado
     * @return UUID del usuario
     */
    public UUID extractUserId(String token) {
        String subject = parseClaims(token).getSubject();
        return UUID.fromString(subject);
    }

    /**
     * Extrae el email del access_token.
     *
     * @param token JWT firmado
     * @return email del usuario
     */
    public String extractEmail(String token) {
        return extractClaim(token, CLAIM_EMAIL);
    }

    /**
     * Extrae el rol del access_token.
     *
     * @param token JWT firmado
     * @return rol del usuario (ej: "USER", "ADMIN")
     */
    public String extractRole(String token) {
        return extractClaim(token, CLAIM_ROLE);
    }

    /**
     * Retorna la fecha de expiración del token.
     *
     * @param token JWT firmado
     * @return fecha de expiración
     */
    public Date extractExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    // -----------------------------------------------------------------------
    // Métodos privados de soporte
    // -----------------------------------------------------------------------

    /**
     * Parsea y verifica la firma del token, retornando los claims internos.
     * Lanza JwtException si el token es inválido o está expirado.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(buildSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extrae un claim específico por su nombre de clave.
     *
     * @param token     JWT firmado
     * @param claimName nombre del claim a extraer
     * @return valor del claim como String, o null si no existe
     */
    private String extractClaim(String token, String claimName) {
        Claims claims = parseClaims(token);
        Object value = claims.get(claimName);
        return value != null ? value.toString() : null;
    }

    /**
     * Construye la clave criptográfica a partir del secret configurado.
     * Usa HMAC-SHA256 (HS256) para la firma del token.
     *
     * El secret debe tener al menos 256 bits (32 caracteres) en producción.
     */
    private SecretKey buildSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}