# Microservice System Architecture & Activity Flows

> High-level architecture of the whole system plus the key activity flows.
> Ports: **auth 8080**, **user 8081**, **product 8082**, **order 8083**, **log 8084**,
> **Kafka 9092**, **RabbitMQ 5672**, **PostgreSQL 5432**.
> Colors: **blue** = action, **amber** = decision, **red** = error/terminal, **green** = success,
> **grey** = start/end, **purple** = service.

## 1. System Architecture

```mermaid
flowchart LR
    classDef svc fill:#f3e8fd,stroke:#9334e6,stroke-width:2px;
    classDef inf fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef db fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef ext fill:#fce8e6,stroke:#d93025,stroke-width:1px;

    subgraph FE["Browser"]
        FE1["Angular SPA<br/>login · register · dashboard<br/>user · product · order · log"]:::ext
    end

    subgraph PLATFORM["Message Brokers"]
        KAFKA["Kafka (KRaft single node)<br/>topics: order.created · order.confirmed<br/>stock.updated · stock.failed"]:::inf
        RMQ["RabbitMQ 3.13-management<br/>(management UI 15672)"]:::inf
    end

    subgraph PG["PostgreSQL (5432, user: lemon)"]
        DB_A["auth-service"]:::db
        DB_U["user-service"]:::db
        DB_P["product-service"]:::db
        DB_O["order-service"]:::db
        DB_L["log-db"]:::db
    end

    subgraph SERVICES["Backend Services (Spring Boot)"]
        AS["auth-service :8080<br/>login · register · JWT (HS384)"]:::svc
        US["user-service :8081<br/>profile CRUD · active · register"]:::svc
        PS["product-service :8082<br/>products · stock update<br/>Kafka consumer/producer"]:::svc
        OS["order-service :8083<br/>orders · sync + async (Kafka)<br/>Kafka consumer/producer"]:::svc
        LS["log-service :8084<br/>message publish/consume logs"]:::svc
    end

    FE1 -->|"JWT Bearer"| AS
    FE1 -->|"JWT Bearer"| US
    FE1 -->|"JWT Bearer"| PS
    FE1 -->|"JWT Bearer"| OS
    FE1 -->|"JWT Bearer"| LS

    AS -->|"service token"| US
    OS -->|"forward caller JWT"| US
    OS -->|"forward caller JWT"| PS
    OS -->|"REST record*"| LS

    AS --> DB_A
    US --> DB_U
    PS --> DB_P
    OS --> DB_O
    LS --> DB_L

    PS -->|"publish/consume"| KAFKA
    OS -->|"publish/consume"| KAFKA

    RMQ -.->|"available (unused by services)"| SERVICES

    class AS,US,PS,OS,LS svc;
    class KAFKA,RMQ inf;
    class DB_A,DB_U,DB_P,DB_O,DB_L db;
    class FE1 ext;
```

## 2. JWT Authentication Activity (all services)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Request"]):::term --> H{"Authorization:<br/>'Bearer &lt;token&gt;'?"}:::dec
    H -->|"no"| NOAUTH["no auth context"]:::act
    H -->|"yes"| PARSE["extract JWT"]:::act
    PARSE --> VERIFY{"JwtService.parseToken()<br/>signature + exp valid?"}:::dec
    VERIFY -->|"invalid / expired"| CLEAR["clear SecurityContext"]:::act
    VERIFY -->|"valid"| ROLE["read sub + role claim"]:::act
    ROLE --> SET["set authentication<br/>ROLE_&lt;role&gt;"]:::act
    NOAUTH --> SEC["SecurityConfig<br/>authorizeHttpRequests"]:::dec
    CLEAR --> SEC
    SET --> SEC
    SEC -->|"role not met"| D403["401 / 403"]:::err
    SEC -->|"allowed"| CTL["controller runs"]:::ok
    CTL --> Stop(["End"]):::term
    D403 --> Stop

    class H,VERIFY,SEC dec;
    class PARSE,ROLE,SET,NOAUTH,CLEAR act;
    class D403 err;
    class CTL ok;
    class Start,Stop term;
