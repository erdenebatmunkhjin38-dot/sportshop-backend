package mn.shop.service;

import jakarta.mail.internet.MimeMessage;
import mn.shop.model.BankAccount;
import mn.shop.model.Customer;
import mn.shop.model.CustomerOrder;
import mn.shop.model.OrderedProduct;
import mn.shop.model.Product;
import mn.shop.model.SiteSettings;
import mn.shop.repository.BankAccountRepository;
import mn.shop.repository.OrderedProductRepository;
import mn.shop.repository.ProductRepository;
import mn.shop.repository.SiteSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Захиалгын HTML и-мэйлүүд: бүртгэгдсэн, төлбөр баталгаажсан, хүргэлтэд гарсан, хүргэгдсэн, цуцлагдсан.
 * spring.mail.username тохируулаагүй бол чимээгүй алгасна. Илгээлт амжилтгүй болсон ч захиалгад нөлөөлөхгүй.
 * Агуулгыг дуудсан урсгал (transaction) дотор бэлтгээд, илгээлтийг тусдаа урсгалд хийнэ.
 */
@Service
public class OrderMailService {

    private static final Logger log = LoggerFactory.getLogger(OrderMailService.class);
    private static final Map<String, String> BANK_NAMES = Map.of(
            "KHAN", "Хаан банк", "GOLOMT", "Голомт банк", "TDB", "Худалдаа хөгжлийн банк",
            "XAC", "Хас банк", "STATE", "Төрийн банк");

    /** И-мэйлд харуулах барааны мөр */
    public record Line(String name, String detail, int quantity, BigDecimal unitPrice) {}

    /** И-мэйлийн доод хэсэгт гарах дэлгүүрийн мэдээлэл */
    public record Shop(String phone, String email, String address, String url) {}

    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    private final String frontendUrl;
    private final SiteSettingsRepository settings;
    private final BankAccountRepository bankAccounts;
    private final OrderedProductRepository orderedProducts;
    private final ProductRepository products;

    public OrderMailService(ObjectProvider<JavaMailSender> senders,
                            @Value("${spring.mail.username:}") String from,
                            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
                            SiteSettingsRepository settings, BankAccountRepository bankAccounts,
                            OrderedProductRepository orderedProducts, ProductRepository products) {
        this.senders = senders;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.settings = settings;
        this.bankAccounts = bankAccounts;
        this.orderedProducts = orderedProducts;
        this.products = products;
    }

    /** Захиалга үүсэхэд: бараа, дүн, (дансаар бол) шилжүүлэх данс */
    public void sendPlaced(CustomerOrder order) {
        if (!enabled()) return;
        BankAccount bank = "QPAY".equals(order.getPaymentMethod()) ? null
                : bankAccounts.findById(order.getPaymentMethod()).orElse(null);
        String html = placedHtml(order, lines(order), bank, shop());
        send(order, "Таны #" + order.getConfirmationNumber() + " дугаартай захиалга бүртгэгдлээ", html);
    }

    /** Төлбөр баталгаажсан (QPay эсвэл админ) */
    public void sendConfirmation(CustomerOrder order) {
        if (!enabled()) return;
        String html = statusHtml(order, "Төлбөр баталгаажлаа",
                "Таны <b>#" + order.getConfirmationNumber() + "</b> дугаартай <b>" + money(order.getAmount())
                        + "</b> захиалгын төлбөр амжилттай <span style=\"color:#059669;font-weight:700\">ТӨЛӨГДЛӨӨ</span>. "
                        + "Бид захиалгыг бэлтгэж эхэллээ.", lines(order), shop());
        send(order, "#" + order.getConfirmationNumber() + " захиалгын төлбөр баталгаажлаа", html);
    }

    /** Админ төлөв өөрчлөхөд (зөвхөн хэрэглэгчид хэрэгтэй төлөвүүд) */
    public void sendStatus(CustomerOrder order, String status) {
        if (!enabled()) return;
        String title;
        String text;
        switch (status) {
            case "SHIPPED" -> {
                title = "Захиалга хүргэлтэд гарлаа";
                text = "Таны <b>#" + order.getConfirmationNumber() + "</b> дугаартай захиалга хүргэлтэд гарлаа. "
                        + "Хүргэлтийн ажилтан тантай <b>" + esc(order.getCustomer().getPhone()) + "</b> дугаараар холбогдоно.";
            }
            case "DELIVERED" -> {
                title = "Захиалга хүргэгдлээ";
                text = "Таны <b>#" + order.getConfirmationNumber() + "</b> дугаартай захиалга хүргэгдлээ. "
                        + "Манай дэлгүүрээс худалдан авалт хийсэнд баярлалаа!";
            }
            case "CANCELLED" -> {
                title = "Захиалга цуцлагдлаа";
                text = "Таны <b>#" + order.getConfirmationNumber() + "</b> дугаартай захиалга цуцлагдлаа. "
                        + "Төлбөр төлсөн бол бид тантай холбогдож буцаан олгоно.";
            }
            default -> { return; }
        }
        send(order, "#" + order.getConfirmationNumber() + " — " + title, statusHtml(order, title, text, null, shop()));
    }

