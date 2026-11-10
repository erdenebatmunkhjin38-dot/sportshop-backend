package mn.shop.service;

import mn.shop.model.PasswordResetToken;
import mn.shop.model.User;
import mn.shop.repository.PasswordResetTokenRepository;
import mn.shop.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Нууц үг сэргээх: и-мэйлээр 30 минут хүчинтэй, нэг удаагийн холбоос илгээнэ.
 * И-мэйл тохируулаагүй (spring.mail.username хоосон) үед холбоосыг зөвхөн серверийн log-д бичнэ (хөгжүүлэлтэд).
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TTL = Duration.ofMinutes(30);
    // Нэг хэрэглэгчид 2 минутад нэгээс олон и-мэйл илгээхгүй (spam хамгаалалт)
    private static final Duration COOLDOWN = Duration.ofMinutes(2);

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                ObjectProvider<JavaMailSender> senders,
                                @Value("${spring.mail.username:}") String from,
                                @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.users = users;
        this.tokens = tokens;
        this.senders = senders;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /** Хэрэглэгч байгаа эсэхийг задруулахгүйн тулд дуудсан тал үргэлж ижил хариу өгнө. */
    @Transactional
    public void requestReset(String email) {
        Optional<User> opt = users.findByUsername(email.trim());
        if (opt.isEmpty()) return;
        User user = opt.get();

        Optional<PasswordResetToken> last = tokens.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (last.isPresent() && last.get().getCreatedAt().plus(COOLDOWN).isAfter(Instant.now())) return;

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tokens.deleteByUserId(user.getId());
        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(user.getId());
        t.setTokenHash(sha256(token));
        t.setCreatedAt(Instant.now());
        t.setExpiresAt(Instant.now().plus(TTL));
        tokens.save(t);

        send(user, frontendUrl + "/reset-password?token=" + token);
    }

    /** Амжилттай бол null, үгүй бол алдааны мессеж. */
    @Transactional
    public String reset(String token, String newPassword) {
        if (token == null || token.isBlank()) return "Холбоос буруу байна";
        Optional<PasswordResetToken> opt = tokens.findByTokenHash(sha256(token.trim()));
        if (opt.isEmpty() || opt.get().isUsed() || opt.get().getExpiresAt().isBefore(Instant.now())) {
            return "Холбоосын хугацаа дууссан эсвэл ашиглагдсан байна. Дахин хүсэлт илгээнэ үү";
        }
        PasswordResetToken t = opt.get();
        Optional<User> user = users.findById(t.getUserId());
        if (user.isEmpty()) return "Хэрэглэгч олдсонгүй";

        user.get().setPassword(encoder.encode(newPassword));
        users.save(user.get());
        tokens.deleteByUserId(t.getUserId()); // бүх хуучин холбоосыг хүчингүй болгоно
        return null;
    }

    private void send(User user, String link) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || from.isBlank()) {
            log.warn("И-мэйл тохируулаагүй тул нууц үг сэргээх холбоосыг илгээсэнгүй ({}): {}", user.getUsername(), link);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getUsername());
        message.setSubject("Нууц үг сэргээх");
        message.setText("Сайн байна уу" + (user.getFirstName() != null ? ", " + user.getFirstName() : "") + ".\n\n"
                + "Та нууц үгээ сэргээх хүсэлт илгээлээ. Доорх холбоосоор орж шинэ нууц үгээ оруулна уу:\n\n"
                + link + "\n\n"
                + "Холбоос 30 минутын дараа хүчингүй болно. Хэрэв та энэ хүсэлтийг илгээгээгүй бол энэ и-мэйлийг үл тоомсорлоно уу.\n\n"
                + "SportShop");
        CompletableFuture.runAsync(() -> {
            try {
                sender.send(message);
            } catch (RuntimeException e) {
                log.error("Нууц үг сэргээх и-мэйл илгээж чадсангүй: {}", user.getUsername(), e);
            }
        });
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
