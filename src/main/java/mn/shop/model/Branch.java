package mn.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Дэлгүүрийн салбар
@Getter
@Setter
@Entity
@Table(name = "branch")
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    // Салбарын зураг (http/https эсвэл frontend-ийн /images/...). Заавал биш.
    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
