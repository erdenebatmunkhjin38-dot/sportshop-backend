package mn.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ordered_product")
@IdClass(OrderedProductId.class) // Түлхүүр классыг энд зааж өгнө
public class OrderedProduct {

    @Id
    @Column(name = "customer_order_id")
    private Long customerOrderId;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // Захиалах үеийн нэгж үнэ (бараан үнэ дараа өөрчлөгдсөн ч түүх зөв үлдэнэ)
    @Column(name = "unit_price", precision = 9, scale = 2)
    private java.math.BigDecimal unitPrice;

    // Сонгосон хэмжээнүүд: "42 × 1, 43 × 2"
    @Column(name = "sizes", length = 200)
    private String sizes;

    // customer_order_id-г дээрх @Id талбар бичдэг тул энд зөвхөн уншина
    // (эс бөгөөс INSERT-д багана давхардаж алдаа гардаг)
    @ManyToOne
    @JoinColumn(name = "customer_order_id", referencedColumnName = "id",
            insertable = false, updatable = false)
    private CustomerOrder customerOrder;
    @ManyToOne
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;
}