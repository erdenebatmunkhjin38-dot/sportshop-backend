package mn.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// Нууц үг сэргээх нэг удаагийн токен. Токенийг өөрийг нь биш SHA-256 hash-ийг хадгална.
@Getter
@Setter
@Entity
@Table(name = "password_reset_token", indexes = @Index(columnList = "token_hash", unique = true))
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used;
}
