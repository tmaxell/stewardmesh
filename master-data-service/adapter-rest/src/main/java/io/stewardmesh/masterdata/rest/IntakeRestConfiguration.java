package io.stewardmesh.masterdata.rest;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IntakeRestConfiguration {

    @Bean
    IntakeApiTelemetry intakeApiTelemetry(MeterRegistry meterRegistry) {
        return new IntakeApiTelemetry(meterRegistry);
    }
}
