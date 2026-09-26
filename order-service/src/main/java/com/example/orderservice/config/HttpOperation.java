package com.example.orderservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpMethod;

@Getter
@Setter
public class HttpOperation {

    private HttpMethod method;

    private String path;
}