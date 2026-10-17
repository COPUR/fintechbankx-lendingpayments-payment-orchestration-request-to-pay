package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Pending rows in insertion order, except rows of an aggregate that has
     * an earlier PARKED row: a parked event keeps its aggregate's later events
     * pending until an operator replays it, so consumers never see them out
     * of order. Other aggregates keep flowing.
     */
    @Query(value = """
            select o.* from outbox_event o
            where o.status = 'PENDING'
              and not exists (
                  select 1 from outbox_event p
                  where p.status = 'PARKED'
                    and p.aggregate_id = o.aggregate_id
                    and p.created_seq < o.created_seq)
            order by o.created_seq
            limit :batchSize
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> findPendingBatch(@Param("batchSize") int batchSize);

    @Modifying
    @Query("delete from OutboxEventJpaEntity e where e.status = com.enterprise.openfinance.requesttopay.infrastructure"
            + ".outbox.OutboxEventJpaEntity.Status.PUBLISHED and e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);

    /** Parked rows not yet counted in outbox_parked_events_total: operator parks done in SQL. */
    @Query("select e from OutboxEventJpaEntity e where e.status = com.enterprise.openfinance.requesttopay.infrastructure"
            + ".outbox.OutboxEventJpaEntity.Status.PARKED and e.parkCounted = false")
    List<OutboxEventJpaEntity> findUncountedParks();

    long countByStatus(OutboxEventJpaEntity.Status status);

    @Query(value = "select min(occurred_at) from outbox_event where status = 'PENDING'", nativeQuery = true)
    Optional<Instant> oldestPendingOccurredAt();
}
