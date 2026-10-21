package org.example.repository;

import org.example.entity.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    List<Outbox> findTop50ByPublishedFalseOrderByCreatedAtAsc();

    @Transactional
    @Modifying
    @Query("update Outbox o set o.published = true where o.id = :id")
    int markPublished(@Param("id") UUID id);
}
