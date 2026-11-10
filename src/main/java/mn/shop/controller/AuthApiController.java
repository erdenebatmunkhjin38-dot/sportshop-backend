package mn.shop.controller;

import mn.shop.model.User;
import mn.shop.repository.UserRepository;
import mn.shop.repository.UserRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import mn.shop.security.JwtService;
import mn.shop.security.LoginRateLimiter;
import mn.shop.service.PasswordResetService;
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
    record ForgotBody(String email) {}
    record ResetBody(String token, String password) {}

    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final JwtService jwt;
    private final LoginRateLimiter rateLimiter;
    private final PasswordResetService passwordReset;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthApiController(UserRepository users, UserRoleRepository userRoles, JwtService jwt,
                             LoginRateLimiter rateLimiter, PasswordResetService passwordReset) {
        this.users = users;
        this.userRoles = userRoles;
        this.jwt = jwt;
        this.rateLimiter = rateLimiter;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterBody body) {
        if (blank(body.name()) || blank(body.email()) || blank(body.password())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Мэдээлэл дутуу байна"));
        }
        if (!strongPassword(body.password())) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "Нууц үг шаардлага хангахгүй байна: 6+ тэмдэгттэй, үсэг, тоо, тусгай тэмдэгт (!@#$ гэх мэт) заавал орсон байх ёстой"));
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
        if (blank(body.email()) || blank(body.password())) return unauthorized();
        // Хэрэглэгчийн IP болон и-мэйлээр тус тусад нь тоолно. Зөвхөн IP-ээр тоолвол бүх хүсэлт
        // Next.js серверээр дамждаг тул нэг хүний буруу оролдлого бүх хэрэглэгчийг хаадаг байсан.
        String ipKey = "ip:" + clientIp(req);
        String emailKey = "email:" + body.email().trim().toLowerCase();
        // 15 минутад 5-аас олон удаа буруу оролдсон бол түр хаана (429 алдаа)
        if (rateLimiter.isBlocked(ipKey) || rateLimiter.isBlocked(emailKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Хэт олон удаа буруу оролдлоо. 15 минутын дараа дахин оролдоно уу"));
        }

        Optional<User> opt = users.findByUsername(body.email().trim());
        if (opt.isEmpty() || !passwordMatches(opt.get(), body.password())) {
            rateLimiter.recordFailure(ipKey); // буруу оролдлогыг тоолно
            rateLimiter.recordFailure(emailKey);
            return unauthorized();
        }
        rateLimiter.reset(ipKey); // амжилттай нэвтэрсэн тул тоолуурыг тэглэнэ
        rateLimiter.reset(emailKey);
        return ResponseEntity.ok(session(opt.get()));
    }

    // Нууц үг сэргээх холбоос илгээх. Бүртгэлтэй эсэхээс үл хамааран ижил хариу өгнө.
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotBody body, HttpServletRequest req) {
        String ipKey = "forgot:" + clientIp(req);
        if (rateLimiter.isBlocked(ipKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Хэт олон хүсэлт илгээлээ. 15 минутын дараа дахин оролдоно уу"));
        }
        rateLimiter.recordFailure(ipKey); // IP тус бүрээс 15 минутад 5 хүсэлт
        if (!blank(body.email())) passwordReset.requestReset(body.email());
        return ResponseEntity.ok(Map.of("message",
                "Хэрэв энэ и-мэйл бүртгэлтэй бол нууц үг сэргээх холбоос илгээлээ. И-мэйлээ шалгана уу"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetBody body) {
        if (blank(body.password()) || !strongPassword(body.password())) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "Нууц үг шаардлага хангахгүй байна: 6+ тэмдэгттэй, үсэг, тоо, тусгай тэмдэгт (!@#$ гэх мэт) заавал орсон байх ёстой"));
        }
        String error = passwordReset.reset(body.token(), body.password());
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));
        return ResponseEntity.ok(Map.of("message", "Нууц үг солигдлоо. Шинэ нууц үгээрээ нэвтэрнэ үү"));
    }

    /**
     * Жинхэнэ хэрэглэгчийн IP. Хүсэлт Next.js серверээс (ижил машин) ирсэн бол Next-ийн
     * дамжуулсан X-Client-IP толгойг итгэнэ. Гаднаас шууд ирсэн бол толгойг үл тоомсорлоно.
     */
    private static String clientIp(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        boolean fromProxy = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        String forwarded = req.getHeader("X-Client-IP");
        if (fromProxy && forwarded != null && !forwarded.isBlank() && forwarded.length() <= 64) {
            return forwarded.trim();
        }
        return remote;
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

    /** 6+ тэмдэгт, дор хаяж нэг үсэг, нэг тоо, нэг тусгай тэмдэгт */
    private static boolean strongPassword(String pw) {
        return pw.length() >= 6
                && pw.chars().anyMatch(Character::isLetter)
                && pw.chars().anyMatch(Character::isDigit)
                && pw.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}