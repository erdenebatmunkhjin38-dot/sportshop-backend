package mn.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "customer_order")
public class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "INT UNSIGNED")
    private Long id;

    @Column(name = "amount", nullable = false, precision = 9, scale = 2)
    private BigDecimal amount;

    // Хүргэлтийн төлбөр (amount-д багтсан). Хуучин захиалгад хоосон
    @Column(name = "delivery_fee", precision = 9, scale = 2)
    private BigDecimal deliveryFee;

    // Энд 'n' үсэг дутуу байсныг засав
    @Column(name = "confirmation_number", nullable = false, columnDefinition = "INT UNSIGNED")
    private Long confirmationNumber;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime dateCreated;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false, columnDefinition = "INT UNSIGNED")
    private Customer customer;

    // Төлбөрийн хэлбэр: QPAY эсвэл банкны код (KHAN, GOLOMT, TDB, XAC, STATE). Хуучин захиалгад хоосон
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    // Төлбөрийн төлөв: PENDING (төлөөгүй), PAID (төлсөн)
    @Column(name = "payment_status", length = 20)
    private String paymentStatus;

    // Захиалгын явц: NEW, PROCESSING, SHIPPED, DELIVERED, CANCELLED. Хуучин захиалгад хоосон
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // QPay-ээр төлөх үед үүсгэсэн нэхэмжлэлийн дугаар (төлбөр шалгахад ашиглана)
    @Column(name = "qpay_invoice_id", length = 64)
    private String qpayInvoiceId;
}