package mn.shop.repository;

import mn.shop.model.UserRole;
import mn.shop.model.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query(value = """
        SELECT r.name FROM user_roles ur
        JOIN role r ON r.id = ur.role_id
        WHERE ur.user_id = :userId
        """, nativeQuery = true)
    List<String> findRoleNames(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO user_roles (user_id, role_id)
        SELECT :userId, r.id FROM role r WHERE r.name = :roleName
        """, nativeQuery = true)
    void assign(@Param("userId") Long userId, @Param("roleName") String roleName);
}