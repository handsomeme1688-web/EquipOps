package com.zoee.equipops.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
@Component
@Getter
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expire}")
    private long expireSeconds;

    public String generateJwt(Map<String,Object> claim){
        return Jwts.builder()
                .signWith(SignatureAlgorithm.HS256,secret)
                .setClaims(new HashMap<>(claim))
                .setExpiration(new Date(System.currentTimeMillis() + expireSeconds*1000 ))
                .compact();
    }

    public Claims parseJwt(String jwt){
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(jwt)
                .getBody();
    }


}
