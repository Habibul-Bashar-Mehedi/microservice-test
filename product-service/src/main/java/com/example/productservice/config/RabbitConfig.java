package com.example.productservice.config;

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
    public static final String ORDER_CONFIRMED_ROUTING_KEY = "order.confirmed";
    public static final String STOCK_UPDATED_ROUTING_KEY = "stock.updated";
    public static final String STOCK_FAILED_ROUTING_KEY = "stock.failed";

    public static final String ORDER_CONFIRMED_QUEUE = "product-service.order-confirmed";

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue orderConfirmedQueue() {
        return new Queue(ORDER_CONFIRMED_QUEUE, true);
    }

    @Bean
    public Binding orderConfirmedBinding() {
        return BindingBuilder.bind(orderConfirmedQueue())
                .to(eventExchange())
                .with(ORDER_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}