package mn.shop.controller;

import mn.shop.model.BankAccount;
import mn.shop.model.SiteSettings;
import mn.shop.repository.BankAccountRepository;
import mn.shop.repository.SiteSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;

// GET нь хүн бүрт нээлттэй, PUT нь зөвхөн ADMIN-д (AdminInterceptor)
@RestController
@RequestMapping("/api/settings")
public class SiteSettingsController {

    record BankBody(String code, String accountNumber, String accountName) {}
    record SettingsBody(String facebookUrl, String instagramUrl, String phone, String email, String address,
                        Long freeDeliveryOver, List<BankBody> bankAccounts) {}

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 \\-]{5,28}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    // Монгол IBAN: MN + 2 хяналтын орон + 16 орон (нийт 20 тэмдэгт)
    private static final Pattern MN_IBAN = Pattern.compile("^MN[0-9]{18}$");

    private final SiteSettingsRepository repository;
    private final BankAccountRepository banks;

    public SiteSettingsController(SiteSettingsRepository repository, BankAccountRepository banks) {
        this.repository = repository;
        this.banks = banks;
    }

    @GetMapping
    public Map<String, Object> get() {
        return response(repository.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettings::new));
    }

    @PutMapping
    @Transactional
    public ResponseEntity<?> update(@RequestBody SettingsBody body) {
        String facebook = clean(body.facebookUrl());
        String instagram = clean(body.instagramUrl());
        String phone = clean(body.phone());
        String email = clean(body.email());
        String address = clean(body.address());
        Long freeOver = body.freeDeliveryOver() == null || body.freeDeliveryOver() <= 0 ? null : body.freeDeliveryOver();

        String error = validate(facebook, "facebook.com", "Facebook");
        if (error == null) error = validate(instagram, "instagram.com", "Instagram");
        if (error == null && phone != null && !PHONE.matcher(phone).matches())
            error = "Утасны дугаар буруу байна (зөвхөн тоо, +, -, хоосон зай)";
        if (error == null && email != null && (email.length() > 120 || !EMAIL.matcher(email).matches()))
            error = "И-мэйл хаяг буруу байна";
        if (error == null && address != null && address.length() > 255)
            error = "Хаяг 255 тэмдэгтээс ихгүй байна";
        if (error == null && freeOver != null && freeOver > 1_000_000_000L)
            error = "Үнэгүй хүргэлтийн дүн хэт их байна";

        // Банкны данс: дугаар хоосон бол тухайн банкыг идэвхгүй болгоно
        List<BankAccount> accounts = new ArrayList<>();
        if (error == null && body.bankAccounts() != null) {
            for (BankBody b : body.bankAccounts()) {
                if (b == null || b.code() == null || !BankAccount.CODES.contains(b.code())) {
                    error = "Банкны код буруу байна";
                    break;
                }
                String raw = clean(b.accountNumber());
                String name = clean(b.accountName());
                if (raw == null) continue;
                // Зай, зураасыг арилгаж, том үсгээр хадгална: MN12 3456 ... → MN123456...
                String number = raw.replaceAll("[\\s\\-]", "").toUpperCase();
                if (!MN_IBAN.matcher(number).matches()) {
                    error = "IBAN буруу байна: MN-ээр эхэлж, араас нь 18 оронтой тоо байх ёстой (нийт 20 тэмдэгт)";
                    break;
                }
                if (!ibanChecksumOk(number)) {
                    error = "IBAN-ий хяналтын орон таарахгүй байна. Дугаараа дахин шалгана уу";
                    break;
                }
                if (name == null || name.length() > 120) {
                    error = "Данс эзэмшигчийн нэрээ оруулна уу (120 тэмдэгтээс ихгүй)";
                    break;
                }
                BankAccount account = new BankAccount();
                account.setCode(b.code());
                account.setAccountNumber(number);
                account.setAccountName(name);
                accounts.add(account);
            }
        }
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));

        SiteSettings settings = repository.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettings::new);
        settings.setFacebookUrl(facebook);
        settings.setInstagramUrl(instagram);
        settings.setPhone(phone);
        settings.setEmail(email);
        settings.setAddress(address);
        settings.setFreeDeliveryOver(freeOver);
        settings = repository.save(settings);

        // bankAccounts ирээгүй (хуучин frontend) бол дансанд хүрэхгүй
        if (body.bankAccounts() != null) {
            banks.deleteAll();
            banks.saveAll(accounts);
        }
        return ResponseEntity.ok(response(settings));
    }

    private Map<String, Object> response(SiteSettings s) {
        List<Map<String, String>> accounts = banks.findAll().stream()
                .map(b -> Map.of("code", b.getCode(), "accountNumber", b.getAccountNumber(), "accountName", b.getAccountName()))
                .toList();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("facebookUrl", s.getFacebookUrl());
        map.put("instagramUrl", s.getInstagramUrl());
        map.put("phone", s.getPhone());
        map.put("email", s.getEmail());
        map.put("address", s.getAddress());
        map.put("freeDeliveryOver", s.getFreeDeliveryOver());
        map.put("bankAccounts", accounts);
        return map;
    }

    /** ISO 13616: эхний 4 тэмдэгтийг ард нь шилжүүлж, үсгийг тоо (A=10 ... Z=35) болгоод mod 97 == 1 */
    static boolean ibanChecksumOk(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int mod = 0;
        for (char c : rearranged.toCharArray()) {
            int v = Character.isDigit(c) ? c - '0' : c - 'A' + 10;
            mod = (v > 9 ? mod * 100 + v : mod * 10 + v) % 97;
        }
        return mod == 1;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Хоосон бол icon нуугдана. Бөглөсөн бол https:// ба тухайн сайтын домэйнтэй байх ёстой.
    private static String validate(String url, String domain, String label) {
        if (url == null) return null;
        if (url.length() > 500) return label + " холбоос хэт урт байна";
        String u = url.toLowerCase();
        if (!u.startsWith("https://")) return label + " холбоос https://-ээр эхлэх ёстой";
        String host = u.substring(8).split("[/?#]", 2)[0];
        if (!host.equals(domain) && !host.endsWith("." + domain))
            return label + " холбоос " + domain + " хаяг байх ёстой";
        return null;
    }
}
