package com.assessment.dms.security;

import com.assessment.dms.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;


@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration tokenValidity;

    public JwtService(AppProperties appProperties) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(appProperties.jwt().secret()));
        this.tokenValidity = Duration.ofMinutes(appProperties.jwt().expirationMinutes());
    }

    public String generateToken(UserPrincipal user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenValidity)))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public long getTokenValiditySeconds() {
        return tokenValidity.toSeconds();
    }
}
