package com.mrkirby153.snowsgivingbot.web.services;

import com.mrkirby153.snowsgivingbot.web.DiscordUser;
import com.mrkirby153.snowsgivingbot.web.dto.DiscordOAuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    public static final long JWT_VALIDITY = 5 * 60 * 60;
    @Value("${jwt.secret}")
    private String secret;

    public <T> T getClaimForToken(String token, Function<Claims, T> claimFunction) {
        final Claims claims = getAllClaims(token);
        return claimFunction.apply(claims);
    }

    public Date getExpirationDate(String token) {
        return getClaimForToken(token, Claims::getExpiration);
    }

    private Claims getAllClaims(String token) {
        return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
    }

    public DiscordUser getUser(String token) {
        Claims claims = getAllClaims(token);

        return new DiscordUser(claims.get("username", String.class),
            claims.get("discriminator", String.class), claims.getSubject(),
            claims.get("avatar", String.class));
    }

    public boolean isExpired(String token) {
        Date expiration = getExpirationDate(token);
        return expiration.before(new Date());
    }


    public String generateToken(DiscordOAuthUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", user.getUsername());
        claims.put("discriminator", user.getDiscriminator());
        claims.put("avatar", user.getAvatar());
        return Jwts.builder().setClaims(claims).setSubject(user.getId())
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(new Date(System.currentTimeMillis() + JWT_VALIDITY * 1000)).signWith(
                SignatureAlgorithm.HS512, secret).compact();
    }
}
