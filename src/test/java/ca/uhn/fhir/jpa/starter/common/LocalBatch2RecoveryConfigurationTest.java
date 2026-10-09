package ca.uhn.fhir.jpa.starter.common;

import ca.uhn.fhir.IHapiBootOrder;
import ca.uhn.fhir.batch2.api.IJobPersistence;
import ca.uhn.fhir.batch2.coordinator.JobDefinitionRegistry;
import ca.uhn.fhir.broker.impl.LinkedBlockingBrokerClient;
import ca.uhn.fhir.jpa.dao.data.IBatch2WorkChunkRepository;
import ca.uhn.fhir.jpa.dao.tx.NonTransactionalHapiTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LocalBatch2RecoveryConfigurationTest {
    @Test
    void runsOnlyOnceInItsOwningContextBeforeUnorderedNativeScheduling() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        IJobPersistence persistence = mock(IJobPersistence.class);
        LocalBatch2RecoveryConfiguration configuration = new LocalBatch2RecoveryConfiguration(context,
                mock(LinkedBlockingBrokerClient.class), persistence, mock(IBatch2WorkChunkRepository.class),
                new NonTransactionalHapiTransactionService(), mock(JobDefinitionRegistry.class));
        configuration.restoreLocalQueue(new ContextRefreshedEvent(mock(ApplicationContext.class)));
        verifyNoInteractions(persistence);
        configuration.restoreLocalQueue(new ContextRefreshedEvent(context));
        configuration.restoreLocalQueue(new ContextRefreshedEvent(context));
        verify(persistence, times(1)).fetchInstances(eq(100), eq(0), anySet());

        Order order = LocalBatch2RecoveryConfiguration.class.getMethod("restoreLocalQueue", ContextRefreshedEvent.class)
                .getAnnotation(Order.class);
        assertThat(order.value()).isGreaterThan(IHapiBootOrder.REGISTER_INTERCEPTORS).isLessThan(Integer.MAX_VALUE);
        ConditionalOnProperty optIn = LocalBatch2RecoveryConfiguration.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(optIn.name()).containsExactly("cognimark.batch2.single-node-local-queue");
        assertThat(optIn.matchIfMissing()).isFalse();
        assertThat(optIn.havingValue()).isEqualTo("true");
    }
}
