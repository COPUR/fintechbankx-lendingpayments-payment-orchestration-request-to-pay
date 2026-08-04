package com.enterprise.openfinance.requesttopay.infrastructure.config;

import com.enterprise.openfinance.application.saga.*;
import com.enterprise.openfinance.application.saga.model.SagaExecution;
import com.enterprise.openfinance.application.saga.model.SagaId;
import com.enterprise.openfinance.application.saga.port.out.SagaEventPublisher;
import com.enterprise.openfinance.application.saga.port.out.SagaStateRepository;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides default NoOp stubs for Saga dependencies.
 * In a real application, these would be backed by actual infrastructure adapters.
 */
@Configuration
public class SagaStubConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SagaStateRepository sagaStateRepository() {
        return new SagaStateRepository() {
            private final java.util.Map<SagaId, SagaExecution> store = new ConcurrentHashMap<>();

            @Override
            public CompletableFuture<SagaExecution> saveSagaState(SagaExecution saga) {
                store.put(saga.getSagaId(), saga);
                return CompletableFuture.completedFuture(saga);
            }

            @Override
            public CompletableFuture<Optional<SagaExecution>> loadSagaState(SagaId sagaId) {
                return CompletableFuture.completedFuture(Optional.ofNullable(store.get(sagaId)));
            }

            @Override
            public CompletableFuture<List<SagaExecution>> findInterruptedSagas() {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaEventPublisher sagaEventPublisher() {
        return Mockito.mock(SagaEventPublisher.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConsentValidationService consentValidationService() {
        return Mockito.mock(ConsentValidationService.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public ParticipantVerificationService participantVerificationService() {
        return Mockito.mock(ParticipantVerificationService.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public CBUAEIntegrationService cbuaeIntegrationService() {
        return Mockito.mock(CBUAEIntegrationService.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationService notificationService() {
        return Mockito.mock(NotificationService.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditService auditService() {
        return Mockito.mock(AuditService.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitingService rateLimitingService() {
        return Mockito.mock(RateLimitingService.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComplianceService complianceService() {
        return Mockito.mock(ComplianceService.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataAggregationService dataAggregationService() {
        return Mockito.mock(DataAggregationService.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataTransformationService dataTransformationService() {
        return Mockito.mock(DataTransformationService.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataEncryptionService dataEncryptionService() {
        return Mockito.mock(DataEncryptionService.class);
    }
}
