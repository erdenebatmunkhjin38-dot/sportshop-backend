package mn.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "product")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "INT UNSIGNED")
    private Long id;

    @Column(name = "name", nullable = false, length = 45)
    private String name;

    @Column(name = "price", nullable = false, precision = 9, scale = 2)
    private BigDecimal price;

    // Хямдралын өмнөх үнэ (дээр нь зураас татаж харуулна). Заавал биш
    @Column(name = "old_price", precision = 9, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "description", columnDefinition = "TINYTEXT")
    private String description;

    // Хямдрал дуусах цаг. Хоосон бол хугацаагүй
    @Column(name = "sale_ends_at")
    private LocalDateTime saleEndsAt;

    // Брэнд (Nike, Adidas гэх мэт). Заавал биш
    @Column(name = "brand", length = 60)
    private String brand;

    // Үлдэгдэл (ширхэг). Хоосон бол тоолохгүй
    @Column(name = "stock")
    private Integer stock;

    // Размерууд таслалаар: "40,41,42" эсвэл "S,M,L". Заавал биш
    @Column(name = "sizes", length = 200)
    private String sizes;

    // Бүтээгдэхүүний зургийн холбоос (http/https). Заавал биш.
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // "Бусад" шүүлтүүр: хямдралтай, онцлох, бестселлер
    @Column(name = "on_sale", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean onSale;

    @Column(name = "featured", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean featured;

    @Column(name = "bestseller", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean bestseller;

    // Шинэ бараа гэж админ тэмдэглэнэ
    @Column(name = "new_arrival", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean newArrival;

    @UpdateTimestamp
    @Column(name = "last_update", nullable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime lastUpdate;

    // Өнгө + размер тус бүрийн үлдэгдэл. Байвал stock нь тэдгээрийн нийлбэр болно
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<ProductVariant> variants = new ArrayList<>();

    /** Одоо хүчинтэй үнэ: хямдралын хугацаа дууссан бол хуучин үнэ рүүгээ буцна */
    public BigDecimal currentPrice() {
        boolean over = saleEndsAt != null && oldPrice != null && saleEndsAt.isBefore(LocalDateTime.now());
        return over ? oldPrice : price;
    }

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Category category;
}
