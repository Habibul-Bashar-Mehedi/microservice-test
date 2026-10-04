# Kafka Activity Flow

> Activity diagrams for the asynchronous order flow via Kafka. Colors mark node roles:
> **blue** = action, **amber** = decision, **red** = error/terminal response, **green** = success,
> **grey** = start/end. Topics are created via `KafkaConfig` `NewTopic` beans on startup.
> Communication split: **synchronous** service calls use the REST client (Consul + Round Robin),
> **asynchronous** calls use **Kafka**, whose broker is discovered from **Consul** at startup.

## 0. Kafka Broker Discovery via Consul (Async Path)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;
    classDef reg fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;

    Start(["Service boot<br/>(order / product)"]):::term --> PP["EnvironmentPostProcessor<br/>KafkaBrokerDiscovery..."]:::act
    PP --> Q["GET Consul catalog<br/>/v1/catalog/service/kafka"]:::reg
    Q --> OK{"broker<br/>found?"}:::dec
    OK -->|"yes"| SET["addFirst property source<br/>spring.cloud.stream.kafka.binder.brokers<br/>= localhost:9092"]:::act
    OK -->|"no / Consul down"| FB["fall back to<br/>application.yaml brokers"]:::act
    SET --> BIND["Spring Cloud Stream<br/>Kafka binder starts"]:::ok
    FB --> BIND
    BIND --> Stop(["publish / consume<br/>on Kafka topics"]):::term

    class OK dec;
    class PP,Q,SET,FB,BIND act;
    class Q reg;
    class Start,Stop term;
```

## 1. Kafka Topology (Topics & Consumer Groups)

```mermaid
flowchart LR
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;

    OS(["order-service"]):::term -->|"publish"| T1["order.created"]:::act
    OS -->|"publish"| T2["order.confirmed"]:::act
    PS(["product-service"]):::term -->|"publish"| T3["stock.updated"]:::act
    PS -->|"publish"| T4["stock.failed"]:::act

    T1 -->|"consume · group=order-service"| OS
    T2 -->|"consume · group=product-service"| PS
    T3 -->|"consume · group=order-service"| OS
    T4 -->|"consume · group=order-service"| OS

    K["Kafka (KRaft single node)<br/>topics auto-created by KafkaAdmin<br/>1 partition, replication factor 1<br/>JacksonJsonSerializer / __TypeId__ header<br/>JacksonJsonDeserializer + trusted packages"]:::act

    T1 --> K
    T2 --> K
    T3 --> K
    T4 --> K

    class T1,T2,T3,T4,K act;
    class OS,PS term;
```

## 2. Order Created Activity (`POST /v2/orders`)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Start"]):::term --> V{"quantity > 0?"}:::dec
    V -->|"no"| B400["400 Quantity must be a positive number"]:::err
    V -->|"yes"| DUP{"duplicate PENDING /<br/>CONFIRMING order?"}:::dec
    DUP -->|"yes"| EXIST["return existing order"]:::ok
    DUP -->|"no"| SAVE["status = PENDING<br/>productUpdated = false<br/>save order"]:::act
    SAVE --> PUB["KafkaOrderEventPublisher.publishCreated()<br/>OrderCreatedEvent(orderId, userId, productId, quantity)"]:::act
    PUB --> LOG["log-service recordPublish('order.created')"]:::act
    LOG --> SEND{"kafkaTemplate.send('order.created', event)"}:::act
    SEND -->|"failure"| PUBFAIL["log recordPublishFailed + rethrow"]:::err
    SEND -->|"ok"| STOPP["200 Order created (PENDING)"]:::ok
    STOPP --> Stop(["End"]):::term
    B400 --> Stop
    EXIST --> Stop
    PUBFAIL --> Stop

    class V,DUP dec;
    class SAVE,PUB,LOG,SEND act;
    class B400,PUBFAIL err;
    class EXIST,STOPP ok;
    class Start,Stop term;
```

## 3. Order Created Consumer Activity (order-service)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["@KafkaListener order.created<br/>groupId = order-service"]):::term --> LOOKUP["orderRepository.findById(orderId)"]:::act
    LOOKUP --> UNKNOWN{"order exists?"}:::dec
    UNKNOWN -->|"no"| FAILED["log recordConsumeFailed<br/>'unknown order'"]:::err
    UNKNOWN -->|"yes"| REC["log recordConsume('order.created')<br/>evict orders cache"]:::act
    REC --> Stop(["End"]):::term
    FAILED --> Stop

    class UNKNOWN dec;
    class LOOKUP,REC act;
    class FAILED err;
    class Start,Stop term;
```

## 4. Order Confirm Activity (`POST /v2/orders/{id}/confirm`)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Start"]):::term --> FIND["orderRepository.findById(id)"]:::act
    FIND --> NF{"order found?"}:::dec
    NF -->|"no"| N404["404 not found"]:::err
    NF -->|"yes"| ST{"status CONFIRMED /<br/>REJECTED / CANCELLED?"}:::dec
    ST -->|"yes"| CUR["return order unchanged"]:::ok
    ST -->|"no (PENDING)"| CONFIRMING["status = CONFIRMING<br/>save order"]:::act
    CONFIRMING --> PUB["KafkaOrderEventPublisher.publishConfirmed()<br/>OrderConfirmedEvent(orderId, productId, quantity)"]:::act
    PUB --> SEND{"kafkaTemplate.send('order.confirmed', event)"}:::act
    SEND -->|"failure"| PUBFAIL["log recordPublishFailed + rethrow"]:::err
    SEND -->|"ok"| A202["202 Accepted<br/>(still CONFIRMING)"]:::ok
    A202 --> Stop(["End"]):::term
    N404 --> Stop
    CUR --> Stop
    PUBFAIL --> Stop

    class NF,ST dec;
    class FIND,CONFIRMING,PUB,SEND act;
    class N404,PUBFAIL err;
    class CUR,A202 ok;
    class Start,Stop term;
```

