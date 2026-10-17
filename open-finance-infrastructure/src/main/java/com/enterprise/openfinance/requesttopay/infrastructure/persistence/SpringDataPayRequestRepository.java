package com.enterprise.openfinance.requesttopay.infrastructure.persistence;

import com.enterprise.openfinance.requesttopay.infrastructure.persistence.entity.PayRequestJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataPayRequestRepository extends JpaRepository<PayRequestJpaEntity, String> {

    /** select ... for update: serialises accept/reject on one pay request across replicas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayRequestJpaEntity p where p.consentId = :consentId")
    Optional<PayRequestJpaEntity> findByIdForUpdate(@Param("consentId") String consentId);
}
