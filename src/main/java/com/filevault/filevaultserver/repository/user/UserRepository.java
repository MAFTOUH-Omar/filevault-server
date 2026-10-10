package com.filevault.filevaultserver.repository.user;

import com.filevault.filevaultserver.exception.user.UserNotFoundException;
import com.filevault.filevaultserver.models.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsrEmail(String usrEmail);

    boolean existsByUsrEmail(String usrEmail);

    List<User> findByRoles_RolId(Long rolId);

    /**
     * Reserves {@code bytes} of the user's storage in one atomic statement: the quota check and the
     * increment cannot be split by a concurrent upload, so two parallel requests can never both squeeze
     * under the limit. A user's quota is the largest {@code rol_storage_quota_bytes} among their roles;
     * a role with a NULL quota means unlimited, and a user with no role at all has none. Returns 0 when
     * the reservation was refused.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE usr_users u
               SET usr_storage_used_bytes = u.usr_storage_used_bytes + :bytes
             WHERE u.usr_id = :usrId
               AND (EXISTS (SELECT 1
                              FROM usr_rol ur
                              JOIN rol_roles r ON r.rol_id = ur.rol_id
                             WHERE ur.usr_id = u.usr_id
                               AND r.rol_storage_quota_bytes IS NULL)
                    OR u.usr_storage_used_bytes + :bytes <=
                       (SELECT COALESCE(MAX(r.rol_storage_quota_bytes), 0)
                          FROM usr_rol ur
                          JOIN rol_roles r ON r.rol_id = ur.rol_id
                         WHERE ur.usr_id = u.usr_id))
            """, nativeQuery = true)
    int reserveStorage(@Param("usrId") UUID usrId, @Param("bytes") long bytes);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE usr_users
               SET usr_storage_used_bytes = GREATEST(usr_storage_used_bytes - :bytes, 0)
             WHERE usr_id = :usrId
            """, nativeQuery = true)
    int releaseStorage(@Param("usrId") UUID usrId, @Param("bytes") long bytes);

    default User getOrThrow(UUID usrId) {
        return findById(usrId).orElseThrow(() -> new UserNotFoundException(usrId));
    }
}
