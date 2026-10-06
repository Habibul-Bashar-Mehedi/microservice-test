package com.example.orderservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.orderservice.config.HttpOperation;
import com.example.orderservice.config.UserServiceProperties;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class RestClientUserClientTest {

    @Mock
    private CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    @Mock
    private CircuitBreaker circuitBreaker;

    private MockRestServiceServer server;
    private RestClientUserClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://user-service");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        HttpOperation operation = new HttpOperation();
        operation.setMethod(HttpMethod.GET);
        operation.setPath("/v1/users/{id}");
        UserServiceProperties properties = new UserServiceProperties();
        properties.setBaseUrl("http://user-service");
        properties.setGetUser(operation);

        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
        doAnswer(invocation -> {
            Supplier<?> toRun = invocation.getArgument(0);
            Function<Throwable, ?> fallback = invocation.getArgument(1);
            try {
                return toRun.get();
            } catch (Throwable throwable) {
                return fallback.apply(throwable);
            }
        }).when(circuitBreaker).run(any(), any());

        client = new RestClientUserClient(restClient, properties, circuitBreakerFactory);
    }

    private void expectProfile() {
        server.expect(requestTo("http://user-service/v1/users/2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":2,\"name\":\"Alice\",\"email\":\"alice@example.com\",\"active\":true}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void isActive_returnsTrueWhenUserIsActive() {
        server.expect(requestTo("http://user-service/v1/users/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"active\":true}", MediaType.APPLICATION_JSON));

        assertThat(client.isActive(1L)).isTrue();
        server.verify();
    }

    @Test
    void isActive_returnsFalseWhenUserIsInactive() {
        server.expect(requestTo("http://user-service/v1/users/1"))
                .andRespond(withSuccess("{\"active\":false}", MediaType.APPLICATION_JSON));

        assertThat(client.isActive(1L)).isFalse();
    }

    @Test
    void isActive_returnsFalseOnHttpErrorResponse() {
        server.expect(requestTo("http://user-service/v1/users/1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.isActive(1L)).isFalse();
    }

    @Test
    void isActive_returnsFalseWhenResponseHasNoBody() {
        server.expect(requestTo("http://user-service/v1/users/1"))
                .andRespond(withNoContent());

        assertThat(client.isActive(1L)).isFalse();
    }

    @Test
    void isActive_fallsBackToFalseWhenRequestThrows() {
        server.expect(requestTo("http://user-service/v1/users/1"))
                .andRespond(withException(new IOException("connection refused")));

        assertThat(client.isActive(1L)).isFalse();
    }

    @Test
    void getEmail_returnsEmailFromProfile() {
        expectProfile();

        assertThat(client.getEmail(2L)).isEqualTo("alice@example.com");
    }

    @Test
    void getName_returnsNameFromProfile() {
        expectProfile();

        assertThat(client.getName(2L)).isEqualTo("Alice");
    }

    @Test
    void getEmail_returnsNullOnHttpErrorResponse() {
        server.expect(requestTo("http://user-service/v1/users/2"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getEmail(2L)).isNull();
    }

    @Test
    void getName_returnsNullOnHttpErrorResponse() {
        server.expect(requestTo("http://user-service/v1/users/2"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getName(2L)).isNull();
    }

    @Test
    void getEmail_fallsBackToNullWhenRequestThrows() {
        server.expect(requestTo("http://user-service/v1/users/2"))
                .andRespond(withException(new IOException("connection refused")));

        assertThat(client.getEmail(2L)).isNull();
    }

    @Test
    void getName_fallsBackToNullWhenRequestThrows() {
        server.expect(requestTo("http://user-service/v1/users/2"))
                .andRespond(withException(new IOException("connection refused")));

        assertThat(client.getName(2L)).isNull();
    }

    @Test
    void getEmail_returnsNullWhenResponseHasNoBody() {
        server.expect(requestTo("http://user-service/v1/users/2"))
                .andRespond(withNoContent());

        assertThat(client.getEmail(2L)).isNull();
    }

    @Test
    void getName_returnsNullWhenResponseHasNoBody() {
        server.expect(requestTo("http://user-service/v1/users/2"))
                .andRespond(withNoContent());

        assertThat(client.getName(2L)).isNull();
    }
}
