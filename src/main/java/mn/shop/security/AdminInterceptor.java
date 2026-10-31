package mn.shop.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    // Хандалтын түвшин:
    // PUBLIC - нэвтрээгүй хүн ч хандаж болно
    // USER   - нэвтэрсэн хэрэглэгч бүр хандаж болно
    // ADMIN  - зөвхөн админ хандаж болно
    private enum Access { PUBLIC, USER, ADMIN }

    // Нэвтэрсэн хэрэглэгч зөвхөн энэ замаар захиалга үүсгэнэ (CheckoutController)
    private static final Set<String> CHECKOUT_PATHS = Set.of("/api/checkout");
    // Өөрийн захиалгын төлбөр: /api/checkout/{orderId}/pay ба /qpay-invoice (эзэмшлийг controller шалгана)
    private static final Pattern PAY_PATH = Pattern.compile("^/api/checkout/\\d+/(pay|qpay-invoice)$");

    // Controller токены мэдээллийг (и-мэйл, эрх) эндээс уншина
    public static final String CLAIMS_ATTR = "jwtClaims";

    private final JwtService jwt;

    public AdminInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
            throws Exception {
        String method = req.getMethod();
        if (method.equals("OPTIONS")) return true; // CORS preflight

        // Энэ хүсэлтэд ямар эрх шаардлагатайг тодорхойлно
        Access access = required(method, req.getRequestURI());
        if (access == Access.PUBLIC) return true;

        // "Authorization: Bearer <токен>" толгойгоос JWT токеныг уншиж шалгана
        String header = req.getHeader("Authorization");
        Claims claims = (header != null && header.startsWith("Bearer "))
                ? jwt.parse(header.substring(7)) : null;

        if (claims == null) return reject(res, 401, "Нэвтэрнэ үү");
        if (access == Access.ADMIN && !"ADMIN".equals(claims.get("role", String.class))) {
            return reject(res, 403, "Админ эрх шаардлагатай");
        }
        req.setAttribute(CLAIMS_ATTR, claims);
        return true;
    }

    /** Тодорхой зөвшөөрөөгүй бүх хүсэлт ADMIN эрх шаардана (default deny). */
    private Access required(String method, String path) {
        boolean read = method.equals("GET") || method.equals("HEAD");

        // Бүтээгдэхүүн, ангилал, салбарыг хүн бүр унших боломжтой
        if (read && (path.startsWith("/api/products") || path.startsWith("/api/categories")
                || path.startsWith("/api/branches") || path.startsWith("/api/brands"))) {
            return Access.PUBLIC;
        }
        // Өөрийн захиалгууд (и-мэйлийг токеноос авна)
        if (read && path.equals("/api/my-orders")) return Access.USER;
        if (method.equals("POST") && (CHECKOUT_PATHS.contains(path) || PAY_PATH.matcher(path).matches())) {
            return Access.USER;
        }
        // Хэрэглэгч, харилцагч, захиалгын мэдээллийг унших, засах, устгах нь зөвхөн админд
        return Access.ADMIN;
    }

    private boolean reject(HttpServletResponse res, int status, String message) throws Exception {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
        return false;
    }
}
