package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.application.saga.model.AggregationId;
import com.enterprise.openfinance.application.saga.model.SagaExecution;
import com.enterprise.openfinance.application.saga.model.SagaId;
import com.enterprise.openfinance.domain.event.ConsentValidationResult;
import com.enterprise.openfinance.domain.event.RateLimitResult;
import com.enterprise.openfinance.domain.event.AggregatedData;
import com.enterprise.openfinance.domain.event.PlatformData;
import com.enterprise.openfinance.domain.event.TransformedData;
import com.enterprise.openfinance.domain.event.EncryptedData;
import com.enterprise.openfinance.domain.event.DataDeliveryResult;
import com.enterprise.openfinance.domain.event.DataSharingResult;
import com.enterprise.openfinance.domain.event.DataSharingStatus;
import com.enterprise.openfinance.domain.event.DataSharingRequest;
import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Saga orchestrator for data sharing request workflow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSharingRequestSaga {

    private final SagaOrchestrator sagaOrchestrator;
    private final ConsentValidationService consentValidationService;
    private final DataAggregationService dataAggregationService;
    private final DataTransformationService dataTransformationService;
    private final DataEncryptionService dataEncryptionService;
    private final AuditService auditService;
    private final RateLimitingService rateLimitingService;
    private final ComplianceService complianceService;

    @Transactional
    public CompletableFuture<DataSharingResult> orchestrateDataSharingRequest(
            DataSharingRequest request) {
        
        var sagaId = SagaId.generate();
        log.info("📊 Starting data sharing saga: {} for consent: {} participant: {}", 
            sagaId, request.getConsentId(), request.getParticipantId());

        return sagaOrchestrator.<DataSharingRequest>startSaga(sagaId, request)
            .thenCompose(saga -> executeStep1_ValidateConsent(saga, request))
            .thenCompose(saga -> executeStep2_CheckRateLimits(saga, request))
            .thenCompose(saga -> executeStep3_AggregateData(saga, request))
            .thenCompose(saga -> executeStep4_TransformData(saga, request))
            .thenCompose(saga -> executeStep5_EncryptData(saga, request))
            .thenCompose(saga -> executeStep6_DeliverData(saga, request))
            .thenCompose(saga -> executeStep7_RecordAuditTrail(saga, request))
            .thenApply(saga -> buildSuccessResult(saga, request))
            .exceptionally(throwable -> {
                log.error("❌ Data sharing saga failed: {}", sagaId, throwable);
                return handleSagaFailure(sagaId, request, throwable);
            });
    }

    private CompletableFuture<SagaExecution> executeStep1_ValidateConsent(
            SagaExecution saga, DataSharingRequest request) {
        
        return sagaOrchestrator.<ConsentValidationResult>executeStep(saga, "VALIDATE_CONSENT", () ->
            consentValidationService.validateConsentForDataAccess(
                request.getConsentId(),
                request.getParticipantId(),
                request.getRequestedScopes(),
                request.getCustomerId(),
                Instant.now()
            )
        ).thenCompose(validation -> {
            if (validation.isValid()) {
                log.debug("✅ Step 1 completed: Consent validated for data access {}", request.getConsentId());
                return CompletableFuture.completedFuture(saga.markStepCompleted("VALIDATE_CONSENT"));
            } else {
                var violations = validation.getViolations();
                var error = new SagaStepFailedException(
                    "Consent validation failed: " + violations,
                    "VALIDATE_CONSENT",
                    determineValidationErrorCode(violations)
                );
                return CompletableFuture.<SagaExecution>failedFuture(error);
            }
        });
    }

    private CompletableFuture<SagaExecution> executeStep2_CheckRateLimits(
            SagaExecution saga, DataSharingRequest request) {
        
        return sagaOrchestrator.<RateLimitResult>executeStep(saga, "CHECK_RATE_LIMITS", () ->
            rateLimitingService.checkRateLimit(
                request.getParticipantId(),
                request.getConsentId(),
                request.getRequestedScopes(),
                request.getDataSize()
            )
        ).thenCompose(rateLimitCheck -> {
            if (rateLimitCheck.isAllowed()) {
                log.debug("✅ Step 2 completed: Rate limits passed for participant {}", request.getParticipantId());
                
                saga.addCompensation("CHECK_RATE_LIMITS", () ->
                    rateLimitingService.restoreQuota(
                        request.getParticipantId(),
                        request.getRequestedScopes(),
                        request.getDataSize()
                    )
                );
                
                return CompletableFuture.completedFuture(saga.markStepCompleted("CHECK_RATE_LIMITS"));
            } else {
                var error = new SagaStepFailedException(
                    "Rate limit exceeded: " + rateLimitCheck.getErrorMessage(),
                    "CHECK_RATE_LIMITS",
                    "RATE_LIMIT_EXCEEDED"
                );
                return CompletableFuture.<SagaExecution>failedFuture(error);
            }
        });
    }

    private CompletableFuture<SagaExecution> executeStep3_AggregateData(
            SagaExecution saga, DataSharingRequest request) {
        
        return sagaOrchestrator.<AggregatedData>executeAsyncStep(saga, "AGGREGATE_DATA",
            Duration.ofMinutes(2),
            () -> aggregateDataFromAllPlatforms(request)
        ).thenCompose(aggregatedData -> {
            log.debug("✅ Step 3 completed: Data aggregated from {} sources", 
                aggregatedData.getSourceCount());
            
            saga.addContextData("aggregatedData", aggregatedData);
            
            saga.addCompensation("AGGREGATE_DATA", () ->
                dataAggregationService.cleanupAggregatedData(new AggregationId(aggregatedData.getAggregationId()))
            );
            
            return CompletableFuture.completedFuture(saga.markStepCompleted("AGGREGATE_DATA"));
        });
    }

    private CompletableFuture<SagaExecution> executeStep4_TransformData(
            SagaExecution saga, DataSharingRequest request) {
        
        var aggregatedData = (AggregatedData) saga.getContextData("aggregatedData");
        
        return sagaOrchestrator.<TransformedData>executeStep(saga, "TRANSFORM_DATA", () ->
            dataTransformationService.transformForParticipant(
                aggregatedData,
                request.getParticipantId(),
                request.getRequestedScopes(),
                request.getDataFormat(),
                request.getComplianceRequirements()
            )
        ).thenCompose(transformedData -> {
            log.debug("✅ Step 4 completed: Data transformed for participant {}", request.getParticipantId());
            
            saga.addContextData("transformedData", transformedData);
            
            saga.addCompensation("TRANSFORM_DATA", () ->
                dataTransformationService.cleanupTransformedData(transformedData.getTransformationId())
            );
            
            return CompletableFuture.completedFuture(saga.markStepCompleted("TRANSFORM_DATA"));
        });
    }

    private CompletableFuture<SagaExecution> executeStep5_EncryptData(
            SagaExecution saga, DataSharingRequest request) {
        
        var transformedData = (TransformedData) saga.getContextData("transformedData");
        
        return sagaOrchestrator.<EncryptedData>executeStep(saga, "ENCRYPT_DATA", () ->
            dataEncryptionService.encryptForParticipant(
                transformedData,
                request.getParticipantId(),
                request.getEncryptionMethod(),
                request.getParticipantPublicKey()
            )
        ).thenCompose(encryptedData -> {
            log.debug("✅ Step 5 completed: Data encrypted for secure transmission to {}", 
                request.getParticipantId());
            
            saga.addContextData("encryptedData", encryptedData);
            
            saga.addCompensation("ENCRYPT_DATA", () ->
                dataEncryptionService.securelyDeleteEncryptedData(encryptedData.getEncryptionId())
            );
            
            return CompletableFuture.completedFuture(saga.markStepCompleted("ENCRYPT_DATA"));
        });
    }

    private CompletableFuture<SagaExecution> executeStep6_DeliverData(
            SagaExecution saga, DataSharingRequest request) {
        
        var encryptedData = (EncryptedData) saga.getContextData("encryptedData");
        
        return sagaOrchestrator.<DataDeliveryResult>executeAsyncStep(saga, "DELIVER_DATA",
            Duration.ofMinutes(1),
            () -> dataAggregationService.deliverDataToParticipant(
                encryptedData,
                request.getParticipantId(),
                request.getDeliveryEndpoint(),
                request.getDeliveryMethod(),
                request.getCallbackUrl()
            )
        ).thenCompose(deliveryResult -> {
            if (deliveryResult.isSuccess()) {
                log.debug("✅ Step 6 completed: Data delivered successfully to participant {}", 
                    request.getParticipantId());
                
                saga.addContextData("deliveryResult", deliveryResult);
                
                return CompletableFuture.completedFuture(saga.markStepCompleted("DELIVER_DATA"));
            } else {
                var error = new SagaStepFailedException(
                    "Data delivery failed: " + deliveryResult.getErrorMessage(),
                    "DELIVER_DATA",
                    deliveryResult.getErrorCode()
                );
                return CompletableFuture.<SagaExecution>failedFuture(error);
            }
        });
    }

    private CompletableFuture<SagaExecution> executeStep7_RecordAuditTrail(
            SagaExecution saga, DataSharingRequest request) {
        
        var deliveryResult = (DataDeliveryResult) saga.getContextData("deliveryResult");
        var aggregatedData = (AggregatedData) saga.getContextData("aggregatedData");
        
        return sagaOrchestrator.<Void>executeStep(saga, "RECORD_AUDIT_TRAIL", () ->
            CompletableFuture.allOf(
                auditService.recordDataAccess(
                    request.getConsentId(),
                    request.getCustomerId(),
                    request.getParticipantId(),
                    request.getRequestedScopes(),
                    aggregatedData.getDataSources(),
                    deliveryResult.getDeliveryTimestamp(),
                    saga.getSagaId()
                ),
                
                complianceService.recordComplianceEvent(
                    "DATA_SHARED",
                    request.getConsentId(),
                    request.getParticipantId(),
                    Map.of(
                        "dataSize", aggregatedData.getDataSize(),
                        "encryptionMethod", request.getEncryptionMethod(),
                        "deliveryMethod", request.getDeliveryMethod(),
                        "sagaId", saga.getSagaId().getValue()
                    )
                ),
                
                consentValidationService.updateConsentUsage(
                    request.getConsentId(),
                    request.getRequestedScopes(),
                    aggregatedData.getDataSize(),
                    Instant.now()
                )
            )
        ).thenCompose(auditResults -> {
            log.debug("✅ Step 7 completed: Audit trail and compliance events recorded");
            return CompletableFuture.completedFuture(saga.markStepCompleted("RECORD_AUDIT_TRAIL"));
        });
    }

    private CompletableFuture<AggregatedData> aggregateDataFromAllPlatforms(DataSharingRequest request) {
        log.debug("🔄 Aggregating data from multiple platforms for consent: {}", request.getConsentId());

        var aggregationTasks = new ArrayList<CompletableFuture<PlatformData>>();

        if (requiresLoanData(request.getRequestedScopes())) {
            aggregationTasks.add(dataAggregationService.aggregateLoanData(request.getCustomerId(), request.getRequestedScopes()));
        }

        if (requiresIslamicFinanceData(request.getRequestedScopes())) {
            aggregationTasks.add(dataAggregationService.aggregateAmanahFiData(request.getCustomerId(), request.getRequestedScopes()));
        }

        if (requiresExpenseData(request.getRequestedScopes())) {
            aggregationTasks.add(dataAggregationService.aggregateMasrufiData(request.getCustomerId(), request.getRequestedScopes()));
        }

        if (requiresExternalData(request.getRequestedScopes())) {
            aggregationTasks.add(dataAggregationService.aggregateExternalData(request.getCustomerId(), request.getRequestedScopes(), request.getParticipantId()));
        }

        return CompletableFuture.allOf(aggregationTasks.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                var platformDataList = aggregationTasks.stream()
                    .map(CompletableFuture::join)
                    .toList();

                return AggregatedData.builder()
                    .aggregationId(AggregationId.generate().value())
                    .platformDataList(platformDataList)
                    .sourceCount(platformDataList.size())
                    .dataSize(platformDataList.stream().mapToLong(PlatformData::getDataSize).sum())
                    .dataSources(platformDataList.stream()
                        .map(PlatformData::getSourcePlatform)
                        .toList())
                    .aggregatedAt(Instant.now())
                    .build();
            });
    }

    private DataSharingResult buildSuccessResult(SagaExecution saga, DataSharingRequest request) {
        var deliveryResult = (DataDeliveryResult) saga.getContextData("deliveryResult");
        var aggregatedData = (AggregatedData) saga.getContextData("aggregatedData");

        log.info("🎉 Data sharing saga completed successfully: {} for consent: {}", 
            saga.getSagaId(), request.getConsentId());

        return DataSharingResult.builder()
            .sagaId(saga.getSagaId().getValue())
            .requestId(request.getRequestId())
            .consentId(request.getConsentId())
            .customerId(request.getCustomerId())
            .participantId(request.getParticipantId())
            .status(DataSharingStatus.COMPLETED)
            .sharedAt(deliveryResult.getDeliveryTimestamp())
            .dataSources(aggregatedData.getDataSources())
            .dataSize(aggregatedData.getDataSize())
            .encryptionMethod(request.getEncryptionMethod())
            .deliveryMethod(request.getDeliveryMethod())
            .executionTimeMs(saga.getExecutionTime().toMillis())
            .build();
    }

    private DataSharingResult handleSagaFailure(SagaId sagaId, 
                                               DataSharingRequest request, 
                                               Throwable throwable) {
        log.error("💥 Executing compensations for failed data sharing saga: {}", sagaId);
        
        sagaOrchestrator.executeCompensations(sagaId)
            .whenComplete((result, compensationError) -> {
                if (compensationError != null) {
                    log.error("❌ Compensation failed for data sharing saga: {}", sagaId, compensationError);
                    auditService.recordSagaCompensationFailed(sagaId, request.getConsentId(), compensationError);
                } else {
                    log.info("✅ Data sharing compensations completed successfully for saga: {}", sagaId);
                    auditService.recordSagaCompensationCompleted(sagaId, request.getConsentId());
                }
            });

        var failureReason = extractFailureReason(throwable);
        var status = determineFailureStatus(throwable);

        return DataSharingResult.builder()
            .sagaId(sagaId.getValue())
            .requestId(request.getRequestId())
            .consentId(request.getConsentId())
            .customerId(request.getCustomerId())
            .participantId(request.getParticipantId())
            .status(status)
            .failureReason(failureReason)
            .failedAt(Instant.now())
            .build();
    }

    private boolean requiresLoanData(Set<ConsentScope> scopes) {
        return scopes.contains(ConsentScope.ACCOUNT_INFORMATION) || 
               scopes.contains(ConsentScope.LOAN_INFORMATION);
    }

    private boolean requiresIslamicFinanceData(Set<ConsentScope> scopes) {
        return scopes.contains(ConsentScope.ISLAMIC_FINANCE) || 
               scopes.contains(ConsentScope.SHARIA_COMPLIANCE);
    }

    private boolean requiresExpenseData(Set<ConsentScope> scopes) {
        return scopes.contains(ConsentScope.TRANSACTION_HISTORY) || 
               scopes.contains(ConsentScope.SPENDING_ANALYSIS);
    }

    private boolean requiresExternalData(Set<ConsentScope> scopes) {
        return scopes.contains(ConsentScope.EXTERNAL_ACCOUNTS) || 
               scopes.contains(ConsentScope.THIRD_PARTY_DATA);
    }

    private String determineValidationErrorCode(Set<String> violations) {
        if (violations != null && violations.contains("CONSENT_EXPIRED")) {
            return "CONSENT_EXPIRED";
        } else if (violations != null && violations.contains("SCOPE_NOT_PERMITTED")) {
            return "INSUFFICIENT_SCOPE";
        } else if (violations != null && violations.contains("CONSENT_REVOKED")) {
            return "CONSENT_REVOKED";
        } else {
            return "VALIDATION_FAILED";
        }
    }

    private String extractFailureReason(Throwable throwable) {
        if (throwable instanceof SagaStepTimeoutException) {
            return "Data aggregation or delivery timeout: " + throwable.getMessage();
        } else if (throwable instanceof SagaStepFailedException) {
            var stepException = (SagaStepFailedException) throwable;
            return "Step failed [" + stepException.getStepName() + "]: " + stepException.getMessage();
        } else {
            return "Unexpected error: " + throwable.getMessage();
        }
    }

    private DataSharingStatus determineFailureStatus(Throwable throwable) {
        if (throwable instanceof SagaStepTimeoutException) {
            return DataSharingStatus.TIMEOUT;
        } else if (throwable instanceof SagaStepFailedException) {
            var stepException = (SagaStepFailedException) throwable;
            return switch (stepException.getErrorCode()) {
                case "CONSENT_EXPIRED", "CONSENT_REVOKED" -> DataSharingStatus.UNAUTHORIZED;
                case "INSUFFICIENT_SCOPE" -> DataSharingStatus.FORBIDDEN;
                case "RATE_LIMIT_EXCEEDED" -> DataSharingStatus.RATE_LIMITED;
                default -> DataSharingStatus.FAILED;
            };
        } else {
            return DataSharingStatus.FAILED;
        }
    }
}
