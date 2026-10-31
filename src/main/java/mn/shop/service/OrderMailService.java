package mn.shop.service;

import mn.shop.model.Customer;
import mn.shop.model.CustomerOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Захиалга баталгаажсан тухай и-мэйл илгээнэ.
 * spring.mail.username тохируулаагүй бол чимээгүй алгасна. Илгээлт амжилтгүй болсон ч захиалгад нөлөөлөхгүй.
 */
@Service
public class OrderMailService {

    private static final Logger log = LoggerFactory.getLogger(OrderMailService.class);

    private final ObjectProvider<JavaMailSender> senders;
    private final String from;

    public OrderMailService(ObjectProvider<JavaMailSender> senders,
                            @Value("${spring.mail.username:}") String from) {
        this.senders = senders;
        this.from = from;
    }

    public void sendConfirmation(CustomerOrder order) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || from.isBlank()) return;

        Customer customer = order.getCustomer();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(customer.getEmail());
        message.setSubject("Таны захиалга баталгаажлаа #" + order.getConfirmationNumber());
        message.setText(body(order, customer));

        // Хэрэглэгчийн хариуг удаашруулахгүйн тулд тусад нь илгээнэ
        CompletableFuture.runAsync(() -> {
            try {
                sender.send(message);
            } catch (RuntimeException e) {
                log.error("Захиалгын и-мэйл илгээж чадсангүй: {}", order.getId(), e);
            }
        });
    }

    private static String body(CustomerOrder order, Customer customer) {
        NumberFormat money = NumberFormat.getIntegerInstance(Locale.US);
        return "Сайн байна уу, " + customer.getName() + ".\n\n"
                + "Таны захиалга баталгаажлаа.\n\n"
                + "Захиалгын дугаар: #" + order.getConfirmationNumber() + "\n"
                + "Нийт дүн: " + money.format(order.getAmount()) + "₮\n"
                + "Хүргэлтийн хаяг: " + customer.getCityRegion() + ", " + customer.getAddress() + "\n"
                + "Утас: " + customer.getPhone() + "\n\n"
                + "Манай дэлгүүрээс худалдан авалт хийсэнд баярлалаа.\n"
                + "SportShop";
    }
}
