# How All Services Authenticate

> How authentication works across every microservice: a single login at the auth-service
> issues one JWT, which every other service validates on each request and then enforces
> role-based access (USER / ADMIN / SERVICE).
> Colors: **blue** = action, **amber** = decision, **red** = error/deny, **green** = success, **grey** = start/end.

## 1. Obtaining the JWT (auth-service)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["User enters credentials"]):::term --> LOGIN["POST /auth/login"]:::act
    LOGIN --> FIND{"email found in<br/>auth DB?"}:::dec
    FIND -->|"no"| U401["401 Invalid email or password"]:::err
    FIND -->|"yes"| ACT{"user-service profile<br/>active?"}:::dec
    ACT -->|"inactive"| U403["403 Not activated"]:::err
    ACT -->|"active"| MATCH{"BCrypt matches<br/>password?"}:::dec
    MATCH -->|"no"| U401
    MATCH -->|"yes"| JWT["JWT issued (HS384)<br/>sub = email, name, role, iat, exp"]:::act
    JWT --> STORE["stored in localStorage<br/>by frontend"]:::act
    STORE --> Done(["End - token ready"]):::term
    U401 --> Done
    U403 --> Done
```

## 2. Authenticating a Request in Any Service

This runs in **every** service (user, product, order, log) on each protected call.

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Request with JWT"]):::term --> FILT["JwtAuthenticationFilter"]:::act
    FILT --> H{"Authorization header<br/>= 'Bearer &lt;token&gt;'?"}:::dec
    H -->|"no"| NOAUTH["no auth context"]:::act
    H -->|"yes"| PARSE["extract token"]:::act
    PARSE --> VERIFY{"JwtService.parseToken()<br/>signature + exp valid?"}:::dec
    VERIFY -->|"invalid / expired"| CLEAR["clear SecurityContext"]:::act
    VERIFY -->|"valid"| ROLE["read sub + role claim"]:::act
    ROLE --> SET["set authentication<br/>ROLE_&lt;role&gt; authority"]:::act
    NOAUTH --> SEC["SecurityConfig:<br/>authorizeHttpRequests"]:::dec
    CLEAR --> SEC
    SET --> SEC
    SEC -->|"endpoint role = ADMIN"| ISADMIN{"has ROLE_ADMIN?"}:::dec
    ISADMIN -->|"no"| D403["403 Forbidden"]:::err
    ISADMIN -->|"yes"| CTL["controller runs"]:::ok
    SEC -->|"endpoint role = USER"| ISUSER{"has ROLE_USER?"}:::dec
    ISUSER -->|"no"| D403
    ISUSER -->|"yes"| CTL
    SEC -->|"any authenticated"| ISANY{"authenticated?"}:::dec
    ISANY -->|"no"| D401["401 Unauthorized"]:::err
    ISANY -->|"yes"| CTL
    SEC -->|"permitAll"| CTL
    CTL --> Stop(["End"]):::term
    D403 --> Stop
    D401 --> Stop

    class H,VERIFY,SEC,ISADMIN,ISUSER,ISANY dec;
    class FILT,PARSE,ROLE,SET,NOAUTH,CLEAR act;
    class D403,D401 err;
    class CTL ok;
    class Start,Stop term;
```

## 3. Role Enforcement per Service

```mermaid
flowchart TD
    classDef svc fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;
    classDef ad fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef us fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef both fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    JWT(["One JWT from auth-service"]):::term --> USER["user-service"]:::svc
    JWT --> PROD["product-service"]:::svc
    JWT --> ORDER["order-service"]:::svc
    JWT --> LOG["log-service"]:::svc

    USER --> U1["POST /v1/users<br/>GET /v1/users<br/>PATCH /v1/users/{id}/active<br/>ADMIN only"]:::ad
    USER --> U2["GET /v1/users/{id}<br/>GET /v1/users/email/{email}<br/>any authenticated"]:::both
    USER --> U3["POST /v1/users/register<br/>SERVICE / ADMIN only"]:::ad

    PROD --> P1["POST /v1/products<br/>PUT /v1/products/{id}/quantity<br/>ADMIN only"]:::ad
    PROD --> P2["GET /v1/products(/{id})<br/>any authenticated"]:::both

    ORDER --> O1["GET /v1/orders/user/{userId}<br/>POST /v1/orders/{id}/cancel<br/>USER only"]:::us
    ORDER --> O2["GET /v1/orders<br/>POST /v1|v2/orders/{id}/confirm<br/>ADMIN only"]:::ad
    ORDER --> O3["POST /v1/orders<br/>POST /v2/orders<br/>USER or ADMIN"]:::both

    LOG --> L1["GET /v1/logs<br/>ADMIN only"]:::ad
    LOG --> L2["POST /v1/logs<br/>permitAll (services write)"]:::both
```

## 4. Service-to-Service Authentication

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Service A needs Service B"]):::term --> TOK{"which token?"}:::dec

    TOK -->|"auth-service to user-service"| STOKEN["service token<br/>role = SERVICE<br/>(JwtConfig.generateServiceToken)"]:::act
    STOKEN --> B1["user-service accepts<br/>SERVICE role"]:::ok

    TOK -->|"order-service to user/product<br/>(sync order create / confirm)"| FWD["forward caller's JWT<br/>RestClientConfig.forwardAuthorization"]:::act
    FWD --> B2["user/product-service sees<br/>original USER or ADMIN role"]:::ok

    TOK -->|"RabbitMQ consumers"| RMQ["no HTTP token<br/>events carry order data only"]:::act
    RMQ --> B3["consumers act on the data,<br/>no role required"]:::ok

    B1 --> Stop(["End"]):::term
    B2 --> Stop
    B3 --> Stop
```