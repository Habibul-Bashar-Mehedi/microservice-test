package com.example.chatbotservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Calls the other microservices through service discovery, forwarding the
 * caller's JWT. Downstream services re-check the role, so the chatbot can never
 * exceed the caller's real permissions.
 */
@Component
public class DownstreamClient {

    private final RestClient.Builder builder;
    private final ObjectMapper mapper;

    public final String productBase;
    public final String userBase;
    public final String orderBase;
    public final String logBase;

    public DownstreamClient(
            RestClient.Builder builder,
            ObjectMapper mapper,
            @Value("${services.product-service.base-url}") String productBase,
            @Value("${services.user-service.base-url}") String userBase,
            @Value("${services.order-service.base-url}") String orderBase,
            @Value("${services.log-service.base-url}") String logBase) {
        this.builder = builder;
        this.mapper = mapper;
        this.productBase = productBase;
        this.userBase = userBase;
        this.orderBase = orderBase;
        this.logBase = logBase;
    }

    public JsonNode get(String baseUrl, String path, String authHeader) {
        return exchange(HttpMethod.GET, baseUrl, path, null, authHeader);
    }

    public JsonNode exchange(HttpMethod method, String baseUrl, String path, Object body, String authHeader) {
        try {
            RestClient.RequestBodySpec spec = builder.baseUrl(baseUrl).build()
                    .method(method)
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON);

            Object requestBody = body;
            if (body instanceof JsonNode jsonBody) {
                requestBody = mapper.writeValueAsString(jsonBody);
            }
            if (requestBody != null) {
                spec.body(requestBody);
            }

            String raw = spec.retrieve().body(String.class);
            if (raw == null) {
                return null;
            }
            return mapper.readTree(raw);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(
                    e.getStatusCode(),
                    "Downstream " + method + " " + baseUrl + path + " failed: "
                            + e.getResponseBodyAsString(),
                    e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Downstream service unavailable: " + e.getMessage(),
                    e);
        }
    }
}
