package com.enterprise.openfinance.requesttopay.infrastructure.persistence;

import com.enterprise.openfinance.requesttopay.infrastructure.persistence.entity.PayRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataPayRequestRepository extends JpaRepository<PayRequestJpaEntity, String> {
}
