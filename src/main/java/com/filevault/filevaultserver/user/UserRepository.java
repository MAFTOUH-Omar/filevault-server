package com.filevault.filevaultserver.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsrEmail(String usrEmail);

    boolean existsByUsrEmail(String usrEmail);
}
