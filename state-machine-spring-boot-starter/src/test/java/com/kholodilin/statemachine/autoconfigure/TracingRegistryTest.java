package com.kholodilin.statemachine.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TracingRegistryTest {

    @Test
    void disabledTracingUsesNoop() {
        StateMachineProperties properties = new StateMachineProperties();
        properties.getObservability().getTracing().setEnabled(false);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObservationRegistry> provider = mock(ObjectProvider.class);

        assertThat(StateMachineAutoConfiguration.tracingRegistry(provider, properties))
                .isSameAs(ObservationRegistry.NOOP);
    }

    @Test
    void enabledTracingUsesProvidedRegistry() {
        StateMachineProperties properties = new StateMachineProperties();
        ObservationRegistry registry = ObservationRegistry.create();
        @SuppressWarnings("unchecked")
        ObjectProvider<ObservationRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(registry);

        assertThat(StateMachineAutoConfiguration.tracingRegistry(provider, properties))
                .isSameAs(registry);
    }

    @Test
    void enabledTracingFallsBackToNoopWhenMissing() {
        StateMachineProperties properties = new StateMachineProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<ObservationRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenAnswer(invocation -> invocation
                .getArgument(0, java.util.function.Supplier.class)
                .get());

        assertThat(StateMachineAutoConfiguration.tracingRegistry(provider, properties))
                .isSameAs(ObservationRegistry.NOOP);
    }
}
