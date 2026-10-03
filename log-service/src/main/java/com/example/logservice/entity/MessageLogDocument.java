package com.example.logservice.entity;

import java.time.Instant;
import java.time.ZoneId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "message_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageLogDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Keyword)
    private String serviceName;

    @Field(type = FieldType.Keyword)
    private String direction;

    @Field(type = FieldType.Keyword)
    private String routingKey;

    @Field(type = FieldType.Keyword)
    private String queue;

    @Field(type = FieldType.Keyword)
    private String email;

    @Field(type = FieldType.Keyword)
    private String payload;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String detail;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;

    public static MessageLogDocument from(MessageLog log) {
        return MessageLogDocument.builder()
                .id(log.getId())
                .serviceName(log.getServiceName())
                .direction(log.getDirection())
                .routingKey(log.getRoutingKey())
                .queue(log.getQueue())
                .email(log.getEmail())
                .payload(log.getPayload())
                .status(log.getStatus())
                .detail(log.getDetail())
                .createdAt(log.getCreatedAt() == null
                        ? null
                        : log.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .build();
    }
}