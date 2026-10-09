package ca.uhn.fhir.jpa.starter.common;

import ca.uhn.fhir.IHapiBootOrder;
import ca.uhn.fhir.batch2.api.IJobPersistence;
import ca.uhn.fhir.batch2.coordinator.JobDefinitionRegistry;
import ca.uhn.fhir.broker.api.IBrokerClient;
import ca.uhn.fhir.jpa.batch2.LocalBatch2WorkRecovery;
import ca.uhn.fhir.jpa.dao.data.IBatch2WorkChunkRepository;
import ca.uhn.fhir.jpa.dao.tx.IHapiTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt in only when deployment enforces one HAPI process per database. */
@Configuration
@ConditionalOnProperty(name = "cognimark.batch2.single-node-local-queue", havingValue = "true")
public class LocalBatch2RecoveryConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(LocalBatch2RecoveryConfiguration.class);
    private final ApplicationContext context;
    private final IBrokerClient broker;
    private final LocalBatch2WorkRecovery recovery;
    private final AtomicBoolean started = new AtomicBoolean();

    public LocalBatch2RecoveryConfiguration(ApplicationContext context, IBrokerClient broker,
            IJobPersistence persistence, IBatch2WorkChunkRepository chunks,
            IHapiTransactionService transactions, JobDefinitionRegistry definitions) {
        this.context = context;
        this.broker = broker;
        this.recovery = new LocalBatch2WorkRecovery(persistence, chunks, transactions, definitions);
    }

    @EventListener(ContextRefreshedEvent.class)
    @Order(IHapiBootOrder.AFTER_SUBSCRIPTION_INITIALIZED + 1)
    public void restoreLocalQueue(ContextRefreshedEvent event) {
        if (event.getApplicationContext() != context || !started.compareAndSet(false, true)) {
            return;
        }
        int recovered = recovery.recoverBeforeScheduling(broker, new Date(context.getStartupDate()));
        LOG.info("Restored dispatchability for {} interrupted local Batch2 work chunks", recovered);
    }
}
