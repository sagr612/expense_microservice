package sagar.userService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sagar.userService.entities.OutboxEvent;

import java.util.List;

@Repository
public interface OutboxRepository  extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}
