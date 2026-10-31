package mn.shop.repository;

import mn.shop.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Категориор нь бүтээгдэхүүн шүүж харах функц нэмж болно
    List<Product> findByCategoryId(Short categoryId);

    // Ангилал устгахын өмнө доторх бүтээгдэхүүний тоог шалгана
    long countByCategoryId(Short categoryId);

    // Нэг мөрийн нөхцөлт шинэчлэл тул зэрэг захиалга үлдэгдлийг хасах руу оруулж чадахгүй.
    // 0 буцаавал үлдэгдэл хүрэлцэхгүй. stock хоосон бол тоолохгүй.
    @Modifying
    @Query("update Product p set p.stock = p.stock - :quantity where p.id = :id and (p.stock is null or p.stock >= :quantity)")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("update Product p set p.stock = p.stock + :quantity where p.id = :id and p.stock is not null")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("update Product p set p.brand = :newName where p.brand = :oldName")
    int renameBrand(@Param("oldName") String oldName, @Param("newName") String newName);
}
