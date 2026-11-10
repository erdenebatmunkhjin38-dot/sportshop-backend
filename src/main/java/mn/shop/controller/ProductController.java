package mn.shop.controller;

import mn.shop.model.Product;
import mn.shop.model.ProductVariant;
import mn.shop.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    // Бүх бүтээгдэхүүнийг авах
    @GetMapping
    public List<Product> getAll() {
        return productService.findAll();
    }

    // Категори ID-аар бүтээгдэхүүн авах
    @GetMapping("/category/{categoryId}")
    public List<Product> getByCategory(@PathVariable Short categoryId) {
        return productService.findByCategoryId(categoryId);
    }

    // ID-аар нэг бүтээгдэхүүн авах
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Шинэ бүтээгдэхүүн нэмэх
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Product product) {
        String error = validateImageUrl(product.getImageUrl());
        if (error == null) error = validateOldPrice(product.getPrice(), product.getOldPrice());
        if (error == null) error = validateStock(product.getStock());
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));

        product.setImageUrl(blankToNull(product.getImageUrl()));
        product.setBrand(blankToNull(product.getBrand()));
        product.setSizes(blankToNull(product.getSizes()));
        List<ProductVariant> incoming = product.getVariants();
        product.setVariants(new java.util.ArrayList<>());
        applyVariants(product, incoming);
        return ResponseEntity.ok(productService.save(product));
    }

    // ID-аар бүтээгдэхүүн засах (PUT /api/products/{id})
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Product productDetails) {
        String error = validateImageUrl(productDetails.getImageUrl());
        if (error == null) error = validateOldPrice(productDetails.getPrice(), productDetails.getOldPrice());
        if (error == null) error = validateStock(productDetails.getStock());
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));

        return productService.findById(id).map(existingProduct -> {

            // 1. Үндсэн мэдээллүүдийг шинэчлэх
            existingProduct.setName(productDetails.getName());
            existingProduct.setDescription(productDetails.getDescription());
            existingProduct.setPrice(productDetails.getPrice());
            existingProduct.setImageUrl(blankToNull(productDetails.getImageUrl()));
            existingProduct.setBrand(blankToNull(productDetails.getBrand()));
            existingProduct.setStock(productDetails.getStock());
            existingProduct.setSizes(blankToNull(productDetails.getSizes()));

            applyVariants(existingProduct, productDetails.getVariants());

            // 2. Хямдрал, онцлох, бестселлер
            existingProduct.setOldPrice(productDetails.getOldPrice());
            existingProduct.setSaleEndsAt(productDetails.getSaleEndsAt());
            existingProduct.setOnSale(productDetails.isOnSale());
            existingProduct.setFeatured(productDetails.isFeatured());
            existingProduct.setBestseller(productDetails.isBestseller());
            existingProduct.setNewArrival(productDetails.isNewArrival());

            // 3. Категори хоосон биш бол шинэчлэх
            if (productDetails.getCategory() != null) {
                existingProduct.setCategory(productDetails.getCategory());
            }

            // 4. Өөрчлөлтийг өгөгдлийн санд хадгалаад буцаах
            Product updatedProduct = productService.save(existingProduct);
            return ResponseEntity.ok(updatedProduct);

        }).orElse(ResponseEntity.notFound().build());
        // Хэрэв тухайн ID-тай бүтээгдэхүүн олдохгүй бол 404 Not Found буцаана.
    }

    // Бүтээгдэхүүн устгах
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Зураг заавал биш. Байвал http/https холбоос эсвэл админы оруулсан /uploads/ зураг
     * (javascript: гэх мэтээс сэргийлнэ).
     */
    private static String validateImageUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim().toLowerCase();
        if (!u.startsWith("http://") && !u.startsWith("https://") && !u.startsWith("/uploads/")) {
            return "Зургийн холбоос http:// эсвэл https://-ээр эхлэх ёстой";
        }
        if (url.trim().length() > 500) return "Зургийн холбоос хэт урт байна";
        return null;
    }

    /** Хямдралын өмнөх үнэ байвал одоогийн үнээс их байх ёстой. */
    private static String validateOldPrice(BigDecimal price, BigDecimal oldPrice) {
        if (oldPrice == null) return null;
        if (oldPrice.signum() <= 0) return "Хямдралын өмнөх үнэ 0-ээс их байх ёстой";
        if (price != null && oldPrice.compareTo(price) <= 0) {
            return "Хямдралын өмнөх үнэ одоогийн үнээс их байх ёстой";
        }
        return null;
    }

    /**
     * Ирсэн хувилбаруудтай ижилсүүлнэ. Ижил өнгө+хэмжээтай мөрийг шинэчилж, бусдыг нь устгана.
     * Хувилбартай бол нийт үлдэгдэл, хэмжээний жагсаалтыг тэдгээрээс бодно.
     */
    private static void applyVariants(Product product, List<ProductVariant> incoming) {
        if (incoming == null) return;

        Set<String> keep = new HashSet<>();
        for (ProductVariant in : incoming) {
            String color = in.getColor() == null ? "" : in.getColor().trim();
            String size = in.getSize() == null ? "" : in.getSize().trim();
            if ((color.isEmpty() && size.isEmpty()) || !keep.add(color + "|" + size)) continue;

            ProductVariant target = product.getVariants().stream()
                    .filter(v -> v.getColor().equals(color) && v.getSize().equals(size))
                    .findFirst().orElse(null);
            if (target == null) {
                target = new ProductVariant();
                target.setProduct(product);
                target.setColor(color);
                target.setSize(size);
                product.getVariants().add(target);
            }
            target.setStock(in.getStock() == null ? 0 : Math.max(0, in.getStock()));
            target.setImageUrl(blankToNull(in.getImageUrl()));
        }
        product.getVariants().removeIf(v -> !keep.contains(v.getColor() + "|" + v.getSize()));

        if (!product.getVariants().isEmpty()) {
            product.setStock(product.getVariants().stream().mapToInt(ProductVariant::getStock).sum());
            Set<String> sizes = new LinkedHashSet<>();
            product.getVariants().forEach(v -> { if (!v.getSize().isEmpty()) sizes.add(v.getSize()); });
            product.setSizes(sizes.isEmpty() ? null : String.join(",", sizes));
        }
    }

    private static String validateStock(Integer stock) {
        return stock != null && stock < 0 ? "Үлдэгдэл 0-ээс бага байж болохгүй" : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
