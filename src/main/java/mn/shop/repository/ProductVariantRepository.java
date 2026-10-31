package mn.shop.repository;

import mn.shop.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    // Нэг мөрийн нөхцөлт шинэчлэл тул зэрэг захиалга үлдэгдлийг хасах руу оруулж чадахгүй. 0 бол хүрэлцэхгүй.
    @Modifying
    @Query("update ProductVariant v set v.stock = v.stock - :quantity where v.id = :id and v.stock >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("update ProductVariant v set v.stock = v.stock + :quantity where v.id = :id")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
