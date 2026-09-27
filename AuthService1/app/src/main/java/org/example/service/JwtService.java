package org.example.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.*;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {
    @Value("${jwt.secret}")
    private    String secret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    private SecretKey signKey;

    @PostConstruct
    void initKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.signKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signKey)              // was setSigningKey
                .build()
                .parseSignedClaims(token)          // was parseClaimsJws
                .getPayload();                      // was getBody
    }

    public String createToken(Map<String, Object> claims, String username) {
        return Jwts.builder()
                .claims(claims)                     // was setClaims
                .subject(username)
                .issuer("expense-tracker-auth")
                .issuedAt(new Date())               // was setIssuedAt
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))  // was setExpiration
                .signWith(signKey, Jwts.SIG.HS256)  // was signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }


    public  String extractUsername(String token){
        return extractClaim(token, Claims::getSubject);
    }
    public String extractUserId(String token){
        return extractAllClaims(token).get("userId", String.class);

    }

    public Date extractExpiration(String token){
        return extractClaim(token,Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims=extractAllClaims(token);
        return claimsResolver.apply(claims);
    }



    private Boolean isTokenExpired(String token){
        return extractExpiration(token).before(new Date());
    }


    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired");
            return false;
        } catch (JwtException ex) {
            log.warn("JWT invalid: {}", ex.getMessage());
            return false;
        }
    }


    public String generateToken(String username, String userId, List<String> roles){
        Map<String, Object> claims=new HashMap<>();
        claims.put("userId", userId);
        claims.put("roles", roles);
        return createToken(claims,username);
    }


}
