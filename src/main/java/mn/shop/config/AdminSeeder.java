package mn.shop.config;

import mn.shop.model.User;
import mn.shop.repository.UserRepository;
import mn.shop.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Сервер асах бүрт app.admin.email хэрэглэгчийг ADMIN болгоно.
 * - Хэрэглэгч байхгүй бол app.admin.password нууц үгтэйгээр үүсгэнэ
 * - Нууц үг нь тохиргоотой таарахгүй бол тохиргооны утгаар шинэчилнэ
 * - ADMIN эрхгүй бол олгоно
 * Тохиргоо хоосон бол юу ч хийхгүй.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final String email;
    private final String password;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminSeeder(UserRepository users, UserRoleRepository userRoles,
                       @Value("${app.admin.email:}") String email,
                       @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.userRoles = userRoles;
        this.email = email.trim();
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isEmpty()) return;
        if (password.length() < 6) {
            log.warn("app.admin.password хамгийн багадаа 6 тэмдэгт байх ёстой. Админ үүсгэсэнгүй.");
            return;
        }

        User admin = users.findByUsername(email).orElseGet(() -> {
            User u = new User();
            u.setUsername(email);
            u.setFirstName("Админ");
            u.setLevel(1);
            log.info("Админ хэрэглэгч үүсгэлээ: {}", email);
            return u;
        });

        // Нууц үг тохиргоотой таарахгүй бол (эсвэл шинэ хэрэглэгч бол) шинэчилнэ
        String stored = admin.getPassword();
        if (stored == null || !stored.startsWith("$2") || !encoder.matches(password, stored)) {
            admin.setPassword(encoder.encode(password));
        }
        admin = users.save(admin);

        // ADMIN эрх байхгүй бол олгоно (давхар нэмэхгүй)
        boolean isAdmin = userRoles.findRoleNames(admin.getId()).stream()
                .anyMatch(r -> r.equalsIgnoreCase("ADMIN"));
        if (!isAdmin) {
            userRoles.assign(admin.getId(), "ADMIN");
            log.info("{} хэрэглэгчид ADMIN эрх олголоо", email);
        }
    }
}
