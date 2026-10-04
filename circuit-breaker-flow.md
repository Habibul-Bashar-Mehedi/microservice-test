# Circuit Breaker Activity Flow

> Resilience4j circuit breaker + 5s time limiter applied to every **synchronous** RestClient call
> (the v1 REST ops). If a downstream service hangs, the call falls back after **5 seconds**; after
> repeated failures the circuit **opens**, short-circuiting further calls to the fallback.
> Colors: **blue** = action, **amber** = decision, **red** = error/terminal, **green** = success,
> **grey** = start/end, **purple** = circuit breaker.

## 1. Circuit Breaker Lifecycle (Closed → Open → Half-Open)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Caller invokes<br/>RestClient v1 op"]):::term --> CLOSED["CLOSED<br/>requests pass through<br/>window: last 10 calls"]:::ok
    CLOSED --> FAIL{"failure rate<br/>&gt; 50% (min 5 calls)?"}:::dec
    FAIL -->|"no"| CLOSED
    FAIL -->|"yes"| OPEN["OPEN<br/>all calls fail fast<br/>wait: 10s"]:::err
    OPEN --> WAIT{"10s elapsed?"}:::dec
    WAIT -->|"no"| OPEN
    WAIT -->|"yes"| HALF["HALF-OPEN<br/>trial: 3 calls"]:::act
    HALF --> TRIAL{"trial calls<br/>succeed?"}:::dec
    TRIAL -->|"yes"| CLOSED
    TRIAL -->|"no"| OPEN
    CLOSED --> Stop(["End"]):::term
    OPEN --> Stop

    class FAIL,WAIT,TRIAL dec;
    class CLOSED ok;
    class OPEN err;
    class HALF act;
    class Start,Stop term;
```

## 2. Per-Request Activity (5s Timeout + Fallback)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Caller, e.g.<br/>order → user-service"]):::term --> CB["circuitBreakerFactory<br/>.create(serviceId)"]:::act
    CB --> STATE{"circuit<br/>state?"}:::dec
    STATE -->|"OPEN"| FF["FAIL FAST<br/>skip downstream call"]:::err
    STATE -->|"CLOSED / HALF-OPEN"| SUB["submit to executor<br/>(preserves request context)"]:::act
    SUB --> TL["TimeLimiter<br/>timeout = 5s"]:::dec
    TL -->|"response &lt; 5s"| R["downstream returns"]:::ok
    TL -->|"no response in 5s"| TIMEOUT["TimeoutException"]:::err
    TIMEOUT --> FB["run fallback"]:::act
    FF --> FB
    R --> REC["record SUCCESS<br/>failure rate decreases"]:::act
    TIMEOUT --> RECFAIL["record FAILURE<br/>sliding window updated"]:::act
    RECFAIL --> OPEN?{"failure rate<br/>&gt; threshold?"}:::dec
    OPEN? -->|"yes"| O["circuit → OPEN"]:::err
    OPEN? -->|"no"| CL["circuit stays CLOSED"]:::ok
    FB --> RET["return fallback result<br/>to caller"]:::ok
    O --> Stop(["End"]):::term
    CL --> Stop
    RET --> Stop

    class STATE,TL,OPEN? dec;
    class CB,SUB,FB,REC,RECFAIL act;
    class FF,TIMEOUT,O err;
    class R,CL,RET ok;
    class Start,Stop term;
```

## 3. Fallback Map (per service)

```mermaid
flowchart LR
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    subgraph AUTH["auth-service → user-service"]
        A1["register / fetch profile"]:::act --> A2["503 user-service unavailable"]:::err
    end

    subgraph USER["user-service → auth-service"]
        U1["sync role"]:::act --> U2["503 auth-service unavailable"]:::err
    end

    subgraph ORDER["order-service → user-service"]
        O1["isActive / email / name"]:::act --> O2["false / null"]:::ok
    end

    subgraph ORDER2["order-service → product-service"]
        O3["update stock"]:::act --> O4["503 product-service unavailable"]:::err
    end

    subgraph LOG["product/order → log-service"]
        L1["record log"]:::act --> L2["skip (log + warn)"]:::ok
    end

    class A1,U1,O1,O3,L1 act;
    class A2,U2,O4 err;
    class O2,L2 ok;
```