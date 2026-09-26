# Asynchronous Order Flow via RabbitMQ

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

    subgraph RMQ["RabbitMQ"]
        X["ms-exchange<br/>topic exchange"]
        Q1["queue: order-service.order-created<br/>binding: order.created"]
        Q2["queue: product-service.order-confirmed<br/>binding: order.confirmed"]
        Q3["queue: order-service.stock-updated<br/>binding: stock.updated"]
        Q4["queue: order-service.stock-failed<br/>binding: stock.failed"]
    end

    subgraph UserS["User Service"]
        U["isActive(userId)<br/>REST call via UserClient"]
    end

    subgraph PS["Product Service"]
        L["onOrderConfirmed consumer"]
        M{"stockUpdate existsById(orderId)?<br/>dedupe check"}
        N{"updateQuantity:<br/>remaining = availableQuantity - quantity<br/>row lock findByIdForUpdate"}
        O["publish StockUpdateFailedEvent<br/>routingKey: stock.failed"]
        P["publish StockUpdateFailedEvent<br/>routingKey: stock.failed"]
        R["save StockUpdate(orderId)<br/>publish StockUpdatedEvent after commit<br/>routingKey: stock.updated"]
    end

    A --> C --> D -->|"OrderCreatedEvent"| X
    X -->|"order.created"| Q1 --> E
    E --> F
    F -->|"no"| G
    F -->|"yes"| H

    B --> H --> I -->|"OrderConfirmedEvent"| X
    X -->|"order.confirmed"| Q2 --> L
    L --> M
    M -->|"duplicate - ignore"| L
    M -->|"new order"| N
    N -->|"remaining < 0<br/>InsufficientStockException"| O
    N -->|"product not found"| P
    N -->|"stock ok"| R

    O -->|"stock.failed"| Q4
    P -->|"stock.failed"| Q4
    Q4 --> K

    R -->|"StockUpdatedEvent"| X
    X -->|"stock.updated"| Q3 --> J

    style X fill:#f9d6c5
    style Q1 fill:#d0e8f5
    style Q2 fill:#d0e8f5
    style Q3 fill:#d0e8f5
    style Q4 fill:#d0e8f5