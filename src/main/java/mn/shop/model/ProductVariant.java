package mn.shop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Барааны хувилбар: өнгө + размер тус бүрийн үлдэгдэл. Өнгө эсвэл размер хоосон байж болно.
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
}
