package mn.shop.controller;

import mn.shop.model.Branch;
import mn.shop.repository.BranchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// GET нь хүн бүрт нээлттэй, POST/PUT/DELETE нь зөвхөн ADMIN-д (AdminInterceptor)
@RestController
@RequestMapping("/api/branches")
public class BranchController {

    @Autowired
    private BranchRepository branchRepository;

    @GetMapping
    public List<Branch> getAll() {
        return branchRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Branch body) {
        String error = validate(body);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));

        Branch branch = new Branch();
        copy(body, branch);
        return ResponseEntity.ok(branchRepository.save(branch));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Branch body) {
        String error = validate(body);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));

        return branchRepository.findById(id).<ResponseEntity<?>>map(branch -> {
            copy(body, branch);
            return ResponseEntity.ok(branchRepository.save(branch));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!branchRepository.existsById(id)) return ResponseEntity.notFound().build();
        branchRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static void copy(Branch from, Branch to) {
        to.setName(from.getName().trim());
        to.setAddress(from.getAddress().trim());
        String url = from.getImageUrl();
        to.setImageUrl(url == null || url.isBlank() ? null : url.trim());
    }

    private static String validate(Branch b) {
        if (b.getName() == null || b.getName().isBlank()) return "Салбарын нэрээ оруулна уу";
        if (b.getName().trim().length() > 60) return "Нэр 60 тэмдэгтээс ихгүй байна";
        if (b.getAddress() == null || b.getAddress().isBlank()) return "Хаягаа оруулна уу";
        if (b.getAddress().trim().length() > 255) return "Хаяг 255 тэмдэгтээс ихгүй байна";
        String url = b.getImageUrl();
        if (url != null && !url.isBlank()) {
            String u = url.trim().toLowerCase();
            if (!u.startsWith("http://") && !u.startsWith("https://") && !u.startsWith("/images/")
                    && !u.startsWith("/uploads/"))
                return "Зургийн холбоос буруу байна";
            if (u.length() > 500) return "Зургийн холбоос хэт урт байна";
        }
        return null;
    }
}
