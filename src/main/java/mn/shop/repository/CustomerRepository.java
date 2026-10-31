package mn.shop.repository;

import mn.shop.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    // Хэрэгцээтэй бол энд иймэрхүү нэмэлт хайлтын функц бичиж болно:
    // Optional<Customer> findByEmail(String email);
}