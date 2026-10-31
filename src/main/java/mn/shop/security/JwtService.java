package mn.shop.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import mn.shop.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret:}") String secret) {
        if (secret.isBlank()) {
            // Secret тохируулаагүй бол санамсаргүй түлхүүр үүсгэнэ. Код дотор бичигдсэн
            // түлхүүрээр хэн нэгэн хуурамч ADMIN токен үүсгэхээс сэргийлнэ.
            log.warn("JWT_SECRET тохируулаагүй тул санамсаргүй түлхүүр ашиглалаа. "
                    + "Сервер дахин асахад бүх токен хүчингүй болно.");
            this.key = Jwts.SIG.HS256.key().build();
        } else if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET хамгийн багадаа 32 тэмдэгт байх ёстой");
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String create(User user, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + WEEK_MS))
                .signWith(key)
                .compact();
    }

    /** Буруу эсвэл хугацаа дууссан бол null */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}