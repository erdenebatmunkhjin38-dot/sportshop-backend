package mn.shop.controller;

import mn.shop.model.User;
import mn.shop.repository.UserRepository;
import mn.shop.repository.UserRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import mn.shop.security.JwtService;
import mn.shop.security.LoginRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    record LoginBody(String email, String password) {}
    record RegisterBody(String name, String email, String password) {}

    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final JwtService jwt;
    private final LoginRateLimiter rateLimiter;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthApiController(UserRepository users, UserRoleRepository userRoles, JwtService jwt,
                             LoginRateLimiter rateLimiter) {
        this.users = users;
        this.userRoles = userRoles;
        this.jwt = jwt;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterBody body) {
        if (blank(body.name()) || blank(body.email()) || blank(body.password())
                || body.password().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Мэдээлэл дутуу байна"));
        }
        String email = body.email().trim();
        if (users.findByUsername(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Энэ и-мэйл бүртгэлтэй байна"));
        }

        User u = new User();
        u.setUsername(email);
        u.setFirstName(body.name().trim());
        u.setPassword(encoder.encode(body.password()));
        u.setLevel(1);
        u = users.save(u);

        // Бүртгүүлсэн хүн үргэлж USER. ADMIN-ыг зөвхөн DB дээрээс гараар олгоно.
        userRoles.assign(u.getId(), "USER");

        return ResponseEntity.ok(session(u));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginBody body, HttpServletRequest req) {
        // Хүсэлт илгээсэн компьютерын IP хаяг
        String ip = req.getRemoteAddr();
        // 15 минутад 5-аас олон удаа буруу оролдсон бол түр хаана (429 алдаа)
        if (rateLimiter.isBlocked(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Хэт олон удаа буруу оролдлоо. 15 минутын дараа дахин оролдоно уу"));
        }
        if (blank(body.email()) || blank(body.password())) return unauthorized();

        Optional<User> opt = users.findByUsername(body.email().trim());
        if (opt.isEmpty() || !passwordMatches(opt.get(), body.password())) {
            rateLimiter.recordFailure(ip); // буруу оролдлогыг тоолно
            return unauthorized();
        }
        rateLimiter.reset(ip); // амжилттай нэвтэрсэн тул тоолуурыг тэглэнэ
        return ResponseEntity.ok(session(opt.get()));
    }

    private Map<String, Object> session(User u) {
        String role = resolveRole(u);
        return Map.of(
                "token", jwt.create(u, role),
                "name", u.getFirstName() != null ? u.getFirstName() : u.getUsername(),
                "email", u.getUsername(),
                "role", role
        );
    }

    private String resolveRole(User u) {
        boolean admin = userRoles.findRoleNames(u.getId()).stream()
                .map(r -> r.toUpperCase().replaceFirst("^ROLE_", ""))
                .anyMatch("ADMIN"::equals);
        return admin ? "ADMIN" : "USER";
    }

    /** BCrypt шалгана. Хуучин plaintext нууц үг таарвал нэвтрэх үед hash болгож шинэчилнэ. */
    private boolean passwordMatches(User u, String raw) {
        String stored = u.getPassword();
        if (stored == null) return false;
        if (stored.startsWith("$2")) return encoder.matches(raw, stored);
        if (stored.equals(raw)) {
            u.setPassword(encoder.encode(raw));
            users.save(u);
            return true;
        }
        return false;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "И-мэйл эсвэл нууц үг буруу байна"));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}