package com.caseflow.ai.persistence.repository;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobStatus;
import com.caseflow.ai.persistence.entity.IngestionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, String> {

    List<IngestionJob> findByEntityTypeAndEntityId(EntityType entityType, String entityId);

    Optional<IngestionJob> findTopByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            EntityType entityType, String entityId);

    List<IngestionJob> findByStatus(IngestionJobStatus status);

    Optional<IngestionJob> findByCorrelationId(String correlationId);

    List<IngestionJob> findByEntityTypeAndStatus(EntityType entityType, IngestionJobStatus status);
}
