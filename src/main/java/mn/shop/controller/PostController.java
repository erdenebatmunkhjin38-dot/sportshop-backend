package mn.shop.controller;

import mn.shop.model.Post;
import mn.shop.repository.PostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// GET нь хүн бүрт нээлттэй, POST/PUT/DELETE нь зөвхөн ADMIN-д (AdminInterceptor)
@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private PostRepository postRepository;

    @GetMapping
    public List<Post> getAll() {
        return postRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Post> getById(@PathVariable Long id) {
        return postRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Post body) {
        String error = validate(body);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));

        Post post = new Post();
        copy(body, post);
        return ResponseEntity.ok(postRepository.save(post));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Post body) {
        String error = validate(body);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));

        return postRepository.findById(id).<ResponseEntity<?>>map(post -> {
            copy(body, post);
            return ResponseEntity.ok(postRepository.save(post));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!postRepository.existsById(id)) return ResponseEntity.notFound().build();
        postRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static void copy(Post from, Post to) {
        to.setTitle(from.getTitle().trim());
        to.setTag(blankToNull(from.getTag()));
        to.setSummary(blankToNull(from.getSummary()));
        to.setContent(blankToNull(from.getContent()));
        to.setImageUrl(blankToNull(from.getImageUrl()));
    }

    private static String validate(Post p) {
        if (p.getTitle() == null || p.getTitle().isBlank()) return "Гарчаа оруулна уу";
        if (p.getTitle().trim().length() > 120) return "Гарчийг 120 тэмдэгтээс ихгүй бичнэ үү";
        String url = p.getImageUrl();
        if (url != null && !url.isBlank()) {
            String u = url.trim().toLowerCase();
            if (!u.startsWith("http://") && !u.startsWith("https://"))
                return "Зургийн холбоос http:// эсвэл https://-ээр эхлэх ёстой";
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}