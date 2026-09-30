package com.example.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_CONFIRMED_TOPIC = "order.confirmed";
    public static final String STOCK_UPDATED_TOPIC = "stock.updated";
    public static final String STOCK_FAILED_TOPIC = "stock.failed";

    public static final String ORDER_SERVICE_GROUP = "order-service";

    @Bean
    public NewTopic orderCreatedTopic() {
        return new NewTopic(ORDER_CREATED_TOPIC, 1, (short) 1);
    }

    @Bean
    public NewTopic orderConfirmedTopic() {
        return new NewTopic(ORDER_CONFIRMED_TOPIC, 1, (short) 1);
    }

    @Bean
    public NewTopic stockUpdatedTopic() {
        return new NewTopic(STOCK_UPDATED_TOPIC, 1, (short) 1);
    }

    @Bean
    public NewTopic stockFailedTopic() {
        return new NewTopic(STOCK_FAILED_TOPIC, 1, (short) 1);
    }
}