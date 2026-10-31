package mn.shop.config;

import mn.shop.model.Branch;
import mn.shop.repository.BranchRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Хүснэгт хоосон бол анхны салбаруудыг оруулна. Цаашид админ удирдана. */
@Component
public class BranchSeeder implements CommandLineRunner {

    private final BranchRepository branches;

    public BranchSeeder(BranchRepository branches) {
        this.branches = branches;
    }

    @Override
    public void run(String... args) {
        if (branches.count() > 0) return;
        add("Салбар 1", "Хан-Уул дүүрэг, Зайсан, Гэрэлт хотхон 17-5", "/images/branch-1.webp");
        add("Салбар 2", "Тара центр, 2 давхарт, 201 тоот", "/images/branch-2.webp");
        add("Салбар 3", "Хан-Уул дүүрэг, 19-р хороо, Зайсангийн гудамж, Богд хааны музейгээс урагш 200м, Sila Centre 2 давхарт", "/images/branch-3.webp");
        add("Салбар 4", "Сүхбаатар дүүрэг, Орос элчингийн баруун тал, 52-р байр", "/images/branch-4.webp");
    }

    private void add(String name, String address, String imageUrl) {
        Branch b = new Branch();
        b.setName(name);
        b.setAddress(address);
        b.setImageUrl(imageUrl);
        branches.save(b);
    }
}
