package com.filevault.filevaultserver.repository.user;

import com.filevault.filevaultserver.exception.user.UserNotFoundException;
import com.filevault.filevaultserver.models.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsrEmail(String usrEmail);

    boolean existsByUsrEmail(String usrEmail);

    default User getOrThrow(UUID usrId) {
        return findById(usrId).orElseThrow(() -> new UserNotFoundException(usrId));
    }
}
