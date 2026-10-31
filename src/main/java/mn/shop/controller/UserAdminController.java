package mn.shop.controller;

import mn.shop.model.User;
import mn.shop.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// /api/users/** нь AdminInterceptor-оор зөвхөн ADMIN-д нээлттэй
@RestController
@RequestMapping("/api/users")
public class UserAdminController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserAdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<User> getAll() {
        return userRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isEmpty()) return notFound(id);
        return ResponseEntity.ok(user.get());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody User updated) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) return notFound(id);

        User user = opt.get();
        if (updated.getUsername() != null)  user.setUsername(updated.getUsername());
        if (updated.getPassword() != null && !updated.getPassword().isBlank()) {
            user.setPassword(encoder.encode(updated.getPassword()));
        }
        if (updated.getFirstName() != null) user.setFirstName(updated.getFirstName());
        if (updated.getLastName() != null)  user.setLastName(updated.getLastName());
        if (updated.getAge() != null)       user.setAge(updated.getAge());
        if (updated.getLevel() != null)     user.setLevel(updated.getLevel());

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Амжилттай шинэчлэгдлээ!"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!userRepository.existsById(id)) return notFound(id);
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Амжилттай устгагдлаа!"));
    }

    private ResponseEntity<?> notFound(Long id) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "ID=" + id + " хэрэглэгч олдсонгүй!"));
    }
}