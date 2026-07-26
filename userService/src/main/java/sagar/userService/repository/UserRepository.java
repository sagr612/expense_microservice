package sagar.userService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import sagar.userService.entities.UserInfo;

import java.util.Optional;

@Repository
@EnableJpaRepositories
public interface UserRepository extends JpaRepository<UserInfo, String> {
    Optional<UserInfo> findByUserId(String userId);
    Optional<UserInfo> findByEmail(String email);
    Boolean existsByEmail(String email);
}
