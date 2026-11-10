package mn.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Дансаар шилжүүлэх үед харагдах банкны данс. Банк бүрт нэг (code = KHAN, XAC ...).
// Данс оруулаагүй банкаар төлөх боломжгүй.
@Getter
@Setter
@Entity
@Table(name = "bank_account")
public class BankAccount {
    // Дэмжигдэх банкууд. frontend-ийн app/lib/payment.ts BANKS-тэй ижил байх ёстой.
    public static final java.util.Set<String> CODES = java.util.Set.of("KHAN", "GOLOMT", "TDB", "XAC", "STATE");

    @Id
    @Column(name = "code", length = 10)
    private String code;

    @Column(name = "account_number", nullable = false, length = 40)
    private String accountNumber;

    @Column(name = "account_name", nullable = false, length = 120)
    private String accountName;
}
