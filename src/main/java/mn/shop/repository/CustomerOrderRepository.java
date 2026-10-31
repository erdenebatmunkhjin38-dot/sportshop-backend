package mn.shop.repository;

import mn.shop.model.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    java.util.List<CustomerOrder> findByCustomerEmailIgnoreCaseOrderByDateCreatedDesc(String email);
}