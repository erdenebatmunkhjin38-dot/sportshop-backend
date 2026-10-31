package mn.shop.repository;

import mn.shop.model.OrderedVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderedVariantRepository extends JpaRepository<OrderedVariant, Long> {
    List<OrderedVariant> findByCustomerOrderId(Long customerOrderId);
}