    // ---------- агуулга ----------

    private List<Line> lines(CustomerOrder order) {
        List<Line> result = new ArrayList<>();
        for (OrderedProduct op : orderedProducts.findByCustomerOrderId(order.getId())) {
            // Нэг transaction дотор хадгалсан мөрд product холбоос хоосон байж болно
            String name = Optional.ofNullable(op.getProduct()).map(Product::getName)
                    .or(() -> products.findById(op.getProductId()).map(Product::getName)).orElse("Бараа");
            BigDecimal price = op.getUnitPrice() != null ? op.getUnitPrice() : BigDecimal.ZERO;
            result.add(new Line(name, op.getSizes(), op.getQuantity(), price));
        }
        return result;
    }

    private Shop shop() {
        SiteSettings s = settings.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettings::new);
        return new Shop(s.getPhone(), s.getEmail(), s.getAddress(), frontendUrl);
    }

    static String placedHtml(CustomerOrder order, List<Line> lines, BankAccount bank, Shop shop) {
        Customer c = order.getCustomer();
        StringBuilder body = new StringBuilder();
        body.append(p("Сайн байна уу, <b>" + esc(c.getName()) + "</b>."));
        body.append(p("Таны <b style=\"color:#ea580c\">#" + order.getConfirmationNumber() + "</b> дугаартай <b style=\"color:#ea580c\">"
                + money(order.getAmount()) + "</b> захиалга амжилттай <b style=\"color:#ea580c\">БҮРТГЭГДЛЭЭ</b>."));
        body.append(itemsTable(lines, order.getDeliveryFee(), order.getAmount()));

        if (bank != null) {
            body.append("<div style=\"margin:20px 0;padding:16px;border-radius:12px;background:#fff7ed;border:1px solid #fed7aa\">")
                    .append("<p style=\"margin:0 0 8px;font-weight:700;color:#9a3412\">Төлбөрөө доорх дансанд шилжүүлнэ үү</p>")
                    .append(kv("Банк", BANK_NAMES.getOrDefault(bank.getCode(), esc(bank.getCode()))))
                    .append(kv("IBAN", "<span style=\"font-family:monospace\">" + formatIban(bank.getAccountNumber()) + "</span>"))
                    .append(kv("Данс эзэмшигч", esc(bank.getAccountName())))
                    .append(kv("Дүн", "<b>" + money(order.getAmount()) + "</b>"))
                    .append(kv("Гүйлгээний утга", "<b style=\"color:#9a3412\">" + order.getConfirmationNumber() + "</b>"))
                    .append("<p style=\"margin:8px 0 0;font-size:12px;color:#9a3412\">Гүйлгээний утгад захиалгын дугаараа заавал бичнэ үү. "
                            + "Төлбөр орсныг шалгаад захиалгыг баталгаажуулна.</p></div>");
        }

        body.append("<p style=\"margin:20px 0 4px;font-weight:700\">Хүргэлтийн хаяг</p>")
                .append(p(esc(c.getName()) + ", " + esc(c.getPhone()) + "<br>" + esc(c.getCityRegion()) + ", " + esc(c.getAddress())));
        return layout("Захиалга бүртгэгдлээ", body.toString(), shop);
    }

    static String statusHtml(CustomerOrder order, String title, String text, List<Line> lines, Shop shop) {
        StringBuilder body = new StringBuilder();
        body.append(p("Сайн байна уу, <b>" + esc(order.getCustomer().getName()) + "</b>."));
        body.append(p(text));
        if (lines != null) body.append(itemsTable(lines, order.getDeliveryFee(), order.getAmount()));
        return layout(title, body.toString(), shop);
    }

    private static String itemsTable(List<Line> lines, BigDecimal deliveryFee, BigDecimal total) {
        String td = "padding:8px 0;border-bottom:1px solid #f1f5f9;";
        StringBuilder t = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" "
                + "style=\"margin:16px 0;border-collapse:collapse;font-size:14px\">");
        for (Line l : lines) {
            t.append("<tr><td style=\"").append(td).append("\">").append(esc(l.name()));
            if (l.detail() != null && !l.detail().isBlank())
                t.append("<br><span style=\"font-size:12px;color:#64748b\">").append(esc(l.detail())).append("</span>");
            t.append("</td><td style=\"").append(td).append("text-align:center;color:#64748b;white-space:nowrap\">× ").append(l.quantity())
                    .append("</td><td style=\"").append(td).append("text-align:right;white-space:nowrap\">")
                    .append(money(l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))).append("</td></tr>");
        }
        BigDecimal fee = deliveryFee == null ? BigDecimal.ZERO : deliveryFee;
        t.append("<tr><td colspan=\"2\" style=\"padding:8px 0;color:#64748b\">Хүргэлт</td><td style=\"padding:8px 0;text-align:right\">")
                .append(fee.signum() == 0 ? "Үнэгүй" : money(fee)).append("</td></tr>")
                .append("<tr><td colspan=\"2\" style=\"padding:10px 0;font-weight:700;border-top:2px solid #0f172a\">Нийт</td>")
                .append("<td style=\"padding:10px 0;text-align:right;font-weight:800;font-size:16px;border-top:2px solid #0f172a\">")
                .append(money(total)).append("</td></tr></table>");
        return t.toString();
    }

    private static String layout(String title, String body, Shop shop) {
        StringBuilder contact = new StringBuilder();
        if (shop.phone() != null) contact.append("Утас: ").append(esc(shop.phone())).append("<br>");
        if (shop.email() != null) contact.append("И-мэйл: <a href=\"mailto:").append(esc(shop.email())).append("\" style=\"color:#ea580c\">")
                .append(esc(shop.email())).append("</a><br>");
        if (shop.address() != null) contact.append("Хаяг: ").append(esc(shop.address()));

        return "<!doctype html><html><head><meta charset=\"utf-8\"></head>"
                + "<body style=\"margin:0;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;color:#0f172a\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"><tr><td align=\"center\" style=\"padding:24px 12px\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden\">"
                + "<tr><td style=\"background:#0f172a;padding:20px 28px;font-size:22px;font-weight:800;color:#ffffff\">Sport<span style=\"color:#f97316\">Shop</span></td></tr>"
                + "<tr><td style=\"padding:28px\">"
                + "<h1 style=\"margin:0 0 16px;font-size:20px\">" + esc(title) + "</h1>"
                + body
                + "<p style=\"margin:24px 0 0\"><a href=\"" + esc(shop.url()) + "/my-orders\" "
                + "style=\"display:inline-block;background:#0f172a;color:#ffffff;text-decoration:none;font-weight:700;padding:12px 22px;border-radius:10px\">"
                + "Захиалгаа хянах</a></p>"
                + "</td></tr>"
                + (contact.isEmpty() ? "" : "<tr><td style=\"padding:20px 28px;background:#f8fafc;font-size:13px;line-height:1.6;color:#475569\">"
                    + "<b style=\"color:#0f172a\">Холбоо барих</b><br>" + contact + "</td></tr>")
                + "</table>"
                + "<p style=\"margin:16px 0 0;font-size:12px;color:#94a3b8\">Энэхүү и-мэйлийг систем автоматаар илгээсэн. Хариу бичих шаардлагагүй.</p>"
                + "</td></tr></table></body></html>";
    }

    private static String p(String html) {
        return "<p style=\"margin:0 0 12px;font-size:15px;line-height:1.6\">" + html + "</p>";
    }

    private static String kv(String key, String valueHtml) {
        return "<p style=\"margin:4px 0;font-size:14px\"><span style=\"color:#9a3412\">" + key + ":</span> " + valueHtml + "</p>";
    }

    private static String money(BigDecimal n) {
        return NumberFormat.getIntegerInstance(Locale.US).format(n == null ? BigDecimal.ZERO : n) + "₮";
    }

    private static String formatIban(String iban) {
        return esc(iban).replaceAll("(.{4})(?=.)", "$1 ");
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ---------- илгээх ----------

    private boolean enabled() {
        return senders.getIfAvailable() != null && !from.isBlank();
    }

    private void send(CustomerOrder order, String subject, String html) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || from.isBlank()) return;
        String to = order.getCustomer().getEmail();
        // Хэрэглэгчийн хариуг удаашруулахгүйн тулд тусад нь илгээнэ
        CompletableFuture.runAsync(() -> {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(from, "SportShop");
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(html, true);
                sender.send(message);
            } catch (Exception e) {
                log.error("Захиалгын и-мэйл илгээж чадсангүй: {} ({})", order.getId(), subject, e);
            }
        });
    }
}
