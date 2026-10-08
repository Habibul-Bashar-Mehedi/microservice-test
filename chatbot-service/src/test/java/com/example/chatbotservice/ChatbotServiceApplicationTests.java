package com.example.chatbotservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.cloud.consul.discovery.register=false")
class ChatbotServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
