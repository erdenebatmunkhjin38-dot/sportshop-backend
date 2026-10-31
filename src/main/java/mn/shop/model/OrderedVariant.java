package mn.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Захиалгаас хасагдсан хувилбарын тоо. Цуцлахад үлдэгдлийг буцаахад ашиглана.
@Getter
@Setter
@Entity
@Table(name = "ordered_variant")
public class OrderedVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_order_id", nullable = false)
    private Long customerOrderId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
