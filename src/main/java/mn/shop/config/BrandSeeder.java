package mn.shop.config;

import mn.shop.model.Brand;
import mn.shop.repository.BrandRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Хүснэгт хоосон бол анхны брэндүүдийг оруулна. Цаашид админ удирдана. */
@Component
public class BrandSeeder implements CommandLineRunner {

    private final BrandRepository brands;

    public BrandSeeder(BrandRepository brands) {
        this.brands = brands;
    }

    @Override
    public void run(String... args) {
        if (brands.count() > 0) return;
        Map<String, String> initial = new LinkedHashMap<>();
        initial.put("Nike", "/brands/nike.svg");
        initial.put("Jordan", "/brands/jordan.svg");
        initial.put("Adidas", "/brands/adidas.svg");
        initial.put("Puma", "/brands/puma.svg");
        initial.put("Under Armour", "/brands/underarmour.svg");
        initial.put("New Balance", "/brands/newbalance.svg");
        initial.put("Reebok", "/brands/reebok.svg");
        initial.put("Asics", null);
        initial.put("Mizuno", null);
        initial.put("Anta", "/brands/anta.svg");
        initial.put("Li-Ning", "/brands/lining.svg");
        initial.put("Yonex", null);
        initial.put("Wilson", null);
        initial.put("Spalding", null);
        initial.put("Molten", null);
        initial.put("Mikasa", null);
        initial.forEach((name, logo) -> {
            Brand b = new Brand();
            b.setName(name);
            b.setLogoUrl(logo);
            brands.save(b);
        });
    }
}
