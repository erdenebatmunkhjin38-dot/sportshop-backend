package mn.shop.service;

import mn.shop.model.Product;
import mn.shop.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public List<Product> findByCategoryId(Short categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    public Product save(Product product) {
        /*
           Хэрэв Product модель дээрээ @UpdateTimestamp ашигласан бол
           product.setLastUpdate(...) гэж бичих шаардлагагүй.
           Hibernate өөрөө автоматаар цагийг нь тааруулчихна.
        */
        return productRepository.save(product);
    }

    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }
}