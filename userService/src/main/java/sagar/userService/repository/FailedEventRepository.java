package sagar.userService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sagar.userService.entities.FailedEvents;

public interface FailedEventRepository extends JpaRepository<FailedEvents, Long> {

}
