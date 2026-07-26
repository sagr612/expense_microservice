package org.example.repository;

import jakarta.validation.constraints.NotNull;
import org.example.entities.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserInfo, String> {
    public UserInfo findByUsername(String username);

    public Boolean existsByUsername(String username);

    boolean existsByPhoneNumber(Long phoneNumber);

    boolean existsByEmail(String email);

    void deleteByUserId(String userId);
}
