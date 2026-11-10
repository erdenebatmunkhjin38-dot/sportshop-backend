package mn.shop.controller;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import mn.shop.model.Customer;
import mn.shop.model.CustomerOrder;
import mn.shop.model.OrderedProduct;
import mn.shop.model.OrderedVariant;
import mn.shop.model.Product;
import mn.shop.model.ProductVariant;
import mn.shop.repository.CustomerOrderRepository;
import mn.shop.repository.CustomerRepository;
import mn.shop.repository.OrderedProductRepository;
import mn.shop.repository.OrderedVariantRepository;
import mn.shop.repository.ProductRepository;
import mn.shop.repository.ProductVariantRepository;
import mn.shop.repository.BankAccountRepository;
import mn.shop.repository.SiteSettingsRepository;
import mn.shop.model.SiteSettings;
import mn.shop.security.AdminInterceptor;
import mn.shop.service.OrderMailService;
import mn.shop.service.QPayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;

/**
 * Сагсаар захиалга үүсгэнэ. Зөвхөн нэвтэрсэн хэрэглэгч (AdminInterceptor шалгана).
 * Үнийг frontend-ээс биш, өгөгдлийн сангаас авч тооцно — хэрэглэгч үнийг өөрчилж чадахгүй.
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    record Item(Long productId, Integer quantity, String size, String color) {}
    record CheckoutBody(String name, String phone, String address, String cityRegion,
                        String paymentMethod, List<Item> items) {}

    private static final int MAX_QUANTITY = 99;
    // QPay эсвэл банкны дансаар (банк бүр тусдаа код)
    private static final Set<String> PAYMENT_METHODS =
            Set.of("QPAY", "KHAN", "GOLOMT", "TDB", "XAC", "STATE");
    private final SecureRandom random = new SecureRandom();

    private final ProductRepository products;
    private final CustomerRepository customers;
    private final CustomerOrderRepository orders;
    private final OrderedProductRepository orderedProducts;
    private final ProductVariantRepository variants;
    private final OrderedVariantRepository orderedVariants;
    private final QPayService qpay;
    private final OrderMailService mail;
    private final SiteSettingsRepository settings;
    private final BankAccountRepository bankAccounts;

    // Хүргэлтийн төлбөр байршлаас хамаарна. frontend-ийн app/lib/delivery.ts-тэй ижил байх ёстой
    @Value("${app.delivery.ulaanbaatar:7000}")
    private BigDecimal ulaanbaatarFee;
    @Value("${app.delivery.rural:7000}")
    private BigDecimal ruralFee;

    // Үүсгэсэн QPay нэхэмжлэлийг (QR зураг) санах ойд хадгална — сервер дахин асахад шинээр үүсгэнэ
    private final Map<Long, QPayService.Invoice> invoices = new java.util.concurrent.ConcurrentHashMap<>();

    public CheckoutController(ProductRepository products, CustomerRepository customers,
                              CustomerOrderRepository orders, OrderedProductRepository orderedProducts,
                              QPayService qpay, OrderMailService mail,
                              ProductVariantRepository variants, OrderedVariantRepository orderedVariants,
                              SiteSettingsRepository settings, BankAccountRepository bankAccounts) {
        this.products = products;
        this.customers = customers;
        this.orders = orders;
        this.orderedProducts = orderedProducts;
        this.qpay = qpay;
        this.mail = mail;
        this.variants = variants;
        this.orderedVariants = orderedVariants;
        this.settings = settings;
        this.bankAccounts = bankAccounts;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> checkout(@RequestBody CheckoutBody body, HttpServletRequest req) {
        // Хүргэлтийн мэдээллийг шалгана (DB-д 45 тэмдэгтээр хязгаарлагдсан)
        String error = validate(body);
        // Дансаар төлөхөд тухайн банкны данс админ тохиргоонд оруулсан байх ёстой
        if (error == null && !"QPAY".equals(body.paymentMethod()) && !bankAccounts.existsById(body.paymentMethod())) {
            error = "Энэ банкаар төлөх боломжгүй байна. Өөр төлбөрийн хэлбэр сонгоно уу";
        }
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));

        // Нэвтэрсэн хэрэглэгчийн и-мэйлийг токеноос авна
        Claims claims = (Claims) req.getAttribute(AdminInterceptor.CLAIMS_ATTR);
        String email = claims.get("username", String.class);

        // Ижил бүтээгдэхүүнийг нэгтгэж, DB-ээс үнийг нь авна
        // Нэг барааны өөр хэмжээнүүд нэг мөрд нэгдэж, хэмжээыг тайлбараар хадгална
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        Map<Long, List<String>> sizes = new HashMap<>();
        for (Item item : body.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
            String label = String.join(" / ", java.util.stream.Stream.of(clean(item.color()), clean(item.size()))
                    .filter(s -> !s.isEmpty()).toList());
            if (!label.isEmpty()) {
                sizes.computeIfAbsent(item.productId(), k -> new ArrayList<>()).add(label + " × " + item.quantity());
            }
        }
        List<Product> found = products.findAllById(quantities.keySet());
        if (found.size() != quantities.size()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Зарим бүтээгдэхүүн олдсонгүй"));
        }

        // Сонгосон өнгө, хэмжээний үлдэгдлийг хасна
        Map<Long, Integer> takenVariants = new LinkedHashMap<>();
        for (Item item : body.items()) {
            Product p = found.stream().filter(x -> x.getId().equals(item.productId())).findFirst().orElseThrow();
            if (p.getVariants().isEmpty()) continue;

            ProductVariant variant = p.getVariants().stream()
                    .filter(v -> v.getColor().equals(clean(item.color())) && v.getSize().equals(clean(item.size())))
                    .findFirst().orElse(null);
            if (variant == null) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ResponseEntity.badRequest().body(Map.of("message", p.getName() + ": өнгө, хэмжээгээ сонгоно уу"));
            }
            if (variants.decreaseStock(variant.getId(), item.quantity()) == 0) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ResponseEntity.status(409).body(Map.of("message",
                        p.getName() + " (" + variant.getColor() + " " + variant.getSize() + "): үлдэгдэл хүрэлцэхгүй байна"));
            }
            takenVariants.merge(variant.getId(), item.quantity(), Integer::sum);
        }

        // Үлдэгдлийг хасна. Хүрэлцэхгүй бол бүх гүйлгээг буцаана
        for (Product p : found) {
            if (products.decreaseStock(p.getId(), quantities.get(p.getId())) == 0) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ResponseEntity.status(409).body(Map.of("message",
                        p.getName() + ": үлдэгдэл хүрэлцэхгүй байна (" + p.getStock() + " ширхэг үлдсэн)"));
            }
        }

        BigDecimal amount = BigDecimal.ZERO;
        for (Product p : found) {
            amount = amount.add(p.currentPrice().multiply(BigDecimal.valueOf(quantities.get(p.getId()))));
        }
        BigDecimal deliveryFee = body.cityRegion().trim().startsWith("Улаанбаатар") ? ulaanbaatarFee : ruralFee;
        // Барааны дүн админы тохируулсан босгоос их бол хүргэлт үнэгүй
        Long freeOver = settings.findById(SiteSettings.SINGLETON_ID).map(SiteSettings::getFreeDeliveryOver).orElse(null);
        if (freeOver != null && amount.compareTo(BigDecimal.valueOf(freeOver)) >= 0) {
            deliveryFee = BigDecimal.ZERO;
        }
        amount = amount.add(deliveryFee);

        Customer customer = new Customer();
        customer.setName(body.name().trim());
        customer.setEmail(email);
        customer.setPhone(body.phone().trim());
        customer.setAddress(body.address().trim());
        customer.setCityRegion(body.cityRegion().trim());
        customer = customers.save(customer);

        CustomerOrder order = new CustomerOrder();
        order.setCustomer(customer);
        order.setAmount(amount);
        order.setDeliveryFee(deliveryFee);
        // Захиалгын баталгаажуулах 6 оронтой дугаар
        order.setConfirmationNumber(100000L + random.nextInt(900000));
        order.setPaymentMethod(body.paymentMethod());
        order.setPaymentStatus("PENDING");
        order.setStatus("NEW");
        order = orders.save(order);

        for (Product p : found) {
            OrderedProduct op = new OrderedProduct();
            op.setCustomerOrderId(order.getId());
            op.setProductId(p.getId());
            op.setCustomerOrder(order);
            op.setQuantity(quantities.get(p.getId()));
            op.setUnitPrice(p.currentPrice());
            if (sizes.containsKey(p.getId())) op.setSizes(String.join(", ", sizes.get(p.getId())));
            orderedProducts.save(op);
        }

        for (Map.Entry<Long, Integer> taken : takenVariants.entrySet()) {
            OrderedVariant line = new OrderedVariant();
            line.setCustomerOrderId(order.getId());
            line.setVariantId(taken.getKey());
            line.setQuantity(taken.getValue());
            orderedVariants.save(line);
        }

        return ResponseEntity.ok(Map.of(
                "orderId", order.getId(),
                "confirmationNumber", order.getConfirmationNumber(),
                "amount", amount,
                "deliveryFee", deliveryFee,
                "paymentMethod", order.getPaymentMethod()
        ));
    }

    /**
     * QPay нэхэмжлэл (QR код, банкны апп-ын холбоос) авна.
     * QPay тохируулаагүй бол {"demo": true} буцаана.
     */
    @PostMapping("/{orderId}/qpay-invoice")
    @Transactional
    public ResponseEntity<?> qpayInvoice(@PathVariable Long orderId, HttpServletRequest req) {
        Optional<CustomerOrder> found = ownOrder(orderId, req);
        if (found.isEmpty()) return notFound();
        CustomerOrder order = found.get();

        if (!qpay.isEnabled()) return ResponseEntity.ok(Map.of("demo", true));
        if (!"QPAY".equals(order.getPaymentMethod())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Энэ захиалга QPay-ээр төлөхөөр сонгогдоогүй"));
        }

        // Нэг захиалгад нэг л удаа нэхэмжлэл үүсгэнэ (хуудсыг refresh хийхэд дахин үүсгэхгүй)
        QPayService.Invoice invoice = invoices.get(order.getId());
        if (invoice == null) {
            try {
                invoice = qpay.createInvoice(order.getId(), order.getConfirmationNumber(),
                        order.getAmount(), order.getCustomer().getEmail());
            } catch (RuntimeException e) {
                return ResponseEntity.status(502).body(Map.of("message", "QPay-тэй холбогдож чадсангүй. Дахин оролдоно уу"));
            }
            invoices.put(order.getId(), invoice);
            order.setQpayInvoiceId(invoice.invoiceId());
            orders.save(order);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("demo", false);
        res.put("qrImage", invoice.qrImage());
        res.put("qrText", invoice.qrText());
        res.put("shortUrl", invoice.shortUrl());
        res.put("bankApps", invoice.bankApps());
        return ResponseEntity.ok(res);
    }

    /**
     * Захиалгын төлбөрийг баталгаажуулна. Зөвхөн захиалгын эзэн.
     * - QPay идэвхтэй үед: QPay-ээс төлбөр орсон эсэхийг шалгаж, орсон бол PAID болгоно
     *   (ороогүй бол 409). Frontend үүнийг хэдэн секунд тутам дуудна.
     * - QPay тохируулаагүй эсвэл дансаар шилжүүлсэн үед: хэрэглэгч "шилжүүлсэн" гэж мэдэгдэхэд
     *   CHECKING болно. Мөнгө орсныг админ шалгаад PUT /api/orders/{id}/payment-аар PAID болгоно.
     *   (Хэрэглэгч өөрөө PAID болгож чадахгүй.)
     */
    @PostMapping("/{orderId}/pay")
    @Transactional
    public ResponseEntity<?> pay(@PathVariable Long orderId, HttpServletRequest req) {
        Optional<CustomerOrder> found = ownOrder(orderId, req);
        if (found.isEmpty()) return notFound();
        CustomerOrder order = found.get();

        if (!"PAID".equals(order.getPaymentStatus())) {
            if ("QPAY".equals(order.getPaymentMethod()) && qpay.isEnabled()) {
                if (order.getQpayInvoiceId() == null
                        || !qpay.isPaid(order.getQpayInvoiceId(), order.getAmount())) {
                    return ResponseEntity.status(409).body(Map.of(
                            "message", "Төлбөр хараахан орж ирээгүй байна",
                            "pending", true));
                }
                markPaid(order);
            } else {
                order.setPaymentStatus("CHECKING");
                orders.save(order);
            }
        }

        return ResponseEntity.ok(Map.of(
                "orderId", order.getId(),
                "confirmationNumber", order.getConfirmationNumber(),
                "paymentStatus", order.getPaymentStatus()
        ));
    }

    /** QPay-ийн callback-аас дуудагдана (QpayCallbackController). Төлбөрийг дахин шалгаж баталгаажуулна. */
    @Transactional
    public void confirmFromCallback(Long orderId) {
        orders.findById(orderId).ifPresent(order -> {
            if ("PAID".equals(order.getPaymentStatus()) || order.getQpayInvoiceId() == null) return;
            // Callback-ыг хэн ч дуудаж болох тул QPay-ээс шалгалгүйгээр итгэхгүй
            if (qpay.isEnabled() && qpay.isPaid(order.getQpayInvoiceId(), order.getAmount())) {
                markPaid(order);
            }
        });
    }

    /** Админ данс руу мөнгө орсныг шалгаад баталгаажуулна (CustomerOrderController). */
    @Transactional
    public Optional<CustomerOrder> confirmByAdmin(Long orderId) {
        return orders.findById(orderId).map(order -> {
            if (!"PAID".equals(order.getPaymentStatus())) markPaid(order);
            return order;
        });
    }

    private void markPaid(CustomerOrder order) {
        order.setPaymentStatus("PAID");
        order.setPaidAt(java.time.LocalDateTime.now());
        orders.save(order);
        invoices.remove(order.getId());
        mail.sendConfirmation(order);
    }

    /** Нэвтэрсэн хэрэглэгчийн өөрийнх нь захиалга (өөр хүнийх бол хоосон) */
    private Optional<CustomerOrder> ownOrder(Long orderId, HttpServletRequest req) {
        Claims claims = (Claims) req.getAttribute(AdminInterceptor.CLAIMS_ATTR);
        String email = claims.get("username", String.class);
        return orders.findById(orderId)
                .filter(o -> email.equalsIgnoreCase(o.getCustomer().getEmail()));
    }

    // Өөр хүний захиалга байвал "олдсонгүй" гэж хариулна (байгаа эсэхийг ч задруулахгүй)
    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(404).body(Map.of("message", "Захиалга олдсонгүй"));
    }

    private static String validate(CheckoutBody b) {
        if (b == null || b.items() == null || b.items().isEmpty()) return "Сагс хоосон байна";
        for (Item i : b.items()) {
            if (i == null || i.productId() == null || i.quantity() == null
                    || i.quantity() < 1 || i.quantity() > MAX_QUANTITY) {
                return "Барааны тоо буруу байна";
            }
        }
        if (!fits(b.name()) || !fits(b.phone()) || !fits(b.address()) || !fits(b.cityRegion())) {
            return "Хүргэлтийн мэдээллээ бүрэн бөглөнө үү (45 тэмдэгтээс ихгүй)";
        }
        if (b.paymentMethod() == null || !PAYMENT_METHODS.contains(b.paymentMethod())) {
            return "Төлбөрийн хэлбэрээ сонгоно уу";
        }
        return null;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean fits(String s) {
        return s != null && !s.isBlank() && s.trim().length() <= 45;
    }
}