```

## 3. Async Order Lifecycle Activity (Kafka)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["POST /v2/orders"]):::term --> V{"quantity > 0?"}:::dec
    V -->|"no"| B400["400"]:::err
    V -->|"yes"| DUP{"duplicate PENDING /<br/>CONFIRMING order?"}:::dec
    DUP -->|"yes"| EXIST["return existing order"]:::ok
    DUP -->|"no"| PEND["status = PENDING<br/>save order"]:::act
    PEND --> PUB1["publish order.created"]:::act
    PUB1 --> LOG1["log-service recordPublish"]:::act
    LOG1 --> T1["topic: order.created"]:::act
    T1 --> C1["@KafkaListener order.created (order-service)<br/>lookup order"]:::act
    C1 --> LOG2["log-service recordConsume"]:::act
    LOG2 --> CFM(["POST /v2/orders/{id}/confirm"]):::term
    CFM --> CM{"status already terminal?"}:::dec
    CM -->|"yes"| CURL["return order unchanged"]:::ok
    CM -->|"no (PENDING)"| CONFIRMING["status = CONFIRMING<br/>save order · 202 Accepted"]:::act
    CONFIRMING --> PUB2["publish order.confirmed"]:::act
    PUB2 --> T2["topic: order.confirmed"]:::act
    T2 --> C2["@KafkaListener order.confirmed (product-service)<br/>@Transactional"]:::act
    C2 --> LOW{"remaining = available - quantity<br/>remaining &lt; 0?"}:::dec
    LOW -->|"yes"| FAILED["publish stock.failed<br/>status = REJECTED"]:::err
    LOW -->|"no"| SAVE2["save StockUpdate(orderId)<br/>publish stock.updated after commit"]:::act
    SAVE2 --> T3["topic: stock.updated"]:::act
    T3 --> C3["@KafkaListener stock.updated (order-service)"]:::act
    C3 --> CONF["status = CONFIRMED<br/>productUpdated = true"]:::ok
    FAILED --> T4["topic: stock.failed"]:::act
    T4 --> C4["@KafkaListener stock.failed (order-service)"]:::act
    C4 --> REJ["status = REJECTED<br/>productUpdated = false"]:::err
    CONF --> Stop(["End"]):::term
    REJ --> Stop
    B400 --> Stop
    EXIST --> Stop
    CURL --> Stop

    class V,DUP,CM,LOW dec;
    class PEND,PUB1,LOG1,T1,C1,LOG2,CFM,CONFIRMING,PUB2,T2,C2,SAVE2,T3,C3,T4,C4 act;
    class B400,FAILED,REJ err;
    class EXIST,CURL,CONF ok;
    class Start,Stop,CFM term;
```

## 4. Product Stock Update Activity (product-service consumer)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["@KafkaListener order.confirmed"]):::term --> VALID{"productId != null &&<br/>quantity > 0?"}:::dec
    VALID -->|"no"| MAL["log recordConsumeFailed"]:::err
    VALID -->|"yes"| DEDUP{"StockUpdate exists<br/>for orderId?"}:::dec
    DEDUP -->|"yes"| DUPOK["duplicate - already updated"]:::ok
    DEDUP -->|"no"| REC["log recordConsume"]:::act
    REC --> LOCK["updateQuantity()<br/>findByIdForUpdate (row lock)<br/>remaining = available - quantity"]:::act
    LOCK --> LOW{"remaining &lt; 0?"}:::dec
    LOW -->|"yes"| SF["publishAfterCommit stock.failed"]:::err
    LOW -->|"no"| PNF{"product not found?"}:::dec
    PNF -->|"yes"| SF2["publishAfterCommit stock.failed"]:::err
    PNF -->|"no"| SAVE["save StockUpdate(orderId)<br/>afterCommit → stock.updated"]:::act
    SAVE --> DONE["transaction commits"]:::ok
    MAL --> Stop(["End"]):::term
    DUPOK --> Stop
    SF --> Stop
    SF2 --> Stop
    DONE --> Stop

    class VALID,DEDUP,LOW,PNF dec;
    class REC,LOCK,SAVE act;
    class MAL,SF,SF2 err;
    class DUPOK,DONE ok;
    class Start,Stop term;
```

## 5. Log-Service Activity

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["service calls"]):::term --> REC["POST /v1/logs<br/>recordPublish / recordConsume<br/>recordPublishFailed / recordConsumeFailed"]:::act
    REC --> SAVE["save MessageLog<br/>topic, payload, status, timestamp"]:::act
    SAVE --> OK["200 saved"]:::ok
    OK --> Stop(["End"]):::term
    Start2(["ADMIN view"]):::term --> LIST["GET /v1/logs"]:::act
    LIST --> ROWS["list logs"]:::ok
    ROWS --> Stop2(["End"]):::term
```