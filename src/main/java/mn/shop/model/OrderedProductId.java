package mn.shop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderedProductId implements Serializable {
    private Long customerOrderId;
    private Long productId;
}