package mn.shop.controller;

import mn.shop.model.OrderedProduct;
import mn.shop.model.OrderedProductId;
import mn.shop.repository.OrderedProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController

@RequestMapping("/api/ordered-products")
public class OrderedProductController {

    @Autowired
    private OrderedProductRepository repository;

    // Бүх захиалсан барааг харах
    @GetMapping
    public List<OrderedProduct> getAll() {
        return repository.findAll();
    }

    // Шинээр бараа нэмэх (Захиалга руу)
    @PostMapping
    public OrderedProduct create(@RequestBody OrderedProduct orderedProduct) {
        return repository.save(orderedProduct);
    }

    // Нэг барааг харах (Захиалгын ID болон Барааны ID-аар)
    @GetMapping("/{orderId}/{productId}")
    public ResponseEntity<OrderedProduct> getOne(@PathVariable Long orderId, @PathVariable Long productId) {
        OrderedProductId id = new OrderedProductId();
        id.setCustomerOrderId(orderId);
        id.setProductId(productId);

        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    // Захиалсан барааны тоо хэмжээг засах
    @PutMapping("/{orderId}/{productId}")
    public ResponseEntity<OrderedProduct> update(
            @PathVariable Long orderId,
            @PathVariable Long productId,
            @RequestBody OrderedProduct updatedDetails) {

        OrderedProductId id = new OrderedProductId();
        id.setCustomerOrderId(orderId);
        id.setProductId(productId);

        return repository.findById(id).map(orderedProduct -> {
            // Жишээ нь: Зөвхөн тоо хэмжээг (quantity) засах
            orderedProduct.setQuantity(updatedDetails.getQuantity());
            // Хэрэв өөр засах талбарууд байгаа бол энд нэмж болно

            return ResponseEntity.ok(repository.save(orderedProduct));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Захиалсан барааг устгах
    @DeleteMapping("/{orderId}/{productId}")
    public ResponseEntity<Void> delete(@PathVariable Long orderId, @PathVariable Long productId) {
        OrderedProductId id = new OrderedProductId();
        id.setCustomerOrderId(orderId);
        id.setProductId(productId);

        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}