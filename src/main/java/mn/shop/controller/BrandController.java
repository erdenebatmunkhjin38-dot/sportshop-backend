package mn.shop.controller;

import mn.shop.model.Brand;
import mn.shop.repository.BrandRepository;
import mn.shop.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// GET нь хүн бүрт нээлттэй, POST/PUT/DELETE нь зөвхөн ADMIN-д (AdminInterceptor)
@RestController
@RequestMapping("/api/brands")
public class BrandController {

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @GetMapping
    public List<Brand> getAll() {
        return brandRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Brand body) {
        String error = validate(body);
        if (error == null && brandRepository.existsByNameIgnoreCase(body.getName().trim())) {
            error = "Ийм нэртэй брэнд бүртгэлтэй байна";
        }
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));

        Brand brand = new Brand();
        copy(body, brand);
        return ResponseEntity.ok(brandRepository.save(brand));
    }

    // Нэр солигдвол тухайн брэндийн бараанууд дагаж шинэчлэгдэнэ
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Brand body) {
        String error = validate(body);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));

        return brandRepository.findById(id).<ResponseEntity<?>>map(brand -> {
            String oldName = brand.getName();
            String newName = body.getName().trim();
            if (!oldName.equalsIgnoreCase(newName) && brandRepository.existsByNameIgnoreCase(newName)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Ийм нэртэй брэнд бүртгэлтэй байна"));
            }
            copy(body, brand);
            Brand saved = brandRepository.save(brand);
            if (!oldName.equals(newName)) productRepository.renameBrand(oldName, newName);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    // Брэндийг устгахад бараанууд хэвээр, зөвхөн брэндгүй болно
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return brandRepository.findById(id).<ResponseEntity<Void>>map(brand -> {
            productRepository.renameBrand(brand.getName(), null);
            brandRepository.delete(brand);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private static void copy(Brand from, Brand to) {
        to.setName(from.getName().trim());
        String url = from.getLogoUrl();
        to.setLogoUrl(url == null || url.isBlank() ? null : url.trim());
    }

    private static String validate(Brand b) {
        if (b.getName() == null || b.getName().isBlank()) return "Брэндийн нэрээ оруулна уу";
        if (b.getName().trim().length() > 60) return "Нэр 60 тэмдэгтээс ихгүй байна";
        if (b.getName().contains(",")) return "Нэрэнд таслал байж болохгүй";
        String url = b.getLogoUrl();
        if (url != null && !url.isBlank()) {
            String u = url.trim().toLowerCase();
            if (!u.startsWith("http://") && !u.startsWith("https://") && !u.startsWith("/brands/")
                    && !u.startsWith("/uploads/"))
                return "Логоны холбоос буруу байна";
            if (u.length() > 500) return "Логоны холбоос хэт урт байна";
        }
        return null;
    }
}
