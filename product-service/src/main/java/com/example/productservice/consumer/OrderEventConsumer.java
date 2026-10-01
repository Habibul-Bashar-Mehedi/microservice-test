package com.example.productservice.consumer;

import com.example.event.OrderConfirmedEvent;
import com.example.productservice.service.OrderStockProcessor;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderStockProcessor orderStockProcessor;

    @Bean
    public Consumer<OrderConfirmedEvent> orderConfirmedListener() {
        return orderStockProcessor::process;
    }
}