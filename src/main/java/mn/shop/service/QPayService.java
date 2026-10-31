package mn.shop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * QPay Merchant API v2 холболт: нэхэмжлэл (QR) үүсгэх, төлбөр орсон эсэхийг шалгах.
 * <p>
 * application-local.properties-д app.qpay.username/password/invoice-code-ийг QPay-ээс
 * авсан утгаар бөглөвөл идэвхжинэ. Хоосон бол {@link #isEnabled()} false буцаах ба
 * систем демо горимоор ажиллана.
 */
@Service
public class QPayService {

    private static final Logger log = LoggerFactory.getLogger(QPayService.class);

    /** Frontend-д харуулах нэхэмжлэлийн мэдээлэл */
    public record Invoice(String invoiceId, String qrImage, String qrText, String shortUrl,
                          List<Map<String, Object>> bankApps) {}

    private final String username;
    private final String password;
    private final String invoiceCode;
    private final String callbackUrl;
    private final RestClient http;

    // Access token-ийг дуусах хүртэл нь дахин ашиглана
    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public QPayService(@Value("${app.qpay.base-url:https://merchant.qpay.mn}") String baseUrl,
                       @Value("${app.qpay.username:}") String username,
                       @Value("${app.qpay.password:}") String password,
                       @Value("${app.qpay.invoice-code:}") String invoiceCode,
                       @Value("${app.qpay.callback-url:}") String callbackUrl) {
        this.username = username;
        this.password = password;
        this.invoiceCode = invoiceCode;
        this.callbackUrl = callbackUrl;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        if (isEnabled()) log.info("QPay идэвхтэй: {}", baseUrl);
        else log.info("QPay тохируулаагүй тул демо горимоор ажиллана");
    }

    public boolean isEnabled() {
        return !username.isBlank() && !password.isBlank() && !invoiceCode.isBlank();
    }

    /** POST /v2/invoice — захиалгын дүнгээр шинэ нэхэмжлэл үүсгэнэ */
    @SuppressWarnings("unchecked")
    public Invoice createInvoice(long orderId, long confirmationNumber, BigDecimal amount, String receiver) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoice_code", invoiceCode);
        // Давхардахгүй дугаар (нэг захиалгад шинэ нэхэмжлэл үүсгэж болох тул цаг нэмнэ)
        body.put("sender_invoice_no", confirmationNumber + "-" + System.currentTimeMillis());
        body.put("invoice_receiver_code", receiver);
        body.put("invoice_description", "ShopMN захиалга #" + confirmationNumber);
        body.put("amount", amount);
        body.put("callback_url", callbackUrl.isBlank()
                ? "http://localhost/api/qpay/callback?order=" + orderId
                : callbackUrl + "?order=" + orderId);

        Map<String, Object> res = http.post().uri("/v2/invoice")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (res == null || res.get("invoice_id") == null) {
            throw new IllegalStateException("QPay нэхэмжлэл үүсгэж чадсангүй");
        }
        List<Map<String, Object>> urls = res.get("urls") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        return new Invoice(
                String.valueOf(res.get("invoice_id")),
                (String) res.get("qr_image"),
                (String) res.get("qr_text"),
                (String) res.get("qPay_shortUrl"),
                urls);
    }

    /** POST /v2/payment/check — нэхэмжлэлийн төлбөр бүрэн орсон эсэх */
    @SuppressWarnings("unchecked")
    public boolean isPaid(String invoiceId, BigDecimal amount) {
        Map<String, Object> body = Map.of(
                "object_type", "INVOICE",
                "object_id", invoiceId,
                "offset", Map.of("page_number", 1, "page_limit", 100));
        try {
            Map<String, Object> res = http.post().uri("/v2/payment/check")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (res == null) return false;

            // PAID төлөвтэй гүйлгээнүүдийн нийлбэр захиалгын дүнд хүрсэн эсэх
            BigDecimal paid = BigDecimal.ZERO;
            if (res.get("rows") instanceof List<?> rows) {
                for (Object o : rows) {
                    Map<String, Object> row = (Map<String, Object>) o;
                    if ("PAID".equals(row.get("payment_status")) && row.get("payment_amount") != null) {
                        paid = paid.add(new BigDecimal(String.valueOf(row.get("payment_amount"))));
                    }
                }
            }
            return paid.compareTo(amount) >= 0;
        } catch (RestClientException e) {
            log.warn("QPay төлбөр шалгахад алдаа: {}", e.getMessage());
            return false;
        }
    }

    /** POST /v2/auth/token (Basic auth) — token-ийг кэшлэнэ */
    @SuppressWarnings("unchecked")
    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) return accessToken;

        String basic = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> res = http.post().uri("/v2/auth/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .retrieve()
                .body(Map.class);
        if (res == null || res.get("access_token") == null) {
            throw new IllegalStateException("QPay token авч чадсангүй");
        }
        accessToken = (String) res.get("access_token");
        // QPay expires_in-ийг дуусах цаг (Unix секунд) хэлбэрээр өгдөг; хугацаа (секунд)
        // ирсэн тохиолдолд ч зөв ажиллана. Аюулгүйн үүднээс 60 секундын өмнө шинэчилнэ.
        long value = res.get("expires_in") instanceof Number n ? n.longValue() : 3600;
        Instant expires = value > 1_000_000_000L
                ? Instant.ofEpochSecond(value)
                : Instant.now().plusSeconds(value);
        tokenExpiresAt = expires.minusSeconds(60);
        return accessToken;
    }
}
