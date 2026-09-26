package com.example.orderservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ms-exchange";
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String ORDER_CONFIRMED_ROUTING_KEY = "order.confirmed";
    public static final String STOCK_UPDATED_ROUTING_KEY = "stock.updated";
    public static final String STOCK_FAILED_ROUTING_KEY = "stock.failed";

    public static final String ORDER_CREATED_QUEUE = "order-service.order-created";
    public static final String STOCK_UPDATED_QUEUE = "order-service.stock-updated";
    public static final String STOCK_FAILED_QUEUE = "order-service.stock-failed";

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return new Queue(ORDER_CREATED_QUEUE, true);
    }

    @Bean
    public Queue stockUpdatedQueue() {
        return new Queue(STOCK_UPDATED_QUEUE, true);
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue())
                .to(eventExchange())
                .with(ORDER_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding stockUpdatedBinding() {
        return BindingBuilder.bind(stockUpdatedQueue())
                .to(eventExchange())
                .with(STOCK_UPDATED_ROUTING_KEY);
    }

    @Bean
    public Queue stockFailedQueue() {
        return new Queue(STOCK_FAILED_QUEUE, true);
    }

    @Bean
    public Binding stockFailedBinding() {
        return BindingBuilder.bind(stockFailedQueue())
                .to(eventExchange())
                .with(STOCK_FAILED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}