package mn.shop.controller;

import mn.shop.model.Category;
import mn.shop.repository.ProductRepository;
import mn.shop.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// GET нь хүн бүрт нээлттэй, POST/PUT/DELETE нь AdminInterceptor-оор зөвхөн ADMIN-д
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductRepository productRepository;

    // Бүх категорийг авах
    @GetMapping
    public List<Category> getAll() {
        return categoryService.findAll();
    }

    // ID-аар нэг категори авах
    @GetMapping("/{id}")
    public ResponseEntity<Category> getById(@PathVariable Short id) {
        return categoryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Шинэ категори нэмэх
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Category body) {
        String error = validateName(body.getName(), null);
        if (error != null) return badRequest(error);

        String parentError = validateParent(body.getParentId(), null);
        if (parentError != null) return badRequest(parentError);

        Category category = new Category();
        category.setName(body.getName().trim());
        category.setParentId(body.getParentId());
        return ResponseEntity.ok(categoryService.save(category));
    }

    // Категорийн нэр, эцэг ангилал шинэчлэх
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Short id, @RequestBody Category body) {
        return categoryService.findById(id).<ResponseEntity<?>>map(category -> {
            String error = validateName(body.getName(), id);
            if (error != null) return badRequest(error);

            String parentError = validateParent(body.getParentId(), id);
            if (parentError != null) return badRequest(parentError);

            category.setName(body.getName().trim());
            category.setParentId(body.getParentId());
            return ResponseEntity.ok(categoryService.save(category));
        }).orElse(notFound(id));
    }

    // Категори устгах. Дотор нь дэд ангилал эсвэл бүтээгдэхүүн байвал устгахгүй (FK алдаанаас сэргийлнэ)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Short id) {
        if (categoryService.findById(id).isEmpty()) return notFound(id);

        boolean hasChildren = categoryService.findAll().stream()
                .anyMatch(c -> id.equals(c.getParentId()));
        if (hasChildren) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Энэ ангилалд дэд ангилал байна. Эхлээд тэдгээрийг устгана уу"));
        }

        long count = productRepository.countByCategoryId(id);
        if (count > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Энэ ангилалд " + count + " бүтээгдэхүүн байна. Эхлээд тэдгээрийг өөр ангилал руу шилжүүлэх эсвэл устгана уу"));
        }
        categoryService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Нэр хоосон, хэт урт, эсвэл өөр ангилалтай давхардсан бол алдааны мессеж буцаана. */
    private String validateName(String name, Short selfId) {
        if (name == null || name.isBlank()) return "Ангиллын нэрээ оруулна уу";
        String trimmed = name.trim();
        if (trimmed.length() > 45) return "Нэр 45 тэмдэгтээс ихгүй байна";

        boolean duplicate = categoryService.findAll().stream()
                .anyMatch(c -> !c.getId().equals(selfId) && c.getName().trim().equalsIgnoreCase(trimmed));
        return duplicate ? "Ийм нэртэй ангилал аль хэдийн байна" : null;
    }

    /** Эцэг нь байгаа, өөрөө өөрийнхөө эцэг биш, нийт 2 түвшнээс гүнзгий биш эсэхийг шалгана. */
    private String validateParent(Short parentId, Short selfId) {
        if (parentId == null) return null;
        if (parentId.equals(selfId)) return "Ангилал өөрийнхөө дэд ангилал байж болохгүй";

        Category parent = categoryService.findById(parentId).orElse(null);
        if (parent == null) return "Эцэг ангилал олдсонгүй";
        if (parent.getParentId() != null) {
            return "Дэд ангилалын дотор дахин дэд ангилал үүсгэх боломжгүй";
        }
        if (selfId != null && categoryService.findAll().stream()
                .anyMatch(c -> selfId.equals(c.getParentId()))) {
            return "Дэд ангилалтай ангилалыг өөр ангилалын дэд болгож болохгүй";
        }
        return null;
    }

    private static ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private static ResponseEntity<?> notFound(Short id) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "ID=" + id + " ангилал олдсонгүй"));
    }
}
