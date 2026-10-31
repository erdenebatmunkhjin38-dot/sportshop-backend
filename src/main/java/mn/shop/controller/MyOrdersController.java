package mn.shop.controller;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import mn.shop.model.CustomerOrder;
import mn.shop.model.OrderedProduct;
import mn.shop.repository.CustomerOrderRepository;
import mn.shop.repository.OrderedProductRepository;
import mn.shop.security.AdminInterceptor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Нэвтэрсэн хэрэглэгч зөвхөн өөрийн захиалгуудыг харна (и-мэйлийг токеноос авна). */
@RestController
public class MyOrdersController {

    private final CustomerOrderRepository orders;
    private final OrderedProductRepository orderedProducts;

    public MyOrdersController(CustomerOrderRepository orders, OrderedProductRepository orderedProducts) {
        this.orders = orders;
        this.orderedProducts = orderedProducts;
    }

    @GetMapping("/api/my-orders")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myOrders(HttpServletRequest req) {
        Claims claims = (Claims) req.getAttribute(AdminInterceptor.CLAIMS_ATTR);
        String email = claims.get("username", String.class);
        return orders.findByCustomerEmailIgnoreCaseOrderByDateCreatedDesc(email).stream().map(this::toView).toList();
    }

    private Map<String, Object> toView(CustomerOrder o) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", o.getId());
        view.put("confirmationNumber", o.getConfirmationNumber());
        view.put("amount", o.getAmount());
        view.put("deliveryFee", o.getDeliveryFee());
        view.put("dateCreated", o.getDateCreated());
        view.put("paymentMethod", o.getPaymentMethod());
        view.put("paymentStatus", o.getPaymentStatus());
        view.put("status", o.getStatus());
        view.put("address", o.getCustomer().getCityRegion() + ", " + o.getCustomer().getAddress());
        view.put("items", orderedProducts.findByCustomerOrderId(o.getId()).stream().map(this::toItem).toList());
        return view;
    }

    private Map<String, Object> toItem(OrderedProduct line) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", line.getProductId());
        item.put("name", line.getProduct() != null ? line.getProduct().getName() : "Бүтээгдэхүүн #" + line.getProductId());
        item.put("imageUrl", line.getProduct() != null ? line.getProduct().getImageUrl() : null);
        item.put("price", line.getUnitPrice() != null ? line.getUnitPrice()
                : line.getProduct() != null ? line.getProduct().getPrice() : null);
        item.put("quantity", line.getQuantity());
        item.put("sizes", line.getSizes());
        return item;
    }
}
