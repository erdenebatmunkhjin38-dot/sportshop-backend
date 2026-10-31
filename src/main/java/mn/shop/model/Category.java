package mn.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "category")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "tinyint UNSIGNED not null")
    private Short id; // tinyint unsigned (0..255) тул Byte (-128..127) хүрэлцэхгүй

    @Column(name = "name", nullable = false, length = 45)
    private String name;

    // Эцэг ангилалын id. NULL бол үндсэн ангилал
    @Column(name = "parent_id", columnDefinition = "TINYINT UNSIGNED")
    private Short parentId;
}
