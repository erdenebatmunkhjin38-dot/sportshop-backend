package mn.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Брэнд. Бараан дээр нэрээр нь (product.brand) холбогдоно.
@Getter
@Setter
@Entity
@Table(name = "brand")
public class Brand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    // Лого (http/https эсвэл frontend-ийн /brands/...). Заавал биш.
    @Column(name = "logo_url", length = 500)
    private String logoUrl;
}
