package mn.shop.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * QPay төлбөр орсны дараа энэ хаягийг дуудна (callback_url). Нэвтрэлтгүй, нийтэд нээлттэй.
 * Callback-ийн агуулгад итгэхгүй — QPay API-аас төлбөрийг дахин шалгаж байж баталгаажуулна.
 * Жич: QPay серверээс хандахын тулд backend интернэтэд нээлттэй байх ёстой (localhost дээр
 * ажиллахгүй). Тэр үед ч frontend хэдэн секунд тутам /pay-ийг дуудаж шалгадаг тул төлбөр баталгаажна.
 */
@RestController
@RequestMapping("/api/qpay/callback")
public class QpayCallbackController {

    private final CheckoutController checkout;

    public QpayCallbackController(CheckoutController checkout) {
        this.checkout = checkout;
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> callback(@RequestParam("order") Long orderId) {
        checkout.confirmFromCallback(orderId);
        return ResponseEntity.ok("SUCCESS");
    }
}
