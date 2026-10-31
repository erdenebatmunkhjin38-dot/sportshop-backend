package mn.shop.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 'not null' гэж давхар бичих шаардлагагүй, @Id өөрөө nullable=false байдаг
    @Column(name = "id", columnDefinition = "INT UNSIGNED")
    private Long id;

    @Column(name = "name", nullable = false, length = 45)
    private String name;

    @Column(name = "email", nullable = false, length = 45)
    private String email;

    @Column(name = "phone", nullable = false, length = 45)
    private String phone;

    @Column(name = "address", nullable = false, length = 45)
    private String address;

    // Өмнөх алдаа: length=2 байсныг 45 болгож засав.
    // Энэ нь "Data truncation: Data too long" алдааг шийднэ.
    @Column(name = "city_region", nullable = false, length = 45)
    private String cityRegion;

    // Картын дугаарыг API хариунд хэзээ ч буцаахгүй
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    // Картын дугаар цуглуулахгүй (аюулгүй байдал), хуучин өгөгдөлд л үлдсэн
    @Column(name = "cc_number", length = 19)
    private String ccNumber;
}