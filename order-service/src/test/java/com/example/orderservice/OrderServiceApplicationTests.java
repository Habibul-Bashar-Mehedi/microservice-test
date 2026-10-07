package com.example.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.cloud.consul.discovery.register=false")
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
