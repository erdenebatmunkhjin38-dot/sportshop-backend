package mn.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Сайтын нийтлэг тохиргоо (сошиал линк гэх мэт). Ганцхан мөр (id = 1) хадгална.
@Getter
@Setter
@Entity
@Table(name = "site_settings")
public class SiteSettings {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "facebook_url", length = 500)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    // Холбоо барих мэдээлэл (дээд мөр, footer, /contact хуудсанд гарна)
    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "address", length = 255)
    private String address;

    // Барааны дүн үүнээс их буюу тэнцүү бол хүргэлт үнэгүй. null бол үнэгүй хүргэлт байхгүй.
    @Column(name = "free_delivery_over")
    private Long freeDeliveryOver;
}
