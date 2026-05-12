package com.ptt.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtils {

    /** Default JWT token validity: 15 minutes in milliseconds. */
    private static final long DEFAULT_JWT_EXPIRATION_MS = 1000L * 60 * 15;

    // JWT signing secret — MUST be injected from environment variables or external config.
    // Never hard-code secrets in source code (CWE-798 remediation).
    // No inline default is provided; the value MUST come from the property file or
    // the JWT_SECRET environment variable.  The @PostConstruct validator below
    // enforces this at startup (CWE-798, issue #91588).
    @Value("${jwt.secret}")
    private String jwtSecret;

    // JWT expiration time in milliseconds (configurable, defaults to 15 minutes)
    @Value("${jwt.expiration-ms:900000}")
    private long jwtExpirationMs;

    @jakarta.annotation.PostConstruct
    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not configured! Set jwt.secret property or JWT_SECRET environment variable.");
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public String generateToken(String userName) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userName);
    }

    private String createToken(Map<String, Object> claims, String userName) {
        long now = System.currentTimeMillis();
        long expirationTime = jwtExpirationMs > 0 ? jwtExpirationMs : DEFAULT_JWT_EXPIRATION_MS;
        return Jwts.builder()
                .claims(claims)
                .subject(userName)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationTime))
                .signWith(getSignKey())
                .compact();
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
