# Asynchronous Order Flow via Kafka

```mermaid
flowchart TD

    subgraph Client["Client"]
        A["POST /v2/orders"]
        B["POST /v2/orders/{id}/confirm"]
    end

    subgraph OS["Order Service"]
        C["asyncOrderService.create()<br/>quantity > 0 check<br/>duplicate PENDING/CONFIRMING check<br/>status = PENDING<br/>productUpdated = false<br/>save order"]
        D["OrderEventPublisher.publishCreated()<br/>payload: OrderCreatedEvent"]
        E["onOrderCreated consumer<br/>lookup order by orderId"]
        F{"userClient.isActive(userId)?"}
        G["status = REJECTED<br/>save order"]
        H["asyncOrderService.confirm()<br/>status PENDING → CONFIRMING<br/>save order<br/>returns 202 Accepted"]
        I["OrderEventPublisher.publishConfirmed()<br/>payload: OrderConfirmedEvent"]
        J["onStockUpdated consumer<br/>status = CONFIRMED<br/>productUpdated = true<br/>save order"]
        K["onStockUpdateFailed consumer<br/>if status not CONFIRMED:<br/>status = REJECTED<br/>productUpdated = false<br/>save order"]
    end

    subgraph KAFKA["Kafka"]
        T1["topic: order.created"]
        T2["topic: order.confirmed"]
        T3["topic: stock.updated"]
        T4["topic: stock.failed"]
    end

    subgraph UserS["User Service"]
        U["isActive(userId)<br/>REST call via UserClient"]
    end

    subgraph PS["Product Service"]
        L["onOrderConfirmed consumer"]
        M{"stockUpdate existsById(orderId)?<br/>dedupe check"}
        N{"updateQuantity:<br/>remaining = availableQuantity - quantity<br/>row lock findByIdForUpdate"}
        O["publish StockUpdateFailedEvent<br/>topic: stock.failed"]
        P["publish StockUpdateFailedEvent<br/>topic: stock.failed"]
        R["save StockUpdate(orderId)<br/>publish StockUpdatedEvent after commit<br/>topic: stock.updated"]
    end

    A --> C --> D -->|"OrderCreatedEvent"| T1
    T1 --> E
    E --> F
    F -->|"no"| G
    F -->|"yes"| H

    B --> H --> I -->|"OrderConfirmedEvent"| T2
    T2 --> L
    L --> M
    M -->|"duplicate - ignore"| L
    M -->|"new order"| N
    N -->|"remaining < 0<br/>InsufficientStockException"| O
    N -->|"product not found"| P
    N -->|"stock ok"| R

    O -->|"stock.failed"| T4
    P -->|"stock.failed"| T4
    T4 --> K

    R -->|"StockUpdatedEvent"| T3
    T3 --> J

    style T1 fill:#d0e8f5
    style T2 fill:#d0e8f5
    style T3 fill:#d0e8f5
    style T4 fill:#d0e8f5