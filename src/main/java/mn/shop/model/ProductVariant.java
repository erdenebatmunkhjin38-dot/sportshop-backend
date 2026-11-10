package mn.shop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Барааны хувилбар: өнгө + хэмжээ тус бүрийн үлдэгдэл. Өнгө эсвэл хэмжээ хоосон байж болно.
@Getter
@Setter
@Entity
@Table(name = "product_variant")
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false, columnDefinition = "INT UNSIGNED")
    private Product product;

    @Column(name = "color", nullable = false, length = 40)
    private String color = "";

    @Column(name = "size", nullable = false, length = 20)
    private String size = "";

    @Column(name = "stock", nullable = false)
    private Integer stock = 0;

    // Энэ өнгийн зураг. Хоосон бол барааны үндсэн зураг харагдана
    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
