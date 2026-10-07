package com.example.productservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.cloud.consul.discovery.register=false")
class ProductServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
