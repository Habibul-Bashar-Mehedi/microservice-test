package com.example.productservice.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class KafkaBrokerDiscoveryEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String BROKERS_PROPERTY = "spring.cloud.stream.kafka.binder.brokers";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String consulHost = environment.getProperty("spring.cloud.consul.host", "localhost");
        String consulPort = environment.getProperty("spring.cloud.consul.port", "8500");
        String serviceName = environment.getProperty("kafka.discovery.service-name", "kafka");

        String brokers = resolveBrokers(consulHost, consulPort, serviceName);
        if (brokers == null || brokers.isBlank()) {
            return;
        }

        environment.getPropertySources().addFirst(
                new MapPropertySource("consulKafkaBrokers", Map.of(BROKERS_PROPERTY, brokers)));
    }

    private String resolveBrokers(String consulHost, String consulPort, String serviceName) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + consulHost + ":" + consulPort
                            + "/v1/catalog/service/" + serviceName))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            return parseBrokers(response.body());
        } catch (Exception e) {
            return null;
        }
    }

    private String parseBrokers(String body) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(body);
        List<String> brokers = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                String address = node.path("ServiceAddress").asString("");
                if (address.isBlank()) {
                    address = node.path("Address").asString("");
                }
                int port = node.path("ServicePort").asInt();
                if (!address.isBlank() && port > 0) {
                    brokers.add(address + ":" + port);
                }
            }
        }
        return String.join(",", brokers);
    }
}