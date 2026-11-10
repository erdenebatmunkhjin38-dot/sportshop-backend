package mn.shop.repository;

import mn.shop.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    // Шинэ токен үүсэх эсвэл нууц үг солигдоход хуучныг нь хүчингүй болгоно
    @Modifying
    @Transactional
    @Query("delete from PasswordResetToken t where t.userId = :userId")
    void deleteByUserId(Long userId);
}
