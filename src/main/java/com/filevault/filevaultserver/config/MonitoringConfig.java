package com.filevault.filevaultserver.config;

import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonitoringConfig {

    private static final int RECORDED_REQUESTS = 500;

    /**
     * Keeps the last requests in memory for the {@code httpexchanges} actuator endpoint (only reachable
     * when it is listed in ACTUATOR_EXPOSE, and then only with the monitoring login). Boot records
     * method, URI, status and timing; Authorization/Cookie headers are excluded by default. Off unless
     * RECORD_REQUESTS=true: it holds request data in the heap and costs a little per request.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.monitoring", name = "record-requests", havingValue = "true")
    public HttpExchangeRepository httpExchangeRepository() {
        InMemoryHttpExchangeRepository repository = new InMemoryHttpExchangeRepository();
        repository.setCapacity(RECORDED_REQUESTS);
        return repository;
    }
}