## 5. Order Confirmed Consumer Activity (product-service)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["@KafkaListener order.confirmed<br/>groupId = product-service<br/>@Transactional"]):::term --> VALID{"productId != null &&<br/>quantity > 0?"}:::dec
    VALID -->|"no"| MAL["log recordConsumeFailed<br/>'malformed event'"]:::err
    VALID -->|"yes"| DEDUP{"stockUpdateRepository<br/>existsById(orderId)?"}:::dec
    DEDUP -->|"yes"| DUPOK["log recordConsume<br/>'duplicate, already updated'"]:::ok
    DEDUP -->|"no"| REC["log recordConsume('order.confirmed')"]:::act
    REC --> UQ["ProductService.updateQuantity()<br/>row-lock findByIdForUpdate<br/>remaining = available - quantity"]:::act
    UQ --> LOW{"remaining < 0<br/>InsufficientStockException?"}:::dec
    LOW -->|"yes"| STOCKFAIL["log recordConsumeFailed<br/>publishAfterCommit('stock.failed', StockUpdateFailedEvent)"]:::err
    LOW -->|"no"| PNF{"product not found<br/>(updated == null)?"}:::dec
    PNF -->|"yes"| NOTFOUND["log recordConsumeFailed<br/>publishAfterCommit('stock.failed', StockUpdateFailedEvent)"]:::err
    PNF -->|"no"| SAVESTOCK["save StockUpdate(orderId)<br/>afterCommit → kafkaTemplate.send('stock.updated', StockUpdatedEvent)"]:::act
    SAVESTOCK --> DONE["log info + transaction commits"]:::ok
    MAL --> Stop(["End"]):::term
    DUPOK --> Stop
    STOCKFAIL --> Stop
    NOTFOUND --> Stop
    DONE --> Stop

    class VALID,DEDUP,LOW,PNF dec;
    class REC,UQ,SAVESTOCK act;
    class MAL,STOCKFAIL,NOTFOUND err;
    class DUPOK,DONE ok;
    class Start,Stop term;
```

## 6. Stock Updated Consumer Activity (order-service)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["@KafkaListener stock.updated<br/>groupId = order-service"]):::term --> FIND["orderRepository.findById(orderId)"]:::act
    FIND --> UNKNOWN{"order found?"}:::dec
    UNKNOWN -->|"no"| FAILED["log recordConsumeFailed<br/>'unknown order'"]:::err
    UNKNOWN -->|"yes"| CANCELLED{"status CANCELLED?"}:::dec
    CANCELLED -->|"yes"| IGNORE["log recordConsume<br/>'cancelled, ignoring'"]:::ok
    CANCELLED -->|"no"| CONFIRM["status = CONFIRMED<br/>productUpdated = true<br/>save order"]:::act
    CONFIRM --> REC["log recordConsume('stock.updated')<br/>evict orders/orderById cache"]:::act
    REC --> Stop(["End"]):::term
    FAILED --> Stop
    IGNORE --> Stop

    class UNKNOWN,CANCELLED dec;
    class FIND,CONFIRM,REC act;
    class FAILED err;
    class IGNORE ok;
    class Start,Stop term;
```

## 7. Stock Update Failed Consumer Activity (order-service)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["@KafkaListener stock.failed<br/>groupId = order-service"]):::term --> FIND["orderRepository.findById(orderId)"]:::act
    FIND --> UNKNOWN{"order found?"}:::dec
    UNKNOWN -->|"no"| FAILED["log recordConsumeFailed<br/>'unknown order'"]:::err
    UNKNOWN -->|"yes"| FINAL{"status CONFIRMED<br/>or CANCELLED?"}:::dec
    FINAL -->|"yes"| IGNORE["log recordConsume<br/>'terminal, ignoring'"]:::ok
    FINAL -->|"no"| REJECT["status = REJECTED<br/>productUpdated = false<br/>save order"]:::act
    REJECT --> REC["log recordConsume('stock.failed')<br/>evict orders/orderById cache"]:::act
    REC --> Stop(["End"]):::term
    FAILED --> Stop
    IGNORE --> Stop

    class UNKNOWN,FINAL dec;
    class FIND,REJECT,REC act;
    class FAILED err;
    class IGNORE ok;
    class Start,Stop term;
```

## 8. Publish After Commit Activity (product-service)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["TransactionSynchronization.afterCommit()"]):::term --> LOG["log-service recordPublish(topic, payload)"]:::act
    LOG --> SEND{"kafkaTemplate.send(topic, payload)<br/>stock.updated / stock.failed"}:::act
    SEND -->|"failure"| PUBFAIL["log recordPublishFailed + rethrow"]:::err
    SEND -->|"ok"| DONE["message on Kafka topic"]:::ok
    DONE --> Stop(["End"]):::term
    PUBFAIL --> Stop

    class SEND dec;
    class LOG act;
    class PUBFAIL err;
    class DONE ok;
    class Start,Stop term;
```