# Consul Service Registry Activity Flow

> How **Consul** acts as the service registry/discovery layer for this system.
> All Spring Boot services self-register on startup; service-to-service calls resolve instances
> from Consul. **Synchronous** calls use the `@LoadBalanced` REST client with **Round Robin**;
> **asynchronous** calls use **Kafka**, whose broker is also discovered from Consul.
> Colors: **blue** = action, **amber** = decision, **red** = error/terminal, **green** = success,
> **grey** = start/end, **purple** = registry/Consul.

## 1. Registry Overview

```mermaid
flowchart LR
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef reg fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;
    classDef svc fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    AS["auth-service :8080"]:::svc
    US["user-service :8081"]:::svc
    PS["product-service :8082"]:::svc
    OS["order-service :8083"]:::svc
    LS["log-service :8084"]:::svc
    KF["kafka broker :9092"]:::svc

    CONSUL["Consul :8500<br/>service catalog<br/>catalog/service/{name}"]:::reg

    AS -->|"register"| CONSUL
    US -->|"register"| CONSUL
    PS -->|"register"| CONSUL
    OS -->|"register"| CONSUL
    LS -->|"register"| CONSUL
    KF -->|"registered via consul/kafka-service.json"| CONSUL

    CONSUL -->|"resolve auth-service"| US
    CONSUL -->|"resolve user-service"| AS
    CONSUL -->|"resolve user-service / product-service"| OS
    CONSUL -->|"resolve log-service"| PS
    CONSUL -->|"resolve log-service"| OS
    CONSUL -->|"resolve kafka"| PS
    CONSUL -->|"resolve kafka"| OS

    class AS,US,PS,OS,LS,KF svc;
    class CONSUL reg;
```

## 2. Service Registration (on startup)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;
    classDef reg fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;

    Start(["Service boot<br/>spring-cloud-starter-consul-discovery"]):::term --> LOAD["load application.yaml<br/>spring.cloud.consul.host/port"]:::act
    LOAD --> NAME["read spring.application.name<br/>(auth-service · user-service · ...)"]:::act
    NAME --> HEALTH{"register-health-check?"}:::dec
    HEALTH -->|"false"| NOCK["skip HTTP health check"]:::act
    HEALTH -->|"true"| HC["register /actuator/health check"]:::act
    NOCK --> IP["prefer-ip-address: true<br/>resolve service IP"]:::act
    HC --> IP
    IP --> REG["PUT /v1/agent/service/register"]:::reg
    REG --> CAT["Consul catalog updated<br/>name · address · port · tags"]:::ok
    CAT --> Stop(["Service discoverable"]):::term

    class HEALTH dec;
    class LOAD,NAME,NOCK,HC,IP,REG,CAT act;
    class Start,Stop term;
```

## 3. Synchronous Discovery (REST client + Round Robin)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;
    classDef reg fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;

    Start(["Caller needs peer<br/>e.g. order → user-service"]):::term --> LB["@LoadBalanced RestClient<br/>baseUrl = http://user-service"]:::act
    LB --> Q["GET Consul catalog<br/>/v1/catalog/service/user-service"]:::reg
    Q --> FOUND{"instances<br/>available?"}:::dec
    FOUND -->|"no"| ERR["503 no instances"]:::err
    FOUND -->|"yes"| RR["RoundRobinLoadBalancer<br/>pick next instance"]:::act
    RR --> CALL["send HTTP request<br/>to chosen host:port"]:::act
    CALL --> OK["response returned<br/>to caller"]:::ok
    OK --> Stop(["End"]):::term
    ERR --> Stop

    class FOUND dec;
    class LB,Q,RR,CALL,OK act;
    class Q reg;
    class ERR err;
    class Start,Stop term;
```

> Run multiple instances of a service (different ports) and Consul registers each one;
> the Round Robin load balancer alternates requests across them.

## 4. Asynchronous Broker Discovery (Kafka)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;
    classDef reg fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;

    Start(["product / order service boot"]):::term --> PP["KafkaBrokerDiscovery<br/>EnvironmentPostProcessor"]:::act
    PP --> Q["GET Consul catalog<br/>/v1/catalog/service/kafka"]:::reg
    Q --> FOUND{"broker<br/>found?"}:::dec
    FOUND -->|"yes"| SET["inject property<br/>spring.cloud.stream.kafka.binder.brokers"]:::act
    FOUND -->|"no / Consul down"| FB["fallback to<br/>application.yaml brokers"]:::act
    SET --> BIND["Spring Cloud Stream<br/>Kafka binder starts"]:::ok
    FB --> BIND
    BIND --> EVT["publish / consume events<br/>order.created · stock.updated ..."]:::ok
    EVT --> Stop(["End"]):::term

    class FOUND dec;
    class PP,Q,SET,FB,BIND,EVT act;
    class Q reg;
    class Start,Stop term;
```

## 5. Registry State / Health

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;
    classDef reg fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;

    Start(["Service running"]):::term --> HB["Consul agent tracks registration"]:::reg
    HB --> STOP{"service stopped<br/>or deregistered?"}:::dec
    STOP -->|"no"| PASS["instance stays in catalog<br/>eligible for Round Robin"]:::ok
    STOP -->|"yes"| DEREG["instance removed from catalog"]:::act
    DEREG --> NEXT["next discovery skips it"]:::ok
    PASS --> HB
    NEXT --> Stop(["End"]):::term

    class STOP dec;
    class HB,PASS,DEREG,NEXT act;
    class HB reg;
    class Start,Stop term;
```
