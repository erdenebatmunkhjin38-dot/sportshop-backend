package mn.shop.controller;

import mn.shop.model.CustomerOrder;
import mn.shop.repository.OrderedProductRepository;
import mn.shop.repository.OrderedVariantRepository;
import mn.shop.repository.ProductRepository;
import mn.shop.repository.ProductVariantRepository;
import mn.shop.service.CustomerOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController

@RequestMapping("/api/orders")
public class CustomerOrderController {

    @Autowired
    private CustomerOrderService orderService;

    @Autowired
    private OrderedProductRepository orderedProducts;

    @Autowired
    private ProductRepository products;

    @Autowired
    private OrderedVariantRepository orderedVariants;

    @Autowired
    private ProductVariantRepository variants;

    @Autowired
    private CheckoutController checkout;

    private static final Set<String> STATUSES = Set.of("NEW", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED");

    record StatusBody(String status) {}

    // PUT: Дансаар шилжүүлсэн төлбөрийг админ шалгаад баталгаажуулна (PAID болгож, и-мэйл илгээнэ)
    @PutMapping("/{id}/payment")
    public ResponseEntity<?> confirmPayment(@PathVariable Long id) {
        return checkout.confirmByAdmin(id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT: Захиалгын явц өөрчлөх. Цуцлахад үлдэгдэл буцаж нэмэгдэнэ
    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody StatusBody body) {
        if (body == null || !STATUSES.contains(body.status())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Төлөв буруу байна"));
        }
        return orderService.findById(id).<ResponseEntity<?>>map(order -> {
            if ("CANCELLED".equals(order.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Цуцалсан захиалгыг өөрчлөх боломжгүй"));
            }
            if ("CANCELLED".equals(body.status())) {
                orderedProducts.findByCustomerOrderId(id)
                        .forEach(line -> products.increaseStock(line.getProductId(), line.getQuantity()));
                orderedVariants.findByCustomerOrderId(id)
                        .forEach(line -> variants.increaseStock(line.getVariantId(), line.getQuantity()));
            }
            order.setStatus(body.status());
            return ResponseEntity.ok(orderService.save(order));
        }).orElse(ResponseEntity.notFound().build());
    }

    // GET: Бүх захиалга авах
    // Хаяг: GET http://localhost:8080/api/orders
    @GetMapping
    public List<CustomerOrder> getAll() {
        return orderService.findAll();
    }

    // GET: ID гаар нь харах
    // Хаяг: GET http://localhost:8080/api/orders/{id}
    @GetMapping("/{id}")
    public ResponseEntity<CustomerOrder> getById(@PathVariable Long id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /// DEL: Захиалга устгах
    // Хаяг: DELETE http://localhost:8080/api/orders/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        return orderService.findById(id).map(order -> {
            // Захиалга хийсэн хэрэглэгчийн нэрийг авч харуулах (сонголтоор)
            String customerName = order.getCustomer().getName();

            orderService.deleteById(id);

            // Буцаах мессеж
            return ResponseEntity.ok(id + " ID-тай захиалга (Хэрэглэгч: " + customerName + ") амжилттай устлаа.");
        }).orElse(ResponseEntity.status(404).body(id + " ID-тай захиалга олдсонгүй."));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerOrder> update(@PathVariable Long id, @RequestBody CustomerOrder orderDetails) {
        return orderService.findById(id).map(order -> {
            // Зөвхөн өөрчлөх боломжтой талбаруудыг шинэчилнэ
            if (orderDetails.getAmount() != null) {
                order.setAmount(orderDetails.getAmount());
            }
            if (orderDetails.getConfirmationNumber() != null) {
                order.setConfirmationNumber(orderDetails.getConfirmationNumber());
            }
            if (orderDetails.getCustomer() != null) {
                order.setCustomer(orderDetails.getCustomer());
            }

            // dateCreated-ийг гараар SET хийх шаардлагагүй,
            // учир нь энэ нь updatable = false байгаа.

            CustomerOrder updatedOrder = orderService.save(order);
            return ResponseEntity.ok(updatedOrder);
        }).orElse(ResponseEntity.notFound().build());
    }

    // POST: Шинэ захиалга үүсгэх
    // Хаяг: POST http://localhost:8080/api/orders
    @PostMapping
    public CustomerOrder create(@RequestBody CustomerOrder order) {
        return orderService.save(order);
    }
}