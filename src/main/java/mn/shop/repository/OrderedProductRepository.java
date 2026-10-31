package mn.shop.repository;

import mn.shop.model.OrderedProduct;
import mn.shop.model.OrderedProductId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderedProductRepository extends JpaRepository<OrderedProduct, OrderedProductId> {

    java.util.List<mn.shop.model.OrderedProduct> findByCustomerOrderId(Long customerOrderId);
}
